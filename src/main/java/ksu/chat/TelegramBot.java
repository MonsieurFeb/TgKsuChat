package ksu.chat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Простой мост между чатом Minecraft и Telegram.
 * <p>
 * Поддерживаются только сообщения в обе стороны и загрузка фотографий из
 * Telegram (фото открывается на клиенте по клику на {@code [Фото]}).
 */
public class TelegramBot {
    private static final Logger LOGGER = LogManager.getLogger("KsuChat");
    private static final String API_BASE = "https://api.telegram.org/bot";
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 40000;

    private final ModConfig config;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread pollThread;
    private long updateOffset = 0L;
    private volatile MinecraftServer server;

    private final SSLSocketFactory sslSocketFactory;
    private final HostnameVerifier hostnameVerifier;

    public TelegramBot(ModConfig config) {
        this.config = config;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "KsuChat-TelegramBot");
            t.setDaemon(true);
            return t;
        });

        SSLSocketFactory factory = null;
        HostnameVerifier verifier = null;
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        @Override
                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            factory = sslContext.getSocketFactory();
            verifier = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };
        } catch (Exception e) {
            LOGGER.warn("[KsuChat] Не удалось установить доверяющий SSL-контекст: {}", e.getMessage());
        }
        this.sslSocketFactory = factory;
        this.hostnameVerifier = verifier;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public void start() {
        if (config.isConfigured()) {
            LOGGER.error("[KsuChat] Токен бота или chatId не настроены! Telegram-интеграция отключена.");
            return;
        }
        running.set(true);
        pollThread = new Thread(new Runnable() {
            @Override
            public void run() {
                pollLoop();
            }
        }, "KsuChat-Poller");
        pollThread.setDaemon(true);
        pollThread.start();
        LOGGER.info("[KsuChat] Telegram-бот запущен.");
    }

    public void stop() {
        running.set(false);
        if (pollThread != null) {
            pollThread.interrupt();
        }
        executor.shutdownNow();
        LOGGER.info("[KsuChat] Telegram-бот остановлен.");
    }

    // ------------------------------------------------------------------
    // Отправка в Telegram
    // ------------------------------------------------------------------

    public void sendMessage(String text) {
        if (config.isConfigured() || text == null || text.isEmpty()) {
            return;
        }
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                    JsonObject body = new JsonObject();
                    body.addProperty("chat_id", config.getChatId());
                    body.addProperty("text", escaped);
                    body.addProperty("parse_mode", "HTML");
                    post("sendMessage", body.toString());
                } catch (Exception e) {
                    LOGGER.error("[KsuChat] Ошибка отправки Telegram-сообщения: {}", e.getMessage(), e);
                }
            }
        });
    }

    private void post(String method, String jsonBody) throws IOException {
        String response = postJson(API_BASE + config.getBotToken() + "/" + method, jsonBody);
        try {
            JsonObject json = new JsonParser().parse(response).getAsJsonObject();
            if (!json.get("ok").getAsBoolean()) {
                String desc = json.has("description") ? json.get("description").getAsString() : "неизвестная ошибка";
                LOGGER.error("[KsuChat] Ошибка Telegram API {}: {}", method, desc);
            }
        } catch (Exception e) {
            LOGGER.error("[KsuChat] Некорректный ответ Telegram API {}: {}", method, response);
        }
    }

    // ------------------------------------------------------------------
    // Опрос обновлений
    // ------------------------------------------------------------------

    private void pollLoop() {
        LOGGER.info("[KsuChat] Опрос Telegram запущен. chatId={}", config.getChatId());
        while (running.get()) {
            HttpURLConnection connection = null;
            try {
                String url = API_BASE + config.getBotToken()
                        + "/getUpdates?offset=" + updateOffset + "&timeout=30";
                connection = openConnection(url, "GET");
                connection.setReadTimeout(READ_TIMEOUT);

                String body = readResponse(connection);
                JsonObject json = new JsonParser().parse(body).getAsJsonObject();
                if (json.get("ok").getAsBoolean()) {
                    JsonArray results = json.getAsJsonArray("result");
                    for (JsonElement elem : results) {
                        JsonObject update = elem.getAsJsonObject();
                        updateOffset = update.get("update_id").getAsLong() + 1;
                        if (update.has("message")) {
                            handleUpdate(update.getAsJsonObject("message"));
                        }
                    }
                }
            } catch (Exception e) {
                if (!running.get()) {
                    break;
                }
                LOGGER.warn("[KsuChat] Ошибка опроса Telegram: {}", e.getMessage());
                try {
                    Thread.sleep(5000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        LOGGER.info("[KsuChat] Опрос Telegram остановлен.");
    }

    private void handleUpdate(JsonObject message) {
        if (!message.has("chat")) {
            return;
        }

        JsonObject chat = message.getAsJsonObject("chat");
        long chatId = chat.get("id").getAsLong();
        if (chatId != config.getChatId()) {
            return;
        }

        String username = "";
        long userId = 0L;
        if (message.has("from")) {
            JsonObject from = message.getAsJsonObject("from");
            if (from.has("id")) {
                userId = from.get("id").getAsLong();
            }
            if (from.has("username")) {
                username = from.get("username").getAsString();
            } else if (from.has("first_name")) {
                username = from.get("first_name").getAsString();
            }
        }
        final String senderName = username.isEmpty() ? "Telegram" : username;

        String text = message.has("text") ? message.get("text").getAsString() : null;
        String caption = message.has("caption") ? message.get("caption").getAsString() : null;
        boolean hasPhoto = message.has("photo");
        boolean hasDocument = message.has("document");

        if (text == null && !hasPhoto && !hasDocument) {
            return;
        }

        if (text != null && text.startsWith("/")) {
            LOGGER.info("[KsuChat] Telegram-команда от {} (ID: {}): {}", senderName, userId, text);
            text = text.replaceAll("@\\S*+", "");
            String[] parts = text.split("\\s+", 3);
            String cmd = parts[0].toLowerCase();
            switch (cmd) {
                case "/players":
                    handlePlayers();
                    break;
                case "/kick":
                    handleKick(userId, senderName, parts);
                    break;
//                case "/zwl":
//                    handleZwlWhitelist(userId, senderName, parts);
//                    break;
                case "/myid":
                    handleMyId(userId, senderName);
                    break;
                case "/start":
                case "/help":
                    handleHelp();
                    break;
                default:
                    sendMessage("Неизвестная команда: " + cmd);
            }
            return;
        }

        if (server == null) {
            return;
        }

        if (hasDocument) {
            final JsonObject doc = message.getAsJsonObject("document");
            String mimeType = doc.has("mime_type") ? doc.get("mime_type").getAsString() : "";
            final boolean isImage = mimeType.startsWith("image/");
            if (!isImage) {
                if (text != null && !text.isEmpty()) {
                    broadcastText(senderName, text);
                }
                return;
            }
            LOGGER.info("[KsuChat] Получено изображение-документ от {}: mime={}, имя={}",
                    senderName, mimeType,
                    doc.has("file_name") ? doc.get("file_name").getAsString() : "?");
            final byte[] photoBytes = downloadDocumentFile(doc);
            LOGGER.info("[KsuChat] ТГ->МК фото-документ от {}: байтов={}",
                    senderName, photoBytes != null ? photoBytes.length : 0);
            final String finalCaption = caption == null ? "" : caption;
            if (photoBytes != null) {
                server.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        broadcastPhoto(senderName, photoBytes, finalCaption);
                    }
                });
            } else {
                broadcastText(senderName, "[Фото не загружено] " + finalCaption);
            }
            return;
        }

        if (hasPhoto) {
            final byte[] photoBytes = downloadPhoto(message);
            LOGGER.info("[KsuChat] ТГ->МК фото от {}: байтов={}",
                    senderName, photoBytes != null ? photoBytes.length : 0);
            final String finalCaption = caption == null ? "" : caption;
            if (photoBytes != null) {
                server.addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        broadcastPhoto(senderName, photoBytes, finalCaption);
                    }
                });
            } else {
                broadcastText(senderName, "[Фото не загружено] " + finalCaption);
            }
            return;
        }

        if (text != null && !text.isEmpty()) {
            broadcastText(senderName, text);
            LOGGER.info("[KsuChat] ТГ->МК сообщение от {}: {}", senderName, text);
        }
    }

    // ------------------------------------------------------------------
    // Команды Telegram
    // ------------------------------------------------------------------

    private void handlePlayers() {
        final MinecraftServer srv = server;
        if (srv == null) {
            sendMessage("Сервер не запущен.");
            return;
        }
        srv.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                java.util.List<EntityPlayerMP> players = srv.getPlayerList().getPlayers();
                if (players.isEmpty()) {
                    sendMessage("Нет игроков на сервере.");
                    return;
                }
                StringBuilder sb = new StringBuilder("Игроки онлайн (" + players.size() + "):\n");
                for (EntityPlayerMP p : players) {
                    sb.append("  - ").append(p.getName()).append("\n");
                }
                sendMessage(sb.toString());
            }
        });
    }

    private void handleKick(long userId, String username, String[] parts) {
        if (!config.isAdmin(userId)) {
            sendMessage("Доступ запрещён. Только для админов.");
            return;
        }
        if (parts.length < 2) {
            sendMessage("Использование: /kick <ник>");
            return;
        }
        final String target = parts[1];
        final String admin = username;
        final MinecraftServer srv = server;
        if (srv == null) {
            sendMessage("Сервер не запущен.");
            return;
        }
        srv.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                EntityPlayerMP player = srv.getPlayerList().getPlayerByUsername(target);
                if (player == null) {
                    sendMessage("Игрок " + target + " не найден на сервере.");
                    return;
                }
                player.connection.disconnect(new TextComponentString("Кикнут админом из Telegram"));
                sendMessage("Игрок " + target + " кикнут @" + admin);
            }
        });
    }

