package ksu.chat;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.ServerChatEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Пересылает сообщения из игрового чата в Telegram.
 * Ванильный чат при этом работает как обычно и не отменяется.
 */
public class EventHandler {
    private static final Logger LOGGER = LogManager.getLogger("KsuChat");

    @SubscribeEvent
    public void onChatMessage(ServerChatEvent event) {
        ModConfig config = KsuChatMod.getConfig();
        TelegramBot bot = KsuChatMod.getTelegramBot();
        if (config == null || bot == null || !config.isForwardChat()) {
            return;
        }

        String message = event.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        String playerName = event.getUsername();
        String formatted = String.format(config.getChatFormat(), playerName, message);
        bot.sendMessage(formatted);
        LOGGER.info("[KsuChat] МК->ТГ {}: {}", playerName, message);
    }
}
