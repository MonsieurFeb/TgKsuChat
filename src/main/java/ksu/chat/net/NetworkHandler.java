package ksu.chat.net;

import ksu.chat.KsuChatMod;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Регистрация сетевых пакетов.
 * Оставлен только пакет с фотографией (сервер -> клиент).
 */
public final class NetworkHandler {
    private static SimpleNetworkWrapper INSTANCE;

    private NetworkHandler() {
    }

    public static void init() {
        if (INSTANCE != null) {
            return;
        }
        INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(KsuChatMod.MODID);
        INSTANCE.registerMessage(PhotoPacket.Handler.class, PhotoPacket.class, 0, Side.CLIENT);
    }

    public static void sendTo(EntityPlayerMP player, IMessage message) {
        if (INSTANCE != null && player != null) {
            INSTANCE.sendTo(message, player);
        }
    }

    /**
     * Проверяет, установлен ли KsuChat на клиенте игрока.
     * Для ванильных клиентов и клиентов без мода возвращает false.
     */
    public static boolean hasMod(EntityPlayerMP player) {
        if (player == null || player.connection == null) {
            return false;
        }
        try {
            NetworkDispatcher dispatcher = NetworkDispatcher.get(player.connection.netManager);
            MinecraftServer server = KsuChatMod.getServer();
            boolean singlePlayer = server != null && server.isSinglePlayer();
            if (dispatcher == null) {
                // Локальная игра: соединение всё равно модовое.
                return singlePlayer;
            }
            if (dispatcher.getConnectionType() == null) {
                return singlePlayer;
            }
            if (dispatcher.getConnectionType() != NetworkDispatcher.ConnectionType.MODDED) {
                return false;
            }
            return dispatcher.getModList() != null
                    && dispatcher.getModList().containsKey(KsuChatMod.MODID);
        } catch (Exception e) {
            return false;
        }
    }
}
