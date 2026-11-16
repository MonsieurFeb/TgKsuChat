package ksu.chat;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import ksu.chat.command.CommandKsuPhoto;
import ksu.chat.net.NetworkHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Mod(modid = KsuChatMod.MODID,
        name = KsuChatMod.NAME,
        version = KsuChatMod.VERSION,
        acceptableRemoteVersions = "*",
        acceptedMinecraftVersions = "[1.12.2]")
public class KsuChatMod {
    public static final String MODID = "ksuchat";
    public static final String NAME = "KsuChat";
    public static final String VERSION = "1.0.2";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public static final int PHOTO_CHUNK_SIZE = 262144;

    @SidedProxy(clientSide = "ksu.chat.client.ClientProxy", serverSide = "ksu.chat.CommonProxy")
    public static CommonProxy proxy;

    private static TelegramBot telegramBot;
    private static ModConfig config;
    private static MinecraftServer server;

    private static final Cache<UUID, byte[]> photoCache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .expireAfterAccess(2, TimeUnit.MINUTES)
            .removalListener(notification -> LOGGER.debug("Фото удалено из кэша: {}", notification.getKey()))
            .build();

    private static final Cache<UUID, byte[][]> photoChunks = CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .expireAfterAccess(2, TimeUnit.MINUTES)
            .removalListener(notification -> LOGGER.debug("Чанки фото удалены из кэша: {}", notification.getKey()))
            .build();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        File suggested = event.getSuggestedConfigurationFile();
        File configFile = new File(suggested.getParentFile(), "ksuchat.toml");
        config = new ModConfig(configFile);
        config.reload();

        NetworkHandler.init();
        MinecraftForge.EVENT_BUS.register(new EventHandler());
        proxy.preInit(event);

        LOGGER.info("[KsuChat] Мод загружен.");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        server = event.getServer();
        config.reload();

        telegramBot = new TelegramBot(config);
        telegramBot.setServer(server);
        telegramBot.start();

        event.registerServerCommand(new CommandKsuPhoto());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        LOGGER.info("[KsuChat] Сервер останавливается, отключение Telegram-бота...");
        if (telegramBot != null) {
            telegramBot.stop();
            telegramBot = null;
        }
        server = null;
    }

    // ------------------------------------------------------------------
    // Кэш фотографий
    // ------------------------------------------------------------------

    public static UUID cachePhoto(byte[] data) {
        UUID id = UUID.randomUUID();
        photoCache.put(id, data);
        return id;
    }

    public static byte[] getCachedPhoto(UUID id) {
        return id == null ? null : photoCache.getIfPresent(id);
    }

    public static byte[][] getPhotoChunks(UUID id, int total) throws ExecutionException {
        return photoChunks.get(id, () -> new byte[total][]);
    }

    public static void invalidatePhotoChunks(UUID id) {
        photoChunks.invalidate(id);
    }

    // ------------------------------------------------------------------
    // Доступ к общему состоянию
    // ------------------------------------------------------------------

    public static CommonProxy getProxy() {
        return proxy;
    }

    public static TelegramBot getTelegramBot() {
        return telegramBot;
    }

    public static ModConfig getConfig() {
        return config;
    }

    public static MinecraftServer getServer() {
        return server;
    }
}
