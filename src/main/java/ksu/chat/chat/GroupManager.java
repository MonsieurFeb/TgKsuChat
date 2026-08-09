package ksu.chat.chat;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GroupManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Path dataDir;
    private final Path groupsFile;
    private final Map<String, GroupData> groups = new ConcurrentHashMap<>();

    public GroupManager(Path dataDir) {
        this.dataDir = dataDir;
        this.groupsFile = dataDir.resolve("groups.json");
        load();
    }

    public boolean createGroup(String name, String password, UUID creatorId) {
        if (groups.containsKey(name.toLowerCase())) return false;
        GroupData group = new GroupData(name, password, creatorId);
        group.members.add(creatorId);
        groups.put(name.toLowerCase(), group);
        save();
        LOGGER.info("[KsuChat] Группа создана: {} by {}", name, creatorId);
        return true;
    }

    public boolean joinGroup(String name, String password, UUID playerId) {
        GroupData group = groups.get(name.toLowerCase());
        if (group == null) return false;
        if (!group.password.equals(password)) return false;
        group.members.add(playerId);
        save();
        return true;
    }

    public boolean leaveGroup(String name, UUID playerId) {
        GroupData group = groups.get(name.toLowerCase());
        if (group == null) return false;
        group.members.remove(playerId);
        if (group.members.isEmpty()) {
            groups.remove(name.toLowerCase());
        }
        save();
        return true;
    }

    public boolean isMember(String name, UUID playerId) {
        GroupData group = groups.get(name.toLowerCase());
        return group != null && group.members.contains(playerId);
    }

    public GroupData getGroup(String name) {
        return groups.get(name.toLowerCase());
    }

    public Collection<GroupData> getAllGroups() {
        return groups.values();
    }

    public boolean groupExists(String name) {
        return groups.containsKey(name.toLowerCase());
    }

    public List<String> getPlayerGroups(UUID playerId) {
        List<String> result = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            if (entry.getValue().members.contains(playerId)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private void load() {
        try {
            if (!Files.exists(groupsFile)) return;
            String json = Files.readString(groupsFile);
            JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            for (String key : root.keySet()) {
                JsonObject obj = root.getAsJsonObject(key);
                String password = obj.get("password").getAsString();
                UUID creator = UUID.fromString(obj.get("creator").getAsString());
                GroupData group = new GroupData(key, password, creator);
                JsonArray members = obj.getAsJsonArray("members");
                for (var m : members) {
                    group.members.add(UUID.fromString(m.getAsString()));
                }
                groups.put(key.toLowerCase(), group);
            }
            LOGGER.info("[KsuChat] Загружено {} групп", groups.size());
        } catch (Exception e) {
            LOGGER.warn("[KsuChat] Ошибка загрузки групп: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataDir);
            JsonObject root = new JsonObject();
            for (var entry : groups.entrySet()) {
                GroupData g = entry.getValue();
                JsonObject obj = new JsonObject();
                obj.addProperty("password", g.password);
                obj.addProperty("creator", g.creatorId.toString());
                JsonArray members = new JsonArray();
                for (UUID m : g.members) {
                    members.add(m.toString());
                }
                obj.add("members", members);
                root.add(g.name, obj);
            }
            Files.writeString(groupsFile, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (Exception e) {
            LOGGER.warn("[KsuChat] Ошибка сохранения групп: {}", e.getMessage());
        }
    }

    public static class GroupData {
        public final String name;
        public final String password;
        public final UUID creatorId;
        public final Set<UUID> members = ConcurrentHashMap.newKeySet();

        public GroupData(String name, String password, UUID creatorId) {
            this.name = name;
            this.password = password;
            this.creatorId = creatorId;
        }
    }
}
