package ksu.chat.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SetActiveChannelPayload(byte channelId, String groupName) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SetActiveChannelPayload> TYPE = new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ksuchat", "set_channel"));

    public static final StreamCodec<FriendlyByteBuf, SetActiveChannelPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.channelId);
                buf.writeUtf(payload.groupName != null ? payload.groupName : "");
            },
            (buf) -> new SetActiveChannelPayload(
                    buf.readByte(),
                    buf.readUtf()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
