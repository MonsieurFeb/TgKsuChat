package ksu.chat.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

public record PhotoPayload(UUID id, int index, int total, byte[] data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PhotoPayload> TYPE = new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ksuchat", "photo"));

    public static final StreamCodec<FriendlyByteBuf, PhotoPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUUID(payload.id);
                buf.writeInt(payload.index);
                buf.writeInt(payload.total);
                buf.writeByteArray(payload.data);
            },
            (buf) -> new PhotoPayload(
                    buf.readUUID(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readByteArray()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
