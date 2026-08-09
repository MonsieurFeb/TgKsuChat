package ksu.chat.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record SyncGroupsPayload(List<String> allGroups, List<String> myGroups) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncGroupsPayload> TYPE = new CustomPacketPayload.Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ksuchat", "sync_groups"));

    public static final StreamCodec<FriendlyByteBuf, SyncGroupsPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.allGroups.size());
                for (String g : payload.allGroups) buf.writeUtf(g);
                buf.writeVarInt(payload.myGroups.size());
                for (String g : payload.myGroups) buf.writeUtf(g);
            },
            (buf) -> {
                int allSize = buf.readVarInt();
                List<String> all = new ArrayList<>();
                for (int i = 0; i < allSize; i++) all.add(buf.readUtf());
                int mySize = buf.readVarInt();
                List<String> my = new ArrayList<>();
                for (int i = 0; i < mySize; i++) my.add(buf.readUtf());
                return new SyncGroupsPayload(all, my);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