//    private void handleZwlWhitelist(long userId, String username, String[] parts) {
//        if (!config.isAdmin(userId)) {
//            sendMessage("Доступ запрещён. Только для админов.");
//            return;
//        }
//        if (parts.length < 3) {
//            sendMessage("Использование: /zwl add <ник> ИЛИ /zwl remove <ник>");
//            return;
//        }
//        final String action = parts[1].toLowerCase();
//        final String target = parts[2];
//        final String admin = username;
//        final MinecraftServer srv = server;
//        if (srv == null) {
//            sendMessage("Сервер не запущен.");
//            return;
//        }
//        srv.addScheduledTask(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    if ("add".equals(action)) {
//                        GameProfile profile = resolveProfile(srv, target);
//                        srv.getPlayerList().addWhitelistedPlayer(profile);
//                        srv.getPlayerList().reloadWhitelist();
//                        sendMessage("Игрок " + target + " добавлен в вайтлист @" + admin);
//                    } else if ("remove".equals(action)) {
//                        GameProfile profile = resolveProfile(srv, target);
//                        srv.getPlayerList().removePlayerFromWhitelist(profile);
//                        srv.getPlayerList().reloadWhitelist();
//                        sendMessage("Игрок " + target + " удалён из вайтлиста @" + admin);
//                    } else {
//                        sendMessage("Использование: /zwl add <ник> ИЛИ /zwl remove <ник>");
//                    }
//                } catch (Exception e) {
//                    LOGGER.error("[KsuChat] Ошибка вайтлиста", e);
//                    sendMessage("Ошибка: " + e.getMessage());
//                }
//            }
//        });
//    }

    private static GameProfile resolveProfile(MinecraftServer srv, String name) {
        EntityPlayerMP online = srv.getPlayerList().getPlayerByUsername(name);
        if (online != null) {
            return online.getGameProfile();
        }
        GameProfile cached = srv.getPlayerProfileCache().getGameProfileForUsername(name);
        if (cached != null) {
            return cached;
        }
        return new GameProfile(java.util.UUID.nameUUIDFromBytes(
                ("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)), name);
    }

    private void handleMyId(long userId, String username) {
        boolean admin = config.isAdmin(userId);
        sendMessage("Ваш Telegram ID: " + userId + "\nUsername: @" + username + "\nАдмин: "
                + (admin ? "ДА" : "НЕТ") + "\n\nДобавьте этот ID в telegramAdmins в config/ksuchat.toml");
    }

    private void handleHelp() {
        sendMessage("KsuChat Команды:\n"
                + "/help - Показать эту справку\n"
                + "/myid - Показать ваш Telegram ID (для настройки)\n"
                + "/players - Список игроков онлайн\n"
                + "/kick [ник] - Кикнуть игрока (админ)\n"
                + "/zwl [add/remove] [ник] - Добавить или удалить из вайтлиста (админ)\n");
    }

    // ------------------------------------------------------------------
    // Загрузка файлов из Telegram
    // ------------------------------------------------------------------

    private byte[] downloadDocumentFile(JsonObject document) {
        try {
            String fileId = document.get("file_id").getAsString();
            return downloadFile(fileId);
        } catch (Exception e) {
            LOGGER.warn("[KsuChat] Ошибка загрузки документа: {}", e.getMessage());
        }
        return null;
    }

    private byte[] downloadPhoto(JsonObject message) {
        try {
            JsonArray photos = message.getAsJsonArray("photo");
            if (photos.size() == 0) {
                return null;
            }
            JsonObject largest = photos.get(photos.size() - 1).getAsJsonObject();
            LOGGER.info("[KsuChat] Фото: {}x{}, {} байтов",
                    largest.has("width") ? largest.get("width").getAsInt() : 0,
                    largest.has("height") ? largest.get("height").getAsInt() : 0,
                    largest.has("file_size") ? largest.get("file_size").getAsInt() : 0);
            return downloadFile(largest.get("file_id").getAsString());
        } catch (Exception e) {
            LOGGER.warn("[KsuChat] Ошибка загрузки фото: {}", e.getMessage());
        }
        return null;
    }

    private byte[] downloadFile(String fileId) throws IOException {
        String getUrl = API_BASE + config.getBotToken() + "/getFile?file_id="
                + URLEncoder.encode(fileId, "UTF-8");
        String response = get(getUrl);
        JsonObject json = new JsonParser().parse(response).getAsJsonObject();
        if (!json.get("ok").getAsBoolean()) {
            LOGGER.warn("[KsuChat] Telegram не отдал файл: {}", response);
            return null;
        }
        String filePath = json.getAsJsonObject("result").get("file_path").getAsString();
        String downloadUrl = "https://api.telegram.org/file/bot" + config.getBotToken() + "/" + filePath;
        byte[] data = getBytes(downloadUrl);
        LOGGER.info("[KsuChat] Загружен файл: {} байтов (file_path={})", data.length, filePath);
        return data;
    }

    // ------------------------------------------------------------------
    // Рассылка сообщений в игру
    // ------------------------------------------------------------------

    private void broadcastText(final String sender, final String text) {
        final MinecraftServer srv = server;
        if (srv == null) {
            return;
        }
        srv.addScheduledTask(new Runnable() {
            @Override
            public void run() {
                TextComponentString name = new TextComponentString(sender);
                name.getStyle().setColor(TextFormatting.AQUA);
                TextComponentString line = new TextComponentString("[ТГ] ");
                line.getStyle().setColor(TextFormatting.DARK_AQUA);
                line.appendSibling(name);
                line.appendSibling(new TextComponentString(": " + text));
                srv.getPlayerList().sendMessage(line);
            }
        });
    }

    /** Вызывать только из основного потока сервера. */
    private void broadcastPhoto(String sender, byte[] data, String caption) {
        MinecraftServer srv = server;
        if (srv == null) {
            return;
        }
        java.util.UUID photoId = KsuChatMod.cachePhoto(data);

        TextComponentString name = new TextComponentString(sender);
        name.getStyle().setColor(TextFormatting.AQUA);
        TextComponentString line = new TextComponentString("[ТГ] ");
        line.getStyle().setColor(TextFormatting.DARK_AQUA);
        line.appendSibling(name);
        line.appendSibling(new TextComponentString(": "));

        TextComponentString photo = new TextComponentString("[Фото]");
        photo.getStyle().setColor(TextFormatting.GREEN);
        photo.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/ksu_photo " + photoId));
        line.appendSibling(photo);

        if (caption != null && !caption.isEmpty()) {
            line.appendSibling(new TextComponentString(" " + caption));
        }
        srv.getPlayerList().sendMessage(line);
    }

    // ------------------------------------------------------------------
    // HTTP-помощники (HttpURLConnection)
    // ------------------------------------------------------------------

    private Proxy buildProxy() {
        String proxyType = config.getProxyType();
        String proxyHost = config.getProxyHost();
        int proxyPort = config.getProxyPort();
        if (proxyType == null || proxyType.isEmpty() || proxyHost == null || proxyHost.isEmpty()) {
            return Proxy.NO_PROXY;
        }
        if ("socks".equalsIgnoreCase(proxyType)) {
            return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(proxyHost, proxyPort));
        }
        if ("http".equalsIgnoreCase(proxyType)) {
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
        }
        LOGGER.warn("[KsuChat] Неизвестный тип прокси: {}. Используйте 'socks' или 'http'.", proxyType);
        return Proxy.NO_PROXY;
    }

    private HttpURLConnection openConnection(String url, String method) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection(buildProxy());
        connection.setRequestMethod(method);
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestProperty("User-Agent", "KsuChat/1.0");
        if (sslSocketFactory != null && connection instanceof HttpsURLConnection) {
            HttpsURLConnection https = (HttpsURLConnection) connection;
            https.setSSLSocketFactory(sslSocketFactory);
            if (hostnameVerifier != null) {
                https.setHostnameVerifier(hostnameVerifier);
            }
        }
        return connection;
    }

    private String get(String url) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(url, "GET");
            return readResponse(connection);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private byte[] getBytes(String url) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(url, "GET");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            InputStream in = connection.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            return out.toByteArray();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String postJson(String url, String jsonBody) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = openConnection(url, "POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            byte[] payload = jsonBody.getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            OutputStream out = connection.getOutputStream();
            out.write(payload);
            out.flush();
            out.close();
            return readResponse(connection);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readResponse(HttpURLConnection connection) throws IOException {
        int code = connection.getResponseCode();
        InputStream in = code >= 200 && code < 400
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (in == null) {
            throw new IOException("HTTP " + code);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
