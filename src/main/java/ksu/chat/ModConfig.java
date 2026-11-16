package ksu.chat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    private static final Logger LOGGER = LogManager.getLogger("KsuChat");

    private final File configFile;

    private String botToken = "";
    private long chatId = 0L;
    private final List<Long> telegramAdmins = new ArrayList<Long>();

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

    public ModConfig(File configFile) {
        this.configFile = configFile;
    }

    public void reload() {
        try {
            File parent = configFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                LOGGER.warn("[KsuChat] Не удалось создать каталог конфигурации {}", parent);
            }
            if (configFile.isFile()) {
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
        List<String> lines = Files.readAllLines(configFile.toPath(), StandardCharsets.UTF_8);
        telegramAdmins.clear();

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int eq = line.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = cleanValue(line.substring(eq + 1).trim());

            if ("botToken".equals(key)) {
                botToken = value;
            } else if ("chatId".equals(key)) {
                try {
                    chatId = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    LOGGER.error("[KsuChat] Неверное значение chatId: '{}'", value);
                }
            } else if ("telegramAdmins".equals(key)) {
                value = value.replace("[", "").replace("]", "").trim();
                if (!value.isEmpty()) {
                    String[] parts = value.split(",");
                    for (String part : parts) {
                        part = part.trim();
                        if (part.startsWith("\"") && part.endsWith("\"") && part.length() >= 2) {
                            part = part.substring(1, part.length() - 1);
                        }
                        if (!part.isEmpty()) {
                            try {
                                telegramAdmins.add(Long.parseLong(part));
                            } catch (NumberFormatException e) {
                                LOGGER.error("[KsuChat] Неверный ID админа: '{}'", part);
                            }
                        }
                    }
                }
            } else if ("forwardChat".equals(key)) {
                forwardChat = Boolean.parseBoolean(value);
            } else if ("chatFormat".equals(key)) {
                chatFormat = value;
            } else if ("proxyType".equals(key)) {
                proxyType = value;
            } else if ("proxyHost".equals(key)) {
                proxyHost = value;
            } else if ("proxyPort".equals(key)) {
                try {
                    proxyPort = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    LOGGER.error("[KsuChat] Неверное значение proxyPort: '{}'", value);
                }
            } else {
                LOGGER.debug("[KsuChat] Пропущен неизвестный ключ конфигурации: {}", key);
            }
        }
        LOGGER.info("[KsuChat] Конфигурация загружена.");
    }

    private void save() throws IOException {
        String content = "# ============================================\n" +
                "# Конфигурация KsuChat\n" +
                "# ============================================\n\n" +
                "# Токен Telegram Bot API от @BotFather\n" +
                "botToken = \"\"\n\n" +
                "# ID чата Telegram, куда будут отправляться сообщения\n" +
                "chatId = 0\n\n" +
                "# Список Telegram ID пользователей с доступом админа\n" +
                "# Узнать свой ID можно командой /myid в боте\n" +
                "# Пример: telegramAdmins = [123456789, 987654321]\n" +
                "telegramAdmins = []\n\n" +
                "# ---- Пересылка сообщений ----\n" +
                "# Minecraft -> Telegram\n" +
                "forwardChat = true\n" +
                "chatFormat = \"<%s> %s\"\n\n" +
                "# ---- Настройки прокси ----\n" +
                "# Если Telegram заблокирован, установите proxyType в \"socks\" или \"http\"\n" +
                "# Оставьте пустым для прямого подключения\n" +
                "proxyType = \"\"\n" +
                "proxyHost = \"\"\n" +
                "proxyPort = 1080\n" +
                "# ---- Сообщения чата ----\n" +
                "forwardChat = true\n" +
                "chatFormat = \"<%s> %s\"\n\n" +
                "# ---- Вход/Выход ----\n" +
                "forwardJoinLeave = true\n" +
                "joinFormat = \"[+] %s зашёл на сервер\"\n" +
                "leaveFormat = \"[-] %s вышел с сервера\"\n\n" +
                "# ---- Смерти ----\n" +
                "forwardDeaths = true\n" +
                "deathFormat = \"[Смерть] %s\"\n\n" +
                "# ---- Достижения ----\n" +
                "forwardAdvancements = true\n" +
                "advancementFormat = \"[Достижение] %s получил достижение [%s]\"\n\n" +
                "# ---- Жизненный цикл сервера ----\n" +
                "serverStartMessage = \"Сервер запущен!\"\n" +
                "serverStopMessage = \"Сервер останавливается...\"\n";

        Files.write(configFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
        LOGGER.info("[KsuChat] Конфигурация по умолчанию создана в {}", configFile);
    }

    public String getBotToken() {
        return botToken;
    }

    public long getChatId() {
        return chatId;
    }

    public List<Long> getTelegramAdmins() {
        return telegramAdmins;
    }

    public boolean isForwardJoinLeave() { return forwardJoinLeave; }
    public String getJoinFormat() { return joinFormat; }
    public String getLeaveFormat() { return leaveFormat; }
    public boolean isForwardDeaths() { return forwardDeaths; }
    public String getDeathFormat() { return deathFormat; }
    public boolean isForwardAdvancements() { return forwardAdvancements; }
    public String getAdvancementFormat() { return advancementFormat; }
    public String getServerStartMessage() { return serverStartMessage; }
    public String getServerStopMessage() { return serverStopMessage; }

    public boolean isAdmin(long telegramUserId) {
        return telegramAdmins.contains(Long.valueOf(telegramUserId));
    }

    public boolean isForwardChat() {
        return forwardChat;
    }

    public String getChatFormat() {
        return chatFormat;
    }

    public String getProxyType() {
        return proxyType;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public boolean isConfigured() {
        return botToken == null || botToken.isEmpty() || chatId == 0L;
    }
}
