package ksu.chat;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.logging.LogUtils;
import ksu.chat.chat.ChatChannel;
import ksu.chat.chat.ChatManager;
import ksu.chat.chat.GroupManager;
import ksu.chat.net.*;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Mod(KsuChatMod.MODID)
public class KsuChatMod {
    public static final String MODID = "ksuchat";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static TelegramBot telegramBot;
    private static ModConfig config;
    private static final Map<UUID, byte[]> photoCache = new ConcurrentHashMap<>();
    private static final Map<UUID, byte[][]> photoChunks = new ConcurrentHashMap<>();
    private static final int PHOTO_CHUNK_SIZE = 262144;
    private static ChatManager chatManager;
    private static GroupManager groupManager;

    public KsuChatMod(IEventBus modEventBus) {
        config = new ModConfig();
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new EventHandler());
        modEventBus.addListener(this::onRegisterPayloads);
        if (FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            try {
//                NeoForge.EVENT_BUS.register(new ksu.chat.client.ClientEventHandler());
            } catch (Exception ignored) {}
        }
        LOGGER.info("[KsuChat] Мод загружен.");
    }

    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MODID);

        registrar.playToClient(PhotoPayload.TYPE, PhotoPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                if (FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                    try {
                        byte[][] chunks = photoChunks.computeIfAbsent(payload.id(), k -> new byte[payload.total()][]);
                        chunks[payload.index()] = payload.data();
                        for (byte[] c : chunks) {
                            if (c == null) {
                                return;
                            }
                        }
                        photoChunks.remove(payload.id());
                        int size = 0;
                        for (byte[] c : chunks) {
                            size += c.length;
                        }
                        byte[] full = new byte[size];
                        int pos = 0;
                        for (byte[] c : chunks) {
                            System.arraycopy(c, 0, full, pos, c.length);
                            pos += c.length;
                        }
                        Class.forName("ksu.chat.client.PhotoScreen")
                                .getMethod("open", byte[].class)
                                .invoke(null, (Object) full);
                    } catch (Exception e) {
                        LOGGER.error("[KsuChat] Не удалось открыть экран фото", e);
                    }
                }
            });
        });

        registrar.playToClient(ChatMessagePayload.TYPE, ChatMessagePayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                if (FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                    handleClientChatMessage(payload);
                }
            });
        });

        registrar.playToClient(SyncGroupsPayload.TYPE, SyncGroupsPayload.CODEC, (payload, ctx) -> {
            ctx.enqueueWork(() -> {
                if (FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                    try {
                        Class.forName("ksu.chat.client.KsuChatScreen")
                                .getMethod("syncGroups", java.util.List.class, java.util.List.class)
                                .invoke(null, payload.allGroups(), payload.myGroups());
                    } catch (Exception e) {
                        LOGGER.error("[KsuChat] Не удалось синхронизировать группы на клиенте", e);
                    }
                }
            });
        });

        registrar.playToServer(ChatSendPayload.TYPE, ChatSendPayload.CODEC, (payload, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ctx.enqueueWork(() -> handleServerChatMessage(player, payload));
        });

        registrar.playToServer(SetActiveChannelPayload.TYPE, SetActiveChannelPayload.CODEC, (payload, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ctx.enqueueWork(() -> {
                //ChatChannel channel = ChatChannel.fromId(payload.channelId());
                ChatChannel channel = ChatChannel.GLOBAL;
                getChatManager().setActiveChannel(player.getUUID(), channel, payload.groupName());
            });
        });

        registrar.playToServer(GroupActionPayload.TYPE, GroupActionPayload.CODEC, (payload, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            ctx.enqueueWork(() -> handleGroupAction(player, payload));
        });
    }

    private static void handleClientChatMessage(ChatMessagePayload payload) {
        try {
            //ChatChannel channel = ChatChannel.fromId(payload.channelId());
            ChatChannel channel = ChatChannel.GLOBAL;
            String tabName;
            net.minecraft.ChatFormatting color;
            switch (channel) {
                case GLOBAL -> {
                    tabName = "Глобальный";
                    color = net.minecraft.ChatFormatting.GREEN;
                }
                case GROUP -> {
                    tabName = payload.groupName();
                    color = net.minecraft.ChatFormatting.AQUA;
                }
                default -> {
                    tabName = "Локальный";
                    color = net.minecraft.ChatFormatting.WHITE;
                }
            }
            if (FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                try {
                    Class<?> screenClass = Class.forName("ksu.chat.client.KsuChatScreen");
                    screenClass.getMethod("addMessage", String.class, String.class, String.class, net.minecraft.ChatFormatting.class, String.class)
                            .invoke(null, tabName, payload.sender(), payload.message(), color, payload.clickCommand());
                } catch (Exception e) {
                    LOGGER.error("[KsuChat] Не удалось добавить сообщение в чат клиента", e);
                }
                try {
                    String prefix = switch (channel) {
                        case GLOBAL -> "§a[Глобальный] §r";
                        case GROUP -> "§b[Группа " + payload.groupName() + "] §r";
                        default -> "§7[Локальный] §r";
                    };
                    net.minecraft.network.chat.Component chatMsg = net.minecraft.network.chat.Component.literal(prefix)
                            .append(net.minecraft.network.chat.Component.literal("<" + payload.sender() + "> "));
                    if (payload.clickCommand() != null && !payload.clickCommand().isEmpty()) {
                        chatMsg = chatMsg.copy().append(
                                net.minecraft.network.chat.Component.literal("[Фото]")
                                        .withStyle(s -> s.withColor(net.minecraft.ChatFormatting.YELLOW)
                                                .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                                        net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                                        payload.clickCommand()))));
                        String remainder = stripPhotoMarker(payload.message());
                        if (!remainder.isEmpty()) {
                            chatMsg = chatMsg.copy().append(net.minecraft.network.chat.Component.literal(remainder));
                        }
                    } else {
                        chatMsg = chatMsg.copy().append(net.minecraft.network.chat.Component.literal(payload.message()));
                    }
                    net.minecraft.client.Minecraft.getInstance().gui.getChat().addMessage(chatMsg);
                } catch (Exception e) {
                    LOGGER.error("[KsuChat] Не удалось добавить сообщение в ванильный чат", e);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[KsuChat] Ошибка обработки сообщения чата на клиенте", e);
        }
    }

    private static String stripPhotoMarker(String message) {
        String m = message;
        if (m.startsWith("[Фото HD]")) {
            m = m.substring("[Фото HD]".length());
        } else if (m.startsWith("[Фото]")) {
            m = m.substring("[Фото]".length());
        }
        return m.trim();
    }

    private void handleServerChatMessage(ServerPlayer sender, ChatSendPayload payload) {
        //ChatChannel channel = ChatChannel.fromId(payload.channelId());
        ChatChannel channel = ChatChannel.GLOBAL;
        String senderName = sender.getName().getString();
        String message = payload.message();
        String groupName = payload.groupName();

        if (channel == ChatChannel.GROUP) {
            if (groupName == null || groupName.isEmpty()) return;
            if (!getGroupManager().isMember(groupName, sender.getUUID())) {
                sender.sendSystemMessage(Component.literal("Вы не в группе " + groupName));
                return;
            }
        }

        ChatMessagePayload out = new ChatMessagePayload(senderName, message, channel.id, groupName, null);
        var recipients = getChatManager().getRecipients(sender, channel, groupName);
        for (ServerPlayer p : recipients) {
            p.connection.send(out);
        }

        LOGGER.info("[KsuChat] [{}] {}: {}", channel, senderName, message);

        if (channel == ChatChannel.GLOBAL && telegramBot != null) {
            telegramBot.sendMessage("<" + senderName + "> " + message);
        }
    }

    private void handleGroupAction(ServerPlayer player, GroupActionPayload payload) {
        String action = payload.action();
        String groupName = payload.groupName();
        String password = payload.password();
        UUID playerId = player.getUUID();

        switch (action) {
            case "create" -> {
                if (groupName == null || groupName.isEmpty() || password == null || password.isEmpty()) {
                    player.sendSystemMessage(Component.literal("Использование: /group create <имя> <пароль>"));
                    return;
                }
                if (getGroupManager().createGroup(groupName, password, playerId)) {
                    player.sendSystemMessage(Component.literal("Группа " + groupName + " создана!"));
                    getChatManager().setActiveChannel(playerId, ChatChannel.GROUP, groupName);
                    syncGroupsToPlayer(player);
                } else {
                    player.sendSystemMessage(Component.literal("Группа " + groupName + " уже существует!"));
                }
            }
            case "join" -> {
                if (groupName == null || password == null) {
                    player.sendSystemMessage(Component.literal("Использование: /group join <имя> <пароль>"));
                    return;
                }
                if (!getGroupManager().groupExists(groupName)) {
                    player.sendSystemMessage(Component.literal("Группа " + groupName + " не найдена!"));
                    return;
                }
                if (getGroupManager().joinGroup(groupName, password, playerId)) {
                    player.sendSystemMessage(Component.literal("Вы вступили в группу " + groupName + "!"));
                    getChatManager().setActiveChannel(playerId, ChatChannel.GROUP, groupName);
                    syncGroupsToPlayer(player);
                } else {
                    player.sendSystemMessage(Component.literal("Неверный пароль или группа!"));
                }
            }
            case "leave" -> {
                if (groupName == null) {
                    player.sendSystemMessage(Component.literal("Использование: /group leave <имя>"));
                    return;
                }
                if (getGroupManager().leaveGroup(groupName, playerId)) {
                    player.sendSystemMessage(Component.literal("Вы вышли из группы " + groupName));
                    getChatManager().setActiveChannel(playerId, ChatChannel.LOCAL, null);
                    syncGroupsToPlayer(player);
                } else {
                    player.sendSystemMessage(Component.literal("Ошибка!"));
                }
            }
            case "list" -> {
                syncGroupsToPlayer(player);
            }
        }
    }

    private void syncGroupsToPlayer(ServerPlayer player) {
        var allGroups = getGroupManager().getAllGroups().stream()
                .map(g -> g.name).toList();
        var myGroups = getGroupManager().getPlayerGroups(player.getUUID());
        player.connection.send(new SyncGroupsPayload(allGroups, myGroups));
    }

    @SubscribeEvent
    public void onCommandsRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("group").then(
                Commands.argument("action", com.mojang.brigadier.arguments.StringArgumentType.string()).then(
                        Commands.argument("name", com.mojang.brigadier.arguments.StringArgumentType.string()).then(
                                Commands.argument("password", com.mojang.brigadier.arguments.StringArgumentType.string()).executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String action = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "action");
                                    String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                                    String pass = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "password");
                                    handleGroupAction(player, new GroupActionPayload(action, name, pass));
                                    return 1;
                                })
                        ).executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            String action = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "action");
                            String name = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "name");
                            handleGroupAction(player, new GroupActionPayload(action, name, null));
                            return 1;
                        })
                ).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    String action = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "action");
                    handleGroupAction(player, new GroupActionPayload(action, null, null));
                    return 1;
                })
        ));

        event.getDispatcher().register(Commands.literal("ksu_photo").then(
                Commands.argument("id", net.minecraft.commands.arguments.UuidArgument.uuid()).executes(ctx -> {
                    UUID id = net.minecraft.commands.arguments.UuidArgument.getUuid(ctx, "id");
                    byte[] data = photoCache.get(id);
                    if (data != null) {
                        var player = ctx.getSource().getPlayerOrException();
                        int total = Math.max(1, (data.length + PHOTO_CHUNK_SIZE - 1) / PHOTO_CHUNK_SIZE);
                        for (int i = 0; i < total; i++) {
                            int from = i * PHOTO_CHUNK_SIZE;
                            int to = Math.min(data.length, from + PHOTO_CHUNK_SIZE);
                            player.connection.send(new PhotoPayload(id, i, total, java.util.Arrays.copyOfRange(data, from, to)));
                        }
                        ctx.getSource().sendSuccess(() -> Component.literal("Открываю фото..."), false);
                    } else {
                        ctx.getSource().sendFailure(Component.literal("Фото не найдено или истекло"));
                    }
                    return 1;
                })
        ));
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncGroupsToPlayer(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (chatManager != null) {
                chatManager.playerLeft(player.getUUID());
            }
        }
    }

    public static UUID cachePhoto(byte[] data) {
        UUID id = UUID.randomUUID();
        photoCache.put(id, data);
        return id;
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[KsuChat] Сервер запускается, инициализация Telegram-бота...");
        config.reload();

        Path dataDir = event.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("ksuchat");
        groupManager = new GroupManager(dataDir);
        chatManager = new ChatManager(groupManager);

        telegramBot = new TelegramBot(config);
        telegramBot.setServer(event.getServer());
        telegramBot.setChatManager(chatManager);
        telegramBot.start();
        telegramBot.sendMessage(config.getServerStartMessage());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[KsuChat] Сервер останавливается, отключение Telegram-бота...");
        if (telegramBot != null) {
            telegramBot.sendMessage(config.getServerStopMessage());
            telegramBot.stop();
            telegramBot = null;
        }
        if (groupManager != null) {
            groupManager.save();
        }
    }

    public static TelegramBot getTelegramBot() {
        return telegramBot;
    }

    public static ModConfig getConfig() {
        return config;
    }

    public static ChatManager getChatManager() {
        return chatManager;
    }

    public static GroupManager getGroupManager() {
        return groupManager;
    }
}
