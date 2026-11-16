package ksu.chat;

import ksu.chat.net.PhotoPacket;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

/**
 * Серверная (и общая) реализация прокси. Клиентская версия находится в
 * {@code ksu.chat.client.ClientProxy}.
 */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
    }

    public void init(FMLInitializationEvent event) {
    }

    /** Вызывается на клиенте при получении фрагмента фото. */
    public void handlePhoto(PhotoPacket message) {
    }
}
