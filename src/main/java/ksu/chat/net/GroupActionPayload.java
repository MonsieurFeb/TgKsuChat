package ksu.chat.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GroupActionPayload(String action, String groupName, String password) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GroupActionPayload> TYPE = new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ksuchat", "group_action"));

    public static final StreamCodec<FriendlyByteBuf, GroupActionPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.action);
                buf.writeUtf(payload.groupName != null ? payload.groupName : "");
                buf.writeUtf(payload.password != null ? payload.password : "");
            },
            (buf) -> new GroupActionPayload(
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readUtf()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
