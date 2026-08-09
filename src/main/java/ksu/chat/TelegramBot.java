package ksu.chat;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.server.players.UserWhiteListEntry;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

import static ksu.chat.KsuChatMod.MODID;

public class TelegramBot {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String API_BASE = "https://api.telegram.org/bot";

    private final ModConfig config;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread pollThread;
    private long updateOffset = 0;
    private volatile MinecraftServer server;
    private volatile ksu.chat.chat.ChatManager chatManager;

    public TelegramBot(ModConfig config) {
        this.config = config;
        this.httpClient = buildHttpClient(config);
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "KsuChat-TelegramBot");
            t.setDaemon(true);
            return t;
        });
    }

    private static HttpClient buildHttpClient(ModConfig config) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15));

        String proxyType = config.getProxyType();
        String proxyHost = config.getProxyHost();
        int proxyPort = config.getProxyPort();

        if (proxyType != null && !proxyType.isEmpty() && proxyHost != null && !proxyHost.isEmpty()) {
            ProxySelector proxySelector;
            if (proxyType.equalsIgnoreCase("socks")) {
                proxySelector = ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort));
            } else if (proxyType.equalsIgnoreCase("http")) {
                proxySelector = new ProxySelector() {
                    @Override
                    public List<java.net.Proxy> select(URI uri) {
                        return List.of(new java.net.Proxy(java.net.Proxy.Type.HTTP,
                                new InetSocketAddress(proxyHost, proxyPort)));
                    }
                    @Override
                    public void connectFailed(URI uri, java.net.SocketAddress sa, IOException e) {
                        LOGGER.warn("[KsuChat] Ошибка подключения к прокси: {}", e.getMessage());
                    }
                };
            } else {
                LOGGER.warn("[KsuChat] Неизвестный тип прокси: {}. Используйте 'socks' или 'http'.", proxyType);
                proxySelector = ProxySelector.getDefault();
            }
            builder.proxy(proxySelector);
            LOGGER.info("[KsuChat] Используется {} прокси {}:{}", proxyType, proxyHost, proxyPort);
        }

        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            builder.sslContext(sslContext);
        } catch (Exception e) {
            LOGGER.warn("[KsuChat] Не удалось установить пользовательский SSL-контекст: {}", e.getMessage());
        }

        return builder.build();
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    public void setChatManager(ksu.chat.chat.ChatManager chatManager) {
        this.chatManager = chatManager;
    }

    public void start() {
        if (!config.isConfigured()) {
            LOGGER.error("[KsuChat] Токен бота или chatId не настроены! Telegram-интеграция отключена.");
            return;
        }
        running.set(true);
        pollThread = new Thread(this::pollLoop, "KsuChat-Poller");
        pollThread.setDaemon(true);
        pollThread.start();
        LOGGER.info("[KsuChat] Telegram-бот успешно запущен.");
    }

    public void stop() {
        running.set(false);
        if (pollThread != null) {
            pollThread.interrupt();
        }
        executor.shutdownNow();
        LOGGER.info("[KsuChat] Telegram-бот остановлен.");
    }

    public void sendMessage(String text) {
        if (!config.isConfigured() || text == null || text.isEmpty()) return;
        executor.execute(() -> {
            try {
                String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                com.google.gson.JsonObject body = new com.google.gson.JsonObject();
                body.addProperty("chat_id", config.getChatId());
                body.addProperty("text", escaped);
                body.addProperty("parse_mode", "HTML");
                post("sendMessage", body.toString());
            } catch (Exception e) {
                LOGGER.error("[KsuChat] Ошибка отправки Telegram-сообщения: {}", e.getMessage(), e);
            }
        });
    }

    public void sendMarkdown(String text) {
        if (!config.isConfigured() || text == null || text.isEmpty()) return;
        executor.execute(() -> {
            try {
                com.google.gson.JsonObject body = new com.google.gson.JsonObject();
                body.addProperty("chat_id", config.getChatId());
                body.addProperty("text", text);
                post("sendMessage", body.toString());
            } catch (Exception e) {
                LOGGER.error("[KsuChat] Ошибка отправки Telegram-сообщения: {}", e.getMessage(), e);
            }
        });
    }

    private void pollLoop() {
        LOGGER.info("[KsuChat] Опрос запущен. chatId={}", config.getChatId());
        while (running.get()) {
            try {
                String url = API_BASE + config.getBotToken() + "/getUpdates?offset=" + updateOffset + "&timeout=30";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(40))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                com.google.gson.JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.get("ok").getAsBoolean()) {
                    com.google.gson.JsonArray results = json.getAsJsonArray("result");
                    if (!results.isEmpty()) {
                        LOGGER.info("[KsuChat] Получено {} обновлений", results.size());
                    }
                    for (var elem : results) {
                        com.google.gson.JsonObject update = elem.getAsJsonObject();
                        updateOffset = update.get("update_id").getAsLong() + 1;
                        if (update.has("message")) {
                            handleUpdate(update.getAsJsonObject("message"));
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.warn("[KsuChat] Ошибка опроса: {}", e.getMessage());
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        LOGGER.info("[KsuChat] Опрос остановлен.");
    }

    private void handleUpdate(com.google.gson.JsonObject message) {
        if (!message.has("chat")) return;

        com.google.gson.JsonObject chat = message.getAsJsonObject("chat");
        long chatId = chat.get("id").getAsLong();
        if (chatId != config.getChatId()) return;

        String username = "";
        if (message.has("from")) {
            com.google.gson.JsonObject from = message.getAsJsonObject("from");
            if (from.has("username")) {
                username = from.get("username").getAsString();
            } else if (from.has("first_name")) {
                username = from.get("first_name").getAsString();
            }
        }
        String senderName = username.isEmpty() ? "Telegram" : username;

        long userId = 0;
        if (message.has("from")) {
            userId = message.getAsJsonObject("from").get("id").getAsLong();
        }

        String text = message.has("text") ? message.get("text").getAsString() : null;
        String caption = message.has("caption") ? message.get("caption").getAsString() : null;
        boolean hasPhoto = message.has("photo");
        boolean hasDocument = message.has("document");

        if (text == null && !hasPhoto && !hasDocument) return;

        if (text != null && text.startsWith("/")) {
            LOGGER.info("[KsuChat] Telegram-команда от {} (ID: {}): {}", senderName, userId, text);
            String[] parts = text.split("\\s+", 3);
            String cmd = parts[0].toLowerCase();
            switch (cmd) {
                case "/players" -> handlePlayers();
//                case "/stop" -> handleStop(userId, senderName);
                case "/kick" -> handleKick(userId, senderName, parts);
//                case "/restart" -> handleRestart(userId, senderName);
//                case "/ban" -> handleBan(userId, senderName, parts);
//                case "/unban" -> handleUnban(userId, senderName, parts);
                case "/zwl" -> handleWhitelist(userId, senderName, parts);
                case "/myid" -> handleMyId(userId, senderName);
                case "/start", "/help" -> handleHelp();
                default -> sendMessage("Неизвестная команда: " + cmd);
            }
            return;
        }

        MinecraftServer srv = server;
        if (srv == null) return;

        if (hasDocument) {
            com.google.gson.JsonObject doc = message.getAsJsonObject("document");
            String mimeType = doc.has("mime_type") ? doc.get("mime_type").getAsString() : "";
            boolean isImage = mimeType.startsWith("image/");
            if (!isImage) {
                if (text != null && !text.isEmpty()) {
                    srv.execute(() -> {
                        ksu.chat.net.ChatMessagePayload chatMsg = new ksu.chat.net.ChatMessagePayload(
                                "[ТГ] " + senderName, text, (byte) 1, "", null);
                        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                            if (p.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
                                p.connection.send(chatMsg);
                            } else {
                                p.sendSystemMessage(Component.literal("<")
                                        .append(Component.literal("[ТГ] ").withStyle(s -> s.withColor(ChatFormatting.BLUE)))
                                        .append(Component.literal(senderName + "> "))
                                        .append(Component.literal(text))
                                );
                            }
                        }
                    });
                }
                return;
            }
            LOGGER.info("[KsuChat] Получено документ-изображение от {}: mime={}, имя={}",
                    senderName, mimeType,
                    doc.has("file_name") ? doc.get("file_name").getAsString() : "?");
            byte[] photoBytes = downloadDocumentFile(doc);
            LOGGER.info("[KsuChat] ТГ->МК документ-фото от {}: байтов={}", senderName, photoBytes != null ? photoBytes.length : 0);
            srv.execute(() -> {
                String finalText = "";
                if (caption != null && !caption.isEmpty()) {
                    finalText = caption;
                }
                if (photoBytes != null) {
                    java.util.UUID photoId = KsuChatMod.cachePhoto(photoBytes);
                    String photoText = "[Фото HD] " + finalText;
                    ksu.chat.net.ChatMessagePayload chatMsg = new ksu.chat.net.ChatMessagePayload(
                            "[ТГ] " + senderName, photoText, (byte) 1, "", "/ksu_photo " + photoId);
                    for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                        if (p.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
                            p.connection.send(chatMsg);
                        } else {
                            p.sendSystemMessage(Component.literal("<")
                                    .append(Component.literal("[ТГ] ").withStyle(s -> s.withColor(ChatFormatting.BLUE)))
                                    .append(Component.literal(senderName + "> "))
                                    .append(Component.literal("[Фото, для просмотра нужен мод] ").withStyle(s -> s.withColor(ChatFormatting.GREEN)))
                                    .append(Component.literal(finalText))
                            );
                        }
                    }
                } else {
                    ksu.chat.net.ChatMessagePayload chatMsg = new ksu.chat.net.ChatMessagePayload(
                            "[ТГ] " + senderName, "[Фото не загружено] " + finalText, (byte) 1, "", null);
                    for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                        if (p.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
                            p.connection.send(chatMsg);
                        } else {
                            p.sendSystemMessage(Component.literal("<")
                                    .append(Component.literal("[ТГ] ").withStyle(s -> s.withColor(ChatFormatting.BLUE)))
                                    .append(Component.literal(senderName + "> "))
                                    .append(Component.literal("[Фото не загружено] ").withStyle(s -> s.withColor(ChatFormatting.GREEN)))
                                    .append(Component.literal(finalText))
                            );
                        }
                    }
                }
            });
            return;
        }

        if (hasPhoto) {
            byte[] photoBytes = downloadPhoto(message);
            LOGGER.info("[KsuChat] ТГ->МК фото от {}: байтов={}", senderName, photoBytes != null ? photoBytes.length : 0);
            srv.execute(() -> {
                String finalText = "";
                if (caption != null && !caption.isEmpty()) {
                    finalText = caption;
                }
                if (photoBytes != null) {
                    java.util.UUID photoId = KsuChatMod.cachePhoto(photoBytes);
                    String photoText = "[Фото] " + finalText;
                    ksu.chat.net.ChatMessagePayload chatMsg = new ksu.chat.net.ChatMessagePayload(
                            "[ТГ] " + senderName, photoText, (byte) 1, "", "/ksu_photo " + photoId);
                    for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                        if (p.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
                            p.connection.send(chatMsg);
                        } else {
                            p.sendSystemMessage(Component.literal("<")
                                    .append(Component.literal("[ТГ] ").withStyle(s -> s.withColor(ChatFormatting.BLUE)))
                                    .append(Component.literal(senderName + "> "))
                                    .append(Component.literal("[Фото, для просмотра нужен мод] ").withStyle(s -> s.withColor(ChatFormatting.GREEN)))
                                    .append(Component.literal(finalText))
                            );
                        }
                    }
                } else {
                    ksu.chat.net.ChatMessagePayload chatMsg = new ksu.chat.net.ChatMessagePayload(
                            "[ТГ] " + senderName, "[Фото не загружено] " + finalText, (byte) 1, "", null);
                    for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                        if (p.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
                            p.connection.send(chatMsg);
                        } else {
                            p.sendSystemMessage(Component.literal("<")
                                    .append(Component.literal("[ТГ] ").withStyle(s -> s.withColor(ChatFormatting.BLUE)))
                                    .append(Component.literal(senderName + "> "))
                                    .append(Component.literal("[Фото не загружено] ").withStyle(s -> s.withColor(ChatFormatting.GREEN)))
                                    .append(Component.literal(finalText))
                                    );
                        }
                    }
                }
            });
            return;
        }

        if (!text.isEmpty()) {
            srv.execute(() -> {
                ksu.chat.net.ChatMessagePayload chatMsg = new ksu.chat.net.ChatMessagePayload(
                        "[ТГ] " + senderName, text, (byte) 1, "", null);
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    if (p.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
                        p.connection.send(chatMsg);
                    } else {
                        p.sendSystemMessage(Component.literal("<")
                                .append(Component.literal("[ТГ] ").withStyle(s -> s.withColor(ChatFormatting.BLUE)))
                                .append(Component.literal(senderName + "> "))
                                .append(Component.literal(text))
                        );
                    }
                }
            });
            LOGGER.info("[KsuChat] ТГ->МК сообщение от {}: {}", senderName, text);
        }
    }

    private byte[] downloadDocumentFile(com.google.gson.JsonObject document) {
        try {
            String fileId = document.get("file_id").getAsString();
            String getUrl = API_BASE + config.getBotToken() + "/getFile?file_id=" + fileId;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
            if (json.get("ok").getAsBoolean()) {
                String filePath = json.getAsJsonObject("result").get("file_path").getAsString();
                String downloadUrl = "https://api.telegram.org/file/bot" + config.getBotToken() + "/" + filePath;
                HttpRequest dl = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();
                HttpResponse<byte[]> dlResponse = httpClient.send(dl, HttpResponse.BodyHandlers.ofByteArray());
                LOGGER.info("[KsuChat] Загружен документ: {} байтов (file_path={})", dlResponse.body().length, filePath);
                return dlResponse.body();
            }
        } catch (Exception e) {
            LOGGER.warn("[KsuChat] Ошибка загрузки документа: {}", e.getMessage());
        }
        return null;
    }

    private byte[] downloadPhoto(com.google.gson.JsonObject message) {
        try {
            com.google.gson.JsonArray photos = message.getAsJsonArray("photo");
            com.google.gson.JsonObject largest = photos.get(photos.size() - 1).getAsJsonObject();
            com.google.gson.JsonObject last = photos.get(photos.size() - 1).getAsJsonObject();
            LOGGER.info("[KsuChat] Фото: {}x{}, {} байтов, file_id={}",
                    last.get("width").getAsInt(), last.get("height").getAsInt(),
                    last.has("file_size") ? last.get("file_size").getAsInt() : "?",
                    last.get("file_id").getAsString().substring(0, Math.min(20, last.get("file_id").getAsString().length())) + "...");
            String fileId = largest.get("file_id").getAsString();

            String getUrl = API_BASE + config.getBotToken() + "/getFile?file_id=" + fileId;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getUrl))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
            if (json.get("ok").getAsBoolean()) {
                String filePath = json.getAsJsonObject("result").get("file_path").getAsString();
                String downloadUrl = "https://api.telegram.org/file/bot" + config.getBotToken() + "/" + filePath;
                HttpRequest dl = HttpRequest.newBuilder()
                        .uri(URI.create(downloadUrl))
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();
                HttpResponse<byte[]> dlResponse = httpClient.send(dl, HttpResponse.BodyHandlers.ofByteArray());
                LOGGER.info("[KsuChat] Загружено фото: {} байтов", dlResponse.body().length);
                return dlResponse.body();
            }
        } catch (Exception e) {
            LOGGER.warn("[KsuChat] Ошибка загрузки фото: {}", e.getMessage());
        }
        return null;
    }

    private void handlePlayers() {
        MinecraftServer srv = server;
        if (srv == null) {
            sendMessage("Сервер не запущен.");
            return;
        }
        List<ServerPlayer> players = srv.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            sendMessage("Нет игроков на сервере.");
            return;
        }
        StringBuilder sb = new StringBuilder("Игроки онлайн (" + players.size() + "):\n");
        for (ServerPlayer p : players) {
            sb.append("  - ").append(p.getName().getString()).append("\n");
        }
        sendMessage(sb.toString());
    }

    private void handleStop(long userId, String username) {
        if (!config.isAdmin(userId)) {
            sendMessage("Доступ запрещён. Только для админов.");
            return;
        }
        MinecraftServer srv = server;
        if (srv == null) {
            sendMessage("Сервер не запущен.");
            return;
        }
        sendMessage("Сервер выключается @" + username + "...");
        executor.execute(() -> srv.execute(srv::close));
    }

    private void handleRestart(long userId, String username) {
        if (!config.isAdmin(userId)) {
            sendMessage("Доступ запрещён. Только для админов.");
            return;
        }
        MinecraftServer srv = server;
        if (srv == null) {
            sendMessage("Сервер не запущен.");
            return;
        }
        sendMessage("Сервер перезагружается @" + username + "...");
        executor.execute(() -> srv.execute(() -> srv.halt(true)));
    }

    private void handleKick(long userId, String username, String[] parts) {
        if (!config.isAdmin(userId)) {
            sendMessage("Доступ запрещён. Только для админов.");
            return;
        }
        if (parts.length < 2) {
            sendMessage("Использование: /kick &lt;ник&gt;");
            return;
        }
        String target = parts[1];
        MinecraftServer srv = server;
        if (srv == null) {
            sendMessage("Сервер не запущен.");
            return;
        }
        srv.execute(() -> {
            ServerPlayer player = srv.getPlayerList().getPlayerByName(target);
            if (player == null) {
                sendMessage("Игрок " + target + " не найден на сервере.");
                return;
            }
            player.connection.disconnect(Component.literal("Кикнут админом из Telegram"));
            sendMessage("Игрок " + target + " кикнут @" + username);
        });
    }

    private void handleBan(long userId, String username, String[] parts) {
        if (!config.isAdmin(userId)) {
            sendMessage("Доступ запрещён. Только для админов.");
            return;
        }
        if (parts.length < 2) {
            sendMessage("Использование: /ban &lt;ник&gt;");
            return;
        }
        String target = parts[1];
        MinecraftServer srv = server;
        if (srv == null) {
            sendMessage("Сервер не запущен.");
            return;
        }
        srv.execute(() -> {
            try {
                ServerPlayer player = srv.getPlayerList().getPlayerByName(target);
                GameProfile profile = player != null ? player.getGameProfile() : null;
                if (profile == null) {
                    sendMessage("Игрок " + target + " не найден на сервере. Невозможно забанить.");
                    return;
                }
                player.connection.disconnect(Component.literal("Забанен админом из Telegram"));
                srv.getPlayerList().getBans().add(new UserBanListEntry(profile, null, "Telegram", null, "Забанен @" + username));
                sendMessage("Игрок " + target + " забанен @" + username);
            } catch (Exception e) {
                LOGGER.error("[KsuChat] Ошибка при бане", e);
                sendMessage("Ошибка при бане: " + e.getMessage());
            }
        });
    }

    private void handleUnban(long userId, String username, String[] parts) {
        if (!config.isAdmin(userId)) {
            sendMessage("Доступ запрещён. Только для админов.");
            return;
        }
        if (parts.length < 2) {
            sendMessage("Использование: /unban &lt;ник&gt;");
            return;
        }
        String target = parts[1];
        MinecraftServer srv = server;
        if (srv == null) {
            sendMessage("Сервер не запущен.");
            return;
        }
        srv.execute(() -> {
            try {
                var banList = srv.getPlayerList().getBans();
                var entries = new ArrayList<>(banList.getEntries());
                boolean found = false;
                for (var entry : entries) {
                    if (entry.getDisplayName().getString().equalsIgnoreCase(target)) {
                        banList.remove(entry);
                        found = true;
                    }
                }
                if (found) {
                    sendMessage("Игрок " + target + " разбанен @" + username);
                } else {
                    sendMessage("Бан для " + target + " не найден");
                }
            } catch (Exception e) {
                LOGGER.error("[KsuChat] Ошибка при разбане", e);
                sendMessage("Ошибка при разбане: " + e.getMessage());
            }
        });
    }

    private void handleWhitelist(long userId, String username, String[] parts) {
        if (!config.isAdmin(userId)) {
            sendMessage("Доступ запрещён. Только для админов.");
            return;
        }
        if (parts.length < 3) {
            sendMessage("Использование: /zwl add &lt;ник&gt; ИЛИ /zwl remove &lt;ник&gt;");
            return;
        }
        String action = parts[1].toLowerCase();
        String target = parts[2];
        MinecraftServer srv = server;
        if (srv == null) {
            sendMessage("Сервер не запущен.");
            return;
        }
        srv.execute(() -> {
            try {
                var wl = srv.getPlayerList().getWhiteList();
                switch (action) {
                    case "add" -> {
                        GameProfile profile = new GameProfile(java.util.UUID.randomUUID(), target);
                        wl.add(new UserWhiteListEntry(profile));
                        sendMessage("Игрок " + target + " добавлен в вайтлист @" + username);
                    }
                    case "remove" -> {
                        java.io.File wlFile = new java.io.File(srv.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile(), "whitelist.json");
                        boolean found = false;
                        if (wlFile.exists()) {
                            String content = java.nio.file.Files.readString(wlFile.toPath());
                            com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseString(content).getAsJsonArray();
                            for (com.google.gson.JsonElement elem : arr) {
                                com.google.gson.JsonObject obj = elem.getAsJsonObject();
                                String name = obj.has("name") ? obj.get("name").getAsString() : "";
                                if (name.equalsIgnoreCase(target)) {
                                    String uuidStr = obj.has("uuid") ? obj.get("uuid").getAsString() : "";
                                    java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
                                    GameProfile gp = new GameProfile(uuid, name);
                                    wl.remove(gp);
                                    found = true;
                                }
                            }
                        }
                        if (!found) {
                            sendMessage("Игрок " + target + " не найден в вайтлисте");
                        } else {
                            sendMessage("Игрок " + target + " удалён из вайтлиста @" + username);
                        }
                    }
                    default -> sendMessage("Использование: /zwl add &lt;ник&gt; ИЛИ /zwl remove &lt;ник&gt;");
                }
            } catch (Exception e) {
                LOGGER.error("[KsuChat] Ошибка вайтлиста", e);
                sendMessage("Ошибка: " + e.getMessage());
            }
        });
    }

    private void handleMyId(long userId, String username) {
        boolean admin = config.isAdmin(userId);
        sendMessage("Ваш Telegram ID: <code>" + userId + "</code>\nUsername: @" + username + "\nАдмин: " + (admin ? "ДА" : "НЕТ") + "\n\nДобавьте этот ID в telegramAdmins в config/ksuchat.toml");
    }

    private void handleHelp() {
        sendMessage("""
            KsuChat Команды:

            /help - Показать эту справку
            /myid - Показать ваш Telegram ID (для настройки)
            /players - Список игроков онлайн
            /kick *ник* - Кикнуть игрока (админ)
            /zwl add *ник* - Добавить в вайтлист (админ)
            /zwl remove *ник* - Убрать из вайтлиста (админ)

            Фото: отправляйте как файл для качества без сжатия!
            """);
//            /stop - Остановить сервер (админ)
//            /restart - Перезагрузить сервер (админ)
//            /ban *ник* - Забанить игрока (админ)
//            /unban *ник* - Разбанить игрока (админ)
    }

    private void post(String method, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + config.getBotToken() + "/" + method))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
        if (!json.get("ok").getAsBoolean()) {
            String desc = json.has("description") ? json.get("description").getAsString() : "неизвестная ошибка";
            LOGGER.error("[KsuChat] Ошибка Telegram API {}: {}", method, desc);
        }
    }
}
