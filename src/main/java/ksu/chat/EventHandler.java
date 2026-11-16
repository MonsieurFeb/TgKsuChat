package ksu.chat;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
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

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            ModConfig config = KsuChatMod.getConfig();
            TelegramBot bot = KsuChatMod.getTelegramBot();
            if (config == null || bot == null) return;
            if (!config.isForwardJoinLeave()) return;

            String playerName = event.player.getName();
            String formatted = String.format(config.getJoinFormat(), playerName);
            bot.sendMessage(formatted);
            LOGGER.info("[KsuChat] Игрок зашёл: {}", playerName);
        } catch (Exception e) {
            LOGGER.error("[KsuChat] Ошибка обработки события входа", e);
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        try {
            ModConfig config = KsuChatMod.getConfig();
            TelegramBot bot = KsuChatMod.getTelegramBot();
            if (config == null || bot == null) return;
            if (!config.isForwardJoinLeave()) return;

            String playerName = event.player.getName();
            String formatted = String.format(config.getLeaveFormat(), playerName);
            bot.sendMessage(formatted);
            LOGGER.info("[KsuChat] Игрок вышел: {}", playerName);
        } catch (Exception e) {
            LOGGER.error("[KsuChat] Ошибка обработки события выхода", e);
        }
    }

//    @SubscribeEvent
//    public void onPlayerDeath(LivingDeathEvent event) {
//        try {
//            ModConfig config = KsuChatMod.getConfig();
//            TelegramBot bot = KsuChatMod.getTelegramBot();
//            if (config == null || bot == null) return;
//            if (!config.isForwardDeaths()) return;
//            if (!(event.getEntity() instanceof ServerPlayer player)) return;
//
//            Component deathMessage = player.getCombatTracker().getDeathMessage();
//            String deathText = deathMessage.getString();
//
//            String formatted = String.format(config.getDeathFormat(), deathText);
//            bot.sendMessage(formatted);
//            LOGGER.info("[KsuChat] Смерть игрока: {}", formatted);
//        } catch (Exception e) {
//            LOGGER.error("[KsuChat] Ошибка обработки события смерти", e);
//        }
//    }
//
//    @SubscribeEvent
//    public void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
//        try {
//            ModConfig config = KsuChatMod.getConfig();
//            TelegramBot bot = KsuChatMod.getTelegramBot();
//            if (config == null || bot == null) return;
//            if (!config.isForwardAdvancements()) return;
//
//            if (!(event.getEntity() instanceof ServerPlayer player)) return;
//            String playerName = player.getName().getString();
//
//            AdvancementHolder advancement = event.getAdvancement();
//            String advName = advancement.value().display()
//                    .map(d -> d.getTitle().getString())
//                    .orElseGet(() -> advancement.id().toString());
//
//            String formatted = String.format(config.getAdvancementFormat(), playerName, advName);
//            bot.sendMessage(formatted);
//            LOGGER.info("[KsuChat] Достижение: {}", formatted);
//        } catch (Exception e) {
//            LOGGER.error("[KsuChat] Ошибка обработки достижения", e);
//        }
//    }
}
