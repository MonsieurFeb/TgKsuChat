package ksu.chat.chat;

public enum ChatChannel {
    LOCAL((byte) 0, "Локальный"),
    GLOBAL((byte) 1, "Глобальный"),
    GROUP((byte) 2, "Группа");

    public final byte id;
    public final String displayName;

    ChatChannel(byte id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public static ChatChannel fromId(byte id) {
        for (ChatChannel ch : values()) {
            if (ch.id == id) return ch;
        }
        return LOCAL;
    }
}
