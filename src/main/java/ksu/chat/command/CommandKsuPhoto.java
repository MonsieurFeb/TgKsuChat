package ksu.chat.command;

import ksu.chat.KsuChatMod;
import ksu.chat.net.NetworkHandler;
import ksu.chat.net.PhotoPacket;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.UUID;

/**
 * Кликабельная команда /ksu_photo &lt;uuid&gt;, открывающая фотографию из кэша.
 */
public class CommandKsuPhoto extends CommandBase {

    @Override
    public String getName() {
        return "ksu_photo";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/ksu_photo <uuid>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 1) {
            throw new CommandException("Использование: /ksu_photo <uuid>");
        }

        UUID id;
        try {
            id = UUID.fromString(args[0]);
        } catch (IllegalArgumentException e) {
            throw new CommandException("Некорректный UUID фотографии");
        }

        byte[] data = KsuChatMod.getCachedPhoto(id);
        if (data == null) {
            throw new CommandException("Фото не найдено или истекло");
        }

        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (!NetworkHandler.hasMod(player)) {
            throw new CommandException("Для просмотра фото установите мод KsuChat на клиент");
        }

        int total = Math.max(1, (data.length + KsuChatMod.PHOTO_CHUNK_SIZE - 1) / KsuChatMod.PHOTO_CHUNK_SIZE);
        for (int i = 0; i < total; i++) {
            int from = i * KsuChatMod.PHOTO_CHUNK_SIZE;
            int to = Math.min(data.length, from + KsuChatMod.PHOTO_CHUNK_SIZE);
            byte[] chunk = new byte[to - from];
            System.arraycopy(data, from, chunk, 0, chunk.length);
            NetworkHandler.sendTo(player, new PhotoPacket(id, i, total, chunk));
        }

        player.sendMessage(new TextComponentString("Открываю фото..."));
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }
}
