package ksu.chat.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

/**
 * Сервер -> клиент: один фрагмент фотографии.
 */
public class PhotoPacket implements IMessage {
    private UUID id;
    private int index;
    private int total;
    private byte[] data = new byte[0];

    public PhotoPacket() {
    }

    public PhotoPacket(UUID id, int index, int total, byte[] data) {
        this.id = id;
        this.index = index;
        this.total = total;
        this.data = data == null ? new byte[0] : data;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        id = new UUID(buf.readLong(), buf.readLong());
        index = buf.readInt();
        total = buf.readInt();
        int length = buf.readInt();
        if (length < 0 || length > buf.readableBytes()) {
            length = 0;
        }
        data = new byte[length];
        buf.readBytes(data);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
        buf.writeInt(index);
        buf.writeInt(total);
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    public UUID getId() {
        return id;
    }

    public int getIndex() {
        return index;
    }

    public int getTotal() {
        return total;
    }

    public byte[] getData() {
        return data;
    }

    public static class Handler implements net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler<PhotoPacket, IMessage> {
        @Override
        public IMessage onMessage(PhotoPacket message, net.minecraftforge.fml.common.network.simpleimpl.MessageContext ctx) {
            ksu.chat.KsuChatMod.getProxy().handlePhoto(message);
            return null;
        }
    }
}
