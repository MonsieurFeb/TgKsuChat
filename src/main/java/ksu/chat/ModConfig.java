package ksu.chat;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Path CONFIG_PATH = resolveConfigPath();

    private static Path resolveConfigPath() {
        try {
            return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("ksuchat.toml");
        } catch (Exception e) {
            return Path.of("config", "ksuchat.toml");
        }
    }

    private String botToken = "";
    private long chatId = 0;
    private List<Long> telegramAdmins = new ArrayList<>();

    private boolean forwardChat = true;
    private String chatFormat = "<%s> %s";

    private boolean forwardJoinLeave = true;
    private String joinFormat = "[+] %s зашёл на сервер";
    private String leaveFormat = "[-] %s вышел с сервера";

    private boolean forwardDeaths = true;
    private String deathFormat = "[Смерть] %s";

    private boolean forwardAdvancements = true;
    private String advancementFormat = "[Достижение] %s получил достижение [%s]";

    private String serverStartMessage = "Сервер запущен!";
    private String serverStopMessage = "Сервер останавливается...";

    private String proxyType = "";
    private String proxyHost = "";
    private int proxyPort = 1080;

    public void reload() {
        try {
            Path parent = CONFIG_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(CONFIG_PATH)) {
                load();
            } else {
                save();
            }
        } catch (IOException e) {
            LOGGER.error("[KsuChat] Ошибка загрузки конфигурации", e);
        }
    }

    private static String cleanValue(String value) {
        value = value.trim();
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String cleanListItem(String item) {
        item = item.trim();
        if (item.startsWith("\"") && item.endsWith("\"")) {
            item = item.substring(1, item.length() - 1);
        }
        return item;
    }

    private void load() throws IOException {
        List<String> lines = Files.readAllLines(CONFIG_PATH);
        telegramAdmins.clear();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            int eq = line.indexOf('=');
            if (eq < 0) continue;
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();

            value = cleanValue(value);

            switch (key) {
                case "botToken" -> botToken = value;
                case "chatId" -> {
                    try {
                        chatId = Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        LOGGER.error("[KsuChat] Неверное значение chatId: '{}'", value);
                    }
                }
                case "telegramAdmins" -> {
                    value = value.replace("[", "").replace("]", "").trim();
                    if (!value.isEmpty()) {
                        String[] parts = value.split(",");
                        for (String part : parts) {
                            part = cleanListItem(part);
                            if (!part.isEmpty()) {
                                try {
                                    telegramAdmins.add(Long.parseLong(part));
                                } catch (NumberFormatException e) {
                                    LOGGER.error("[KsuChat] Неверный ID админа: '{}'", part);
                                }
                            }
                        }
                    }
                }
                case "forwardChat" -> forwardChat = Boolean.parseBoolean(value);
                case "chatFormat" -> chatFormat = value;
                case "forwardJoinLeave" -> forwardJoinLeave = Boolean.parseBoolean(value);
                case "joinFormat" -> joinFormat = value;
                case "leaveFormat" -> leaveFormat = value;
                case "forwardDeaths" -> forwardDeaths = Boolean.parseBoolean(value);
                case "deathFormat" -> deathFormat = value;
                case "forwardAdvancements" -> forwardAdvancements = Boolean.parseBoolean(value);
                case "advancementFormat" -> advancementFormat = value;
                case "serverStartMessage" -> serverStartMessage = value;
                case "serverStopMessage" -> serverStopMessage = value;
                case "proxyType" -> proxyType = value;
                case "proxyHost" -> proxyHost = value;
                case "proxyPort" -> {
                    try {
                        proxyPort = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        LOGGER.error("[KsuChat] Неверное значение proxyPort: '{}'", value);
                    }
                }
                default -> LOGGER.warn("[KsuChat] Неизвестный ключ конфигурации: {}", key);
            }
        }
        LOGGER.info("[KsuChat] Конфигурация загружена. ID админов: {}", telegramAdmins);
    }

    private void save() throws IOException {
        String content = """
                # ============================================
                # Конфигурация KsuChat
                # ============================================

                # Токен Telegram Bot API от @BotFather
                botToken = ""

                # ID чата Telegram, куда будут отправляться сообщения
                chatId = 0

                # Список Telegram ID пользователей с доступом админа
                # Узнать свой ID можно командой /myid в боте
                # Пример: telegramAdmins = [123456789, 987654321]
                telegramAdmins = []

                # ---- Настройки прокси ----
                # Если Telegram заблокирован, установите proxyType в "socks" или "http"
                # Оставьте пустым для прямого подключения
                proxyType = ""
                proxyHost = ""
                proxyPort = 1080

                # ---- Сообщения чата ----
                forwardChat = true
                chatFormat = "<%s> %s"

                # ---- Вход/Выход ----
                forwardJoinLeave = true
                joinFormat = "[+] %s зашёл на сервер"
                leaveFormat = "[-] %s вышел с сервера"

                # ---- Смерти ----
                forwardDeaths = true
                deathFormat = "[Смерть] %s"

                # ---- Достижения ----
                forwardAdvancements = true
                advancementFormat = "[Достижение] %s получил достижение [%s]"

                # ---- Жизненный цикл сервера ----
                serverStartMessage = "Сервер запущен!"
                serverStopMessage = "Сервер останавливается..."
                """;
        Files.writeString(CONFIG_PATH, content);
        LOGGER.info("[KsuChat] Конфигурация по умолчанию создана в {}", CONFIG_PATH);
    }

    public String getBotToken() { return botToken; }
    public long getChatId() { return chatId; }
    public List<Long> getTelegramAdmins() { return telegramAdmins; }
    public boolean isForwardChat() { return forwardChat; }
    public String getChatFormat() { return chatFormat; }
    public boolean isForwardJoinLeave() { return forwardJoinLeave; }
    public String getJoinFormat() { return joinFormat; }
    public String getLeaveFormat() { return leaveFormat; }
    public boolean isForwardDeaths() { return forwardDeaths; }
    public String getDeathFormat() { return deathFormat; }
    public boolean isForwardAdvancements() { return forwardAdvancements; }
    public String getAdvancementFormat() { return advancementFormat; }
    public String getServerStartMessage() { return serverStartMessage; }
    public String getServerStopMessage() { return serverStopMessage; }
    public String getProxyType() { return proxyType; }
    public String getProxyHost() { return proxyHost; }
    public int getProxyPort() { return proxyPort; }

    public boolean isAdmin(long telegramUserId) {
        return telegramAdmins.contains(telegramUserId);
    }

    public boolean isConfigured() {
        return botToken != null && !botToken.isEmpty() && chatId != 0;
    }
}
