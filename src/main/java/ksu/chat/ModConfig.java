package ksu.chat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Простой конфиг в формате key = value (файл config/ksuchat.toml).
 * Оставлены только настройки пересылки сообщений и прокси.
 */
public class ModConfig {
    private static final Logger LOGGER = LogManager.getLogger("KsuChat");

    private final File configFile;

    private String botToken = "";
    private long chatId = 0L;
    private final List<Long> telegramAdmins = new ArrayList<Long>();

    private boolean forwardChat = true;
    private String chatFormat = "<%s> %s";

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
        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            value = value.substring(1, value.length() - 1);
        }
        return value;
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
        StringBuilder content = new StringBuilder();
        content.append("# ============================================\n");
        content.append("# Конфигурация KsuChat\n");
        content.append("# ============================================\n\n");
        content.append("# Токен Telegram Bot API от @BotFather\n");
        content.append("botToken = \"\"\n\n");
        content.append("# ID чата Telegram, куда будут отправляться сообщения\n");
        content.append("chatId = 0\n\n");
        content.append("# Список Telegram ID пользователей с доступом админа\n");
        content.append("# Узнать свой ID можно командой /myid в боте\n");
        content.append("# Пример: telegramAdmins = [123456789, 987654321]\n");
        content.append("telegramAdmins = []\n\n");
        content.append("# ---- Пересылка сообщений ----\n");
        content.append("# Minecraft -> Telegram\n");
        content.append("forwardChat = true\n");
        content.append("chatFormat = \"<%s> %s\"\n\n");
        content.append("# ---- Настройки прокси ----\n");
        content.append("# Если Telegram заблокирован, установите proxyType в \"socks\" или \"http\"\n");
        content.append("# Оставьте пустым для прямого подключения\n");
        content.append("proxyType = \"\"\n");
        content.append("proxyHost = \"\"\n");
        content.append("proxyPort = 1080\n");

        Files.write(configFile.toPath(), content.toString().getBytes(StandardCharsets.UTF_8));
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
        return botToken != null && !botToken.isEmpty() && chatId != 0L;
    }
}
