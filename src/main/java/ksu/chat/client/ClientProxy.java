package ksu.chat.client;

import ksu.chat.CommonProxy;
import ksu.chat.KsuChatMod;
import ksu.chat.net.PhotoPacket;
import net.minecraft.client.Minecraft;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Клиентская реализация прокси: приём фотографий из Telegram и их показ.
 * Клиентский мод нужен только для просмотра фото; чат работает ванильно.
 */
public class ClientProxy extends CommonProxy {
    private static final Logger LOGGER = LogManager.getLogger("KsuChat");

    @Override
    public void handlePhoto(final PhotoPacket message) {
        Minecraft.getMinecraft().addScheduledTask(new Runnable() {
            @Override
            public void run() {
                try {
                    UUID id = message.getId();
                    byte[][] chunks = KsuChatMod.getPhotoChunks(id, message.getTotal());
                    chunks[message.getIndex()] = message.getData();

                    int size = 0;
                    for (byte[] chunk : chunks) {
                        if (chunk == null) {
                            return;
                        }
                        size += chunk.length;
                    }

                    byte[] full = new byte[size];
                    int pos = 0;
                    for (byte[] chunk : chunks) {
                        System.arraycopy(chunk, 0, full, pos, chunk.length);
                        pos += chunk.length;
                    }
                    KsuChatMod.invalidatePhotoChunks(id);
                    PhotoScreen.open(full);
                } catch (ExecutionException e) {
                    LOGGER.error("[KsuChat] Ошибка получения фрагментов фото: {}", message.getId(), e);
                } catch (Exception e) {
                    LOGGER.error("[KsuChat] Не удалось открыть экран фото", e);
                }
            }
        });
    }
}
