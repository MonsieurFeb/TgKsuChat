# TgKsuChat

<p align="center">
  <img src="icon.jpg" width="300" alt="icon.jpg">
</p>

Небольшой мод для приватного сервера, который пересылает сообщения между чатом Minecraft и Telegram в обе стороны. Дополнительно умеет показывать фотографии из Telegram прямо в игре.

## Версии

- Minecraft **1.21.1**
- NeoForge **21.1.2xx**
- Java **21**

Мод ставится на сервер. Клиентская часть **опциональна**: без неё чат и пересылка сообщений работают как обычно, но фотографии из Telegram посмотреть в игре нельзя.

## Что делает мод

- **Minecraft → Telegram**: сообщения игроков из обычного чата отправляются в указанный Telegram-чат.
- **Telegram → Minecraft**: сообщения из Telegram появляются в игровом чате с префиксом `[ТГ]`.
- **Фотографии**: фото из Telegram приходит в игру кликабельной меткой `[Фото]` и подписью. По клику открывается полноэкранный просмотр (нужен клиентский мод).

## Конфигурация

При первом запуске создаётся файл `config/ksuchat.toml`:

```toml
# Токен Telegram Bot API от @BotFather
botToken = ""

# ID чата Telegram, куда будут отправляться сообщения
chatId = 0

# Telegram ID пользователей с доступом к админ-командам
# Узнать свой ID можно командой /myid в боте
telegramAdmins = []

# ---- Пересылка сообщений ----
# Minecraft -> Telegram
forwardChat = true
chatFormat = "<%s> %s"

# ---- Настройки прокси ----
# Если Telegram заблокирован, установите proxyType в "socks" или "http"
# Оставьте пустым для прямого подключения
proxyType = ""
proxyHost = ""
proxyPort = 1080
```

Как получить значения:

1. Создайте бота через [@BotFather](https://t.me/BotFather) и скопируйте токен в `botToken`.
2. Добавьте бота в нужный Telegram-чат.
3. Узнайте ID чата (например, через `@getmyid_bot` или `@RawDataBot`) и укажите его в `chatId`.
4. Перезапустите сервер.

## Команды

Команды вводятся в Telegram-чате, к которому подключён бот:

| Команда | Описание |
| --- | --- |
| `/players` | Показать список игроков онлайн. |
| `/kick <игрок>` | Кикнуть игрока (только для `telegramAdmins`). |
| `/zwl add <игрок>` | Добавить игрока в вайтлист (только для `telegramAdmins`). |
| `/zwl remove <игрок>` | Удалить игрока из вайтлиста (только для `telegramAdmins`). |
| `/myid` | Показать свой Telegram ID — его нужно добавить в `telegramAdmins`. |
| `/start`, `/help` | Показать справку по командам. |

(zwl - простенький мод на вайтлист, при желании, команду можно заменить)

## Сборка

Требуются **JDK 21** и Gradle 9.x. В проекте есть Gradle Wrapper:

```bash
./gradlew build
```

Готовый мод появится в `build/libs/ksuchat-x.jar`.

## Благодарности

This project is inspired by these projects:
- https://www.curseforge.com/minecraft/mc-mods/telegram-chain
- https://modrinth.com/plugin/tgbridge
