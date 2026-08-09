package ksu.chat.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ChatMessagePayload(String sender, String message, byte channelId, String groupName, String clickCommand) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ChatMessagePayload> TYPE = new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ksuchat", "chat_message"));

    public static final StreamCodec<FriendlyByteBuf, ChatMessagePayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.sender);
                buf.writeUtf(payload.message);
                buf.writeByte(payload.channelId);
                buf.writeUtf(payload.groupName != null ? payload.groupName : "");
                buf.writeUtf(payload.clickCommand != null ? payload.clickCommand : "");
            },
            (buf) -> new ChatMessagePayload(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readByte(),
                    buf.readUtf(),
                    buf.readUtf()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
