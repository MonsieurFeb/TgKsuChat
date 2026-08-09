package ksu.chat.client;

import ksu.chat.chat.ChatChannel;
import ksu.chat.net.ChatSendPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import static ksu.chat.KsuChatMod.MODID;

public class ClientEventHandler {

    @SubscribeEvent
    public void onKeyPress(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        if (mc.screen != null) return;

        if (event.getKey() == 84 && event.getAction() == 1) {
            mc.setScreen(new KsuChatScreen());
        }
    }

    @SubscribeEvent
    public void onScreenOpening(ScreenEvent.Opening event) {
        Screen newScreen = event.getNewScreen();
        if (newScreen instanceof ChatScreen && !(newScreen instanceof KsuChatScreen)) {
            event.setNewScreen(new KsuChatScreen());
        }
    }

    @SubscribeEvent
    public void onClientChat(ClientChatEvent event) {
        String message = event.getMessage();
        if (message.isEmpty() || message.startsWith("/")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        event.setCanceled(true);
        if (mc.player.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
            mc.player.connection.send(new ChatSendPayload(message, ChatChannel.GLOBAL.id, null));
        }
    }
}
