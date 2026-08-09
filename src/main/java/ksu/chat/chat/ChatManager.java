package ksu.chat.chat;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double LOCAL_RANGE = 100.0;

    private final GroupManager groupManager;
    private final Map<UUID, ChatChannel> activeChannels = new ConcurrentHashMap<>();
    private final Map<UUID, String> activeGroupNames = new ConcurrentHashMap<>();

    public ChatManager(GroupManager groupManager) {
        this.groupManager = groupManager;
    }

    public void setActiveChannel(UUID playerId, ChatChannel channel, String groupName) {
        activeChannels.put(playerId, channel);
        if (groupName != null) {
            activeGroupNames.put(playerId, groupName);
        }
    }

    public ChatChannel getActiveChannel(UUID playerId) {
        return activeChannels.getOrDefault(playerId, ChatChannel.LOCAL);
    }

    public String getActiveGroup(UUID playerId) {
        return activeGroupNames.get(playerId);
    }

    public void playerLeft(UUID playerId) {
        activeChannels.remove(playerId);
        activeGroupNames.remove(playerId);
    }

    public List<ServerPlayer> getRecipients(ServerPlayer sender, ChatChannel channel, String groupName) {
        MinecraftServer server = sender.getServer();
        List<ServerPlayer> result = new ArrayList<>();

        switch (channel) {
            case GLOBAL -> result.addAll(server.getPlayerList().getPlayers());
            case LOCAL -> {
                Vec3 pos = sender.position();
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p.position().distanceTo(pos) <= LOCAL_RANGE) {
                        result.add(p);
                    }
                }
            }
            case GROUP -> {
                if (groupName == null) break;
                GroupManager.GroupData group = groupManager.getGroup(groupName);
                if (group == null) break;
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (group.members.contains(p.getUUID())) {
                        result.add(p);
                    }
                }
            }
        }
        return result;
    }
}
