package ksu.chat.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ChatSendPayload(String message, byte channelId, String groupName) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ChatSendPayload> TYPE = new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ksuchat", "chat_send"));

    public static final StreamCodec<FriendlyByteBuf, ChatSendPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.message);
                buf.writeByte(payload.channelId);
                buf.writeUtf(payload.groupName != null ? payload.groupName : "");
            },
            (buf) -> new ChatSendPayload(
                    buf.readUtf(),
                    buf.readByte(),
                    buf.readUtf()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
