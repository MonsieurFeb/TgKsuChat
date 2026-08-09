package ksu.chat;

import com.mojang.logging.LogUtils;
import ksu.chat.chat.ChatChannel;
import ksu.chat.chat.ChatManager;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import java.util.List;

public class EventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChatMessage(ServerChatEvent event) {
        event.setCanceled(true);

        ServerPlayer player = event.getPlayer();
        String playerName = player.getName().getString();
        Component displayName = event.getPlayer().getDisplayName();
        String message = event.getRawText();
        Component messageText = event.getMessage();

        ChatManager chatManager = KsuChatMod.getChatManager();
        if (chatManager == null) return;

        ChatChannel channel = chatManager.getActiveChannel(player.getUUID());
        String groupName = chatManager.getActiveGroup(player.getUUID());

        if (channel == ChatChannel.GROUP) {
            if (groupName == null || groupName.isEmpty()) {
                channel = ChatChannel.LOCAL;
            } else if (!KsuChatMod.getGroupManager().isMember(groupName, player.getUUID())) {
                player.sendSystemMessage(Component.literal("Вы не в группе " + groupName));
                return;
            }
        }

        Component fullMessage = Component.literal("<")
                .append(displayName)
                .append(Component.literal("> "))
                .append(messageText);

        List<ServerPlayer> recipients = chatManager.getRecipients(player, channel, groupName);
        for (ServerPlayer p : recipients) {
            p.sendSystemMessage(fullMessage);
        }

        LOGGER.info("[KsuChat] [{}] {}: {}", channel, playerName, message);

        if (channel == ChatChannel.GLOBAL) {
            TelegramBot bot = KsuChatMod.getTelegramBot();
            ModConfig config = KsuChatMod.getConfig();
            if (bot != null && config != null && config.isForwardChat()) {
                String formatted = String.format(config.getChatFormat(), playerName, message);
                bot.sendMessage(formatted);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        try {
            ModConfig config = KsuChatMod.getConfig();
            TelegramBot bot = KsuChatMod.getTelegramBot();
            if (config == null || bot == null) return;
            if (!config.isForwardJoinLeave()) return;

            String playerName = event.getEntity().getName().getString();
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

            String playerName = event.getEntity().getName().getString();
            String formatted = String.format(config.getLeaveFormat(), playerName);
            bot.sendMessage(formatted);
            LOGGER.info("[KsuChat] Игрок вышел: {}", playerName);
        } catch (Exception e) {
            LOGGER.error("[KsuChat] Ошибка обработки события выхода", e);
        }
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        try {
            ModConfig config = KsuChatMod.getConfig();
            TelegramBot bot = KsuChatMod.getTelegramBot();
            if (config == null || bot == null) return;
            if (!config.isForwardDeaths()) return;
            if (!(event.getEntity() instanceof ServerPlayer player)) return;

            // String playerName = player.getName().getString();
            Component deathMessage = player.getCombatTracker().getDeathMessage();
            String deathText = deathMessage.getString();

            String formatted = String.format(config.getDeathFormat(), deathText);
            bot.sendMessage(formatted);
            LOGGER.info("[KsuChat] Смерть игрока: {}", formatted);
        } catch (Exception e) {
            LOGGER.error("[KsuChat] Ошибка обработки события смерти", e);
        }
    }

    @SubscribeEvent
    public void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        try {
            ModConfig config = KsuChatMod.getConfig();
            TelegramBot bot = KsuChatMod.getTelegramBot();
            if (config == null || bot == null) return;
            if (!config.isForwardAdvancements()) return;

            if (!(event.getEntity() instanceof ServerPlayer player)) return;
            String playerName = player.getName().getString();

            AdvancementHolder advancement = event.getAdvancement();
            String advName = advancement.value().display()
                    .map(d -> d.getTitle().getString())
                    .orElseGet(() -> advancement.id().toString());

            String formatted = String.format(config.getAdvancementFormat(), playerName, advName);
            bot.sendMessage(formatted);
            LOGGER.info("[KsuChat] Достижение: {}", formatted);
        } catch (Exception e) {
            LOGGER.error("[KsuChat] Ошибка обработки достижения", e);
        }
    }
}
