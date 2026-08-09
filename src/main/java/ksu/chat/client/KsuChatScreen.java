package ksu.chat.client;

import com.mojang.logging.LogUtils;
import ksu.chat.chat.ChatChannel;
import ksu.chat.net.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.*;

import static ksu.chat.KsuChatMod.MODID;

public class KsuChatScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final List<ChatTab> tabs = new ArrayList<>();
    public static final Map<String, List<ChatLineData>> messagesByTab = new LinkedHashMap<>();
    public static int activeTabIndex = 0;
    public static List<String> allGroups = new ArrayList<>();
    public static List<String> myGroups = new ArrayList<>();

    private EditBox inputBox;
    private CommandSuggestions commandSuggestions;
    private int scrollOffset = 0;
    private EditBox groupNameBox;
    private EditBox groupPassBox;
    private String groupAction = null;
    private String groupFeedback = null;
    private int feedbackTimer = 0;

    public String selectedGroup = null;

    private int myGroupsYStart = 0;
    private int myGroupsItemHeight = 12;
    private int groupLeaveBtnX = 0;
    private int groupLeaveBtnY = 0;
    private int btnCreateX, btnCreateY;
    private int btnJoinX, btnJoinY;

    private final List<ClickArea> clickAreas = new ArrayList<>();

    static {
        tabs.add(new ChatTab("Глобальный", ChatChannel.GLOBAL, null));
        tabs.add(new ChatTab("Локальный", ChatChannel.LOCAL, null));
        tabs.add(new ChatTab("Группы", null, null));
    }

    public static void addMessage(String tabName, String sender, String message, ChatFormatting color) {
        addMessage(tabName, sender, message, color, null);
    }

    public static void addMessage(String tabName, String sender, String message, ChatFormatting color, String clickCommand) {
        messagesByTab.computeIfAbsent(tabName, k -> new ArrayList<>())
                .add(new ChatLineData(sender, message, color, clickCommand));
        List<ChatLineData> msgs = messagesByTab.get(tabName);
        if (msgs.size() > 200) {
            msgs.subList(0, msgs.size() - 200).clear();
        }
    }

    public static void syncGroups(List<String> all, List<String> my) {
        allGroups.clear();
        allGroups.addAll(all);
        myGroups.clear();
        myGroups.addAll(my);
    }

    public KsuChatScreen() {
        super(Component.literal("Чат"));
    }

    @Override
    protected void init() {
        super.init();
        inputBox = new EditBox(this.font, 10, this.height - 24, this.width - 20, 18, Component.literal("Сообщение..."));
        inputBox.setMaxLength(256);
        inputBox.setFocused(true);
        inputBox.setResponder(this::onInputChanged);
        this.addWidget(inputBox);

        this.commandSuggestions = new CommandSuggestions(this.minecraft, this, this.inputBox, this.font, false, false, 1, 10, true, -805306368);
        this.commandSuggestions.setAllowHiding(false);
        this.commandSuggestions.updateCommandInfo();

        groupNameBox = new EditBox(this.font, 0, 0, 120, 16, Component.literal("Имя группы"));
        groupNameBox.setMaxLength(32);
        this.addWidget(groupNameBox);

        groupPassBox = new EditBox(this.font, 0, 0, 120, 16, Component.literal("Пароль"));
        groupPassBox.setMaxLength(32);
        this.addWidget(groupPassBox);

        syncChannelToServer();
    }

    private void onInputChanged(String text) {
        if (commandSuggestions != null) {
            commandSuggestions.setAllowSuggestions(text.startsWith("/"));
            commandSuggestions.updateCommandInfo();
        }
    }

    private void syncChannelToServer() {
        ChatTab active = tabs.get(activeTabIndex);
        if (active.channel != null && Minecraft.getInstance().player != null) {
            if (Minecraft.getInstance().player.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
                Minecraft.getInstance().player.connection.send(
                        new SetActiveChannelPayload(active.channel.id, active.groupName));
            }
        } else if (active.channel == null && selectedGroup != null && Minecraft.getInstance().player != null) {
            if (Minecraft.getInstance().player.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
                Minecraft.getInstance().player.connection.send(
                        new SetActiveChannelPayload((byte) 2, selectedGroup));
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (commandSuggestions != null && commandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == 256) {
            if (selectedGroup != null) {
                selectedGroup = null;
                syncChannelToServer();
                return true;
            }
            if (groupAction != null) {
                groupAction = null;
                return true;
            }
            this.minecraft.setScreen(null);
            return true;
        }
        if (groupNameBox != null && groupNameBox.isFocused() || groupPassBox != null && groupPassBox.isFocused()) {
            if (keyCode == 257 || keyCode == 335) {
                submitGroupAction();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == 257 || keyCode == 335) {
            sendMessage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        ChatTab active = tabs.get(activeTabIndex);
        List<ChatLineData> msgs;
        if (active.channel == null) {
            if (selectedGroup == null) return true;
            msgs = messagesByTab.getOrDefault(selectedGroup, Collections.emptyList());
        } else {
            msgs = messagesByTab.getOrDefault(active.name, Collections.emptyList());
        }
        int maxScroll = Math.max(0, msgs.size() - getMaxVisibleLines());
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) vertical));
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        clickAreas.clear();
        guiGraphics.fill(0, 0, this.width, this.height, 0xAA0C1015);

        int tabHeight = 22;
        int tabWidth = Math.max(90, this.width / Math.max(1, tabs.size()));
        int tabY = 4;

        int panelX = 4;
        int panelY = tabY + tabHeight + 4;
        int panelW = this.width - 8;
        int panelH = this.height - 32;
        drawPanel(guiGraphics, panelX, panelY, panelW, panelH, 0xFF1B2129, 0xFF10161C, 0xFF3C4654);

        for (int i = 0; i < tabs.size(); i++) {
            ChatTab tab = tabs.get(i);
            int tabX = i * tabWidth;
            boolean isActive = (i == activeTabIndex);
            int bgColor = isActive ? 0xFF2E5B9F : 0xFF1C2228;
            int borderColor = isActive ? 0xFF6BA4FF : 0xFF3A404B;
            int textColor = isActive ? 0xFFFFFFFF : 0xFFB8C0CB;

            drawPanel(guiGraphics, tabX + 4, tabY, tabWidth - 8, tabHeight, bgColor, borderColor, borderColor);
            String label = tab.name;
            int textW = this.font.width(label);
            guiGraphics.drawString(this.font, label,
                    tabX + 4 + (tabWidth - 8 - textW) / 2, tabY + 6, textColor, true);
        }

        ChatTab active = tabs.get(activeTabIndex);

        if (active.channel == null) {
            if (selectedGroup != null) {
                renderGroupChat(guiGraphics, mouseX, mouseY, panelY + 6);
            } else {
                renderGroupsTab(guiGraphics, mouseX, mouseY, panelY + 6);
            }
        } else {
            renderChatTab(guiGraphics, mouseX, mouseY, panelY + 6);
        }

        inputBox.setX(8);
        inputBox.setY(this.height - 25);
        inputBox.setWidth(this.width - 82);
        inputBox.render(guiGraphics, mouseX, mouseY, partialTick);

        int sendX = this.width - 68;
        int sendY = this.height - 27;
        boolean hoverSend = mouseX >= sendX && mouseX <= sendX + 58 && mouseY >= sendY && mouseY <= sendY + 18;
        guiGraphics.fill(sendX, sendY, sendX + 58, sendY + 18, hoverSend ? 0xFF3E8EDB : 0xFF2E6CC5);
        guiGraphics.drawString(this.font, "Отправить", sendX + 8, sendY + 4, 0xFFFFFFFF, true);

        if (commandSuggestions != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, 200.0F);
            commandSuggestions.render(guiGraphics, mouseX, mouseY);
            guiGraphics.pose().popPose();
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderChatTab(GuiGraphics guiGraphics, int mouseX, int mouseY, int startY) {
        ChatTab active = tabs.get(activeTabIndex);
        List<ChatLineData> msgs = messagesByTab.getOrDefault(active.name, Collections.emptyList());

        int msgAreaTop = startY + 4;
        int msgAreaBottom = this.height - 34;
        int msgAreaLeft = 10;
        int msgAreaRight = this.width - 10;
        int lineHeight = 12;
        int maxVisible = (msgAreaBottom - msgAreaTop) / lineHeight;

        drawPanel(guiGraphics, msgAreaLeft, msgAreaTop, msgAreaRight - msgAreaLeft, msgAreaBottom - msgAreaTop, 0xFF141A20, 0xFF212A32, 0xFF2D3941);

        guiGraphics.enableScissor(msgAreaLeft, msgAreaTop, msgAreaRight - msgAreaLeft, msgAreaBottom - msgAreaTop);

        int startIdx = Math.max(0, msgs.size() - maxVisible - scrollOffset);
        int endIdx = Math.min(msgs.size(), startIdx + maxVisible);

        int y = msgAreaTop + 6;
        for (int i = startIdx; i < endIdx; i++) {
            ChatLineData line = msgs.get(i);
            int color = line.color != null ? line.color.getColor() : 0xFFFFFF;
            drawChatLine(guiGraphics, line, color, msgAreaLeft + 8, y);
            y += lineHeight;
        }

        guiGraphics.disableScissor();

        if (msgs.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "Нет сообщений", this.width / 2, (msgAreaTop + msgAreaBottom) / 2, 0xFF8EA3B6);
        }

        int scrollBarX = msgAreaRight - 7;
        int scrollBarY = msgAreaTop + 4;
        int barH = Math.max(18, msgAreaBottom - msgAreaTop - 8);
        if (!msgs.isEmpty()) {
            int maxScroll = Math.max(1, Math.max(0, msgs.size() - maxVisible));
            int barSize = Math.max(16, (int) ((double) maxVisible / (msgs.size() + 1) * barH));
            int barTop = scrollBarY + (int) ((double) scrollOffset / maxScroll * (barH - barSize));
            guiGraphics.fill(scrollBarX, scrollBarY, scrollBarX + 4, scrollBarY + barH, 0xFF2D3941);
            guiGraphics.fill(scrollBarX, barTop, scrollBarX + 4, Math.min(barTop + barSize, scrollBarY + barH), 0xFF7CA7FF);
        }
    }

    private void renderGroupChat(GuiGraphics guiGraphics, int mouseX, int mouseY, int startY) {
        int contentX = 10;
        int contentY = startY + 4;
        int contentW = this.width - 20;
        int contentH = this.height - 90;
        drawPanel(guiGraphics, contentX, contentY, contentW, contentH, 0xFF141A20, 0xFF212A32, 0xFF2D3941);

        int titleY = contentY + 6;
        guiGraphics.drawCenteredString(this.font, Component.literal(selectedGroup), this.width / 2, titleY, 0xFF73F29B);

        int leaveBtnSize = 16;
        int leaveBtnPad = 8;
        groupLeaveBtnX = this.width - leaveBtnSize - leaveBtnPad;
        groupLeaveBtnY = titleY - 2;
        boolean hoverLeave = mouseX >= groupLeaveBtnX && mouseX <= groupLeaveBtnX + leaveBtnSize
                && mouseY >= groupLeaveBtnY && mouseY <= groupLeaveBtnY + leaveBtnSize;
        int leaveBtnColor = hoverLeave ? 0xFFFF5555 : 0xFFCC3333;
        guiGraphics.fill(groupLeaveBtnX, groupLeaveBtnY, groupLeaveBtnX + leaveBtnSize, groupLeaveBtnY + leaveBtnSize, leaveBtnColor);
        guiGraphics.drawString(this.font, "◀", groupLeaveBtnX + leaveBtnSize / 2 - 3, groupLeaveBtnY + 3, 0xFFFFFFFF, true);

        int msgAreaTop = titleY + 18;
        int msgAreaBottom = this.height - 34;
        int lineHeight = 12;
        int maxVisible = (msgAreaBottom - msgAreaTop) / lineHeight;

        List<ChatLineData> msgs = messagesByTab.getOrDefault(selectedGroup, Collections.emptyList());
        guiGraphics.enableScissor(contentX + 4, msgAreaTop, contentW - 10, msgAreaBottom - msgAreaTop);

        int startIdx = Math.max(0, msgs.size() - maxVisible - scrollOffset);
        int endIdx = Math.min(msgs.size(), startIdx + maxVisible);

        int y = msgAreaTop + 4;
        for (int i = startIdx; i < endIdx; i++) {
            ChatLineData line = msgs.get(i);
            int color = line.color != null ? line.color.getColor() : 0xFFFFFF;
            drawChatLine(guiGraphics, line, color, contentX + 10, y);
            y += lineHeight;
        }
        guiGraphics.disableScissor();

        if (msgs.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, "Нет сообщений", this.width / 2, (msgAreaTop + msgAreaBottom) / 2, 0xFF8EA3B6);
        }
    }

    private void renderGroupsTab(GuiGraphics guiGraphics, int mouseX, int mouseY, int startY) {
        int leftColumnX = 12;
        int rightColumnX = this.width / 2 + 8;
        int panelY = startY + 4;
        int panelH = this.height - 96;

        drawPanel(guiGraphics, leftColumnX, panelY, this.width / 2 - 18, panelH, 0xFF141A20, 0xFF212A32, 0xFF2D3941);
        drawPanel(guiGraphics, rightColumnX, panelY, this.width - rightColumnX - 12, panelH, 0xFF141A20, 0xFF212A32, 0xFF2D3941);

        int y = panelY + 8;
        guiGraphics.drawString(this.font, "Мои группы", leftColumnX + 8, y, 0xFF73F29B, true);
        y += 14;
        myGroupsYStart = y;

        if (myGroups.isEmpty()) {
            guiGraphics.drawString(this.font, "Нет групп", leftColumnX + 12, y, 0xFF7B8592, true);
            y += 14;
        } else {
            for (String g : myGroups) {
                boolean hover = mouseX >= leftColumnX + 8 && mouseX <= this.width / 2 - 18 && mouseY >= y && mouseY <= y + myGroupsItemHeight;
                int color = hover ? 0xFFFFFFFF : 0xFFAFD1FF;
                guiGraphics.drawString(this.font, "▶ " + g, leftColumnX + 12, y, color, true);
                y += myGroupsItemHeight;
            }
        }

        y = panelY + 8;
        guiGraphics.drawString(this.font, "Все группы", rightColumnX + 8, y, 0xFFE7D87A, true);
        y += 14;

        if (allGroups.isEmpty()) {
            guiGraphics.drawString(this.font, "Нет групп", rightColumnX + 12, y, 0xFF7B8592, true);
            y += 14;
        } else {
            for (String g : allGroups) {
                boolean joined = myGroups.contains(g);
                int color = joined ? 0xFF73F29B : 0xFFCFD7E5;
                String suffix = joined ? " [в группе]" : "";
                guiGraphics.drawString(this.font, g + suffix, rightColumnX + 12, y, color, true);
                y += 12;
            }
        }

        int formY = this.height - 62;
        guiGraphics.drawString(this.font, "Создать группу", 12, formY, 0xFF73F29B, true);
        groupNameBox.setX(12);
        groupNameBox.setY(formY + 12);
        groupNameBox.setWidth(150);
        groupNameBox.render(guiGraphics, mouseX, mouseY, 0);

        groupPassBox.setX(170);
        groupPassBox.setY(formY + 12);
        groupPassBox.setWidth(150);
        groupPassBox.render(guiGraphics, mouseX, mouseY, 0);

        this.btnCreateX = 330;
        this.btnCreateY = formY + 12;
        boolean hoverCreate = mouseX >= btnCreateX && mouseX <= btnCreateX + 70 && mouseY >= btnCreateY && mouseY <= btnCreateY + 16;
        guiGraphics.fill(btnCreateX, btnCreateY, btnCreateX + 70, btnCreateY + 16, hoverCreate ? 0xFF3E9A5B : 0xFF2E7A48);
        guiGraphics.drawString(this.font, "Создать", btnCreateX + 12, btnCreateY + 3, 0xFFFFFFFF, true);

        this.btnJoinX = 12;
        this.btnJoinY = formY + 38;
        boolean hoverJoin = mouseX >= btnJoinX && mouseX <= btnJoinX + 120 && mouseY >= btnJoinY && mouseY <= btnJoinY + 16;
        guiGraphics.fill(btnJoinX, btnJoinY, btnJoinX + 120, btnJoinY + 16, hoverJoin ? 0xFFB97A2A : 0xFF8C5C1A);
        guiGraphics.drawString(this.font, "Присоединиться", btnJoinX + 10, btnJoinY + 3, 0xFFFFFFFF, true);

        if (groupFeedback != null) {
            int fbY = this.height - 20;
            int fbColor = groupFeedback.startsWith("!") ? 0xFFFF6666 : 0xFF77F28C;
            guiGraphics.drawString(this.font, groupFeedback, 12, fbY, fbColor, true);
        }
    }

    private void drawChatLine(GuiGraphics guiGraphics, ChatLineData line, int color, int x, int y) {
        int cursorX = x;
        cursorX += guiGraphics.drawString(this.font, "<" + line.sender + "> ", cursorX, y, color, true);
        if (line.clickCommand != null) {
            String remainder = stripPhotoMarker(line.message);
            String chipText = "[Фото]";
            int tw = this.font.width(chipText);
            int chipH = 12;
            int chipX = cursorX;
            int chipY = y - 1;
            guiGraphics.fill(chipX, chipY, chipX + tw + 8, chipY + chipH, 0xFF1E2E4E);
            guiGraphics.fill(chipX, chipY, chipX + tw + 8, chipY + 1, 0xFF4A6FA0);
            guiGraphics.fill(chipX, chipY + chipH - 1, chipX + tw + 8, chipY + chipH, 0xFF3A587F);
            guiGraphics.fill(chipX, chipY, chipX + 1, chipY + chipH, 0xFF4A6FA0);
            guiGraphics.fill(chipX + tw + 7, chipY, chipX + tw + 8, chipY + chipH, 0xFF4A6FA0);
            guiGraphics.drawString(this.font, chipText, chipX + 4, y, 0xFFE7D87A, true);
            clickAreas.add(new ClickArea(chipX, chipY, tw + 8, chipH, line.clickCommand));
            cursorX = chipX + tw + 8 + 4;
            if (!remainder.isEmpty()) {
                guiGraphics.drawString(this.font, remainder, cursorX, y, color, true);
            }
            return;
        }
        guiGraphics.drawString(this.font, line.message, cursorX, y, color, true);
    }

    private String stripPhotoMarker(String message) {
        String m = message;
        if (m.startsWith("[Фото HD]")) {
            m = m.substring("[Фото HD]".length());
        } else if (m.startsWith("[Фото]")) {
            m = m.substring("[Фото]".length());
        }
        return m.trim();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (ClickArea area : clickAreas) {
                if (mouseX >= area.x && mouseX <= area.x + area.w && mouseY >= area.y && mouseY <= area.y + area.h) {
                    inputBox.setValue(area.command);
                    inputBox.setFocused(true);
                    return true;
                }
            }
        }
        if (commandSuggestions != null && commandSuggestions.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            int tabHeight = 20;
            int tabWidth = Math.max(80, this.width / Math.max(1, tabs.size()));
            int tabY = 2;
            if (mouseY >= tabY && mouseY <= tabY + tabHeight) {
                int clickedTab = (int) (mouseX / tabWidth);
                if (clickedTab >= 0 && clickedTab < tabs.size() && clickedTab != activeTabIndex) {
                    activeTabIndex = clickedTab;
                    scrollOffset = 0;
                    selectedGroup = null;
                    syncChannelToServer();
                    return true;
                }
            }

            ChatTab active = tabs.get(activeTabIndex);
            if (active.channel == null) {
                if (selectedGroup != null) {
                    int leaveBtnSize = 16;
                    if (mouseX >= groupLeaveBtnX && mouseX <= groupLeaveBtnX + leaveBtnSize
                            && mouseY >= groupLeaveBtnY && mouseY <= groupLeaveBtnY + leaveBtnSize) {
                        if (Minecraft.getInstance().player.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
                            Minecraft.getInstance().player.connection.send(
                                    new GroupActionPayload("leave", selectedGroup, null));
                        }
                        groupFeedback = "Вышли из " + selectedGroup;
                        feedbackTimer = 100;
                        selectedGroup = null;
                        scrollOffset = 0;
                        syncChannelToServer();
                        if (Minecraft.getInstance().player.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
                            Minecraft.getInstance().player.connection.send(
                                    new GroupActionPayload("list", null, null));
                        }
                        return true;
                    }
                    return super.mouseClicked(mouseX, mouseY, button);
                }

                int y = myGroupsYStart;
                for (String g : myGroups) {
                    if (mouseX >= 6 && mouseX <= this.width - 6 && mouseY >= y && mouseY <= y + myGroupsItemHeight) {
                        selectedGroup = g;
                        scrollOffset = 0;
                        syncChannelToServer();
                        inputBox.setValue("");
                        inputBox.setFocused(true);
                        return true;
                    }
                    y += myGroupsItemHeight;
                }

                if (mouseX >= btnCreateX && mouseX <= btnCreateX + 60 && mouseY >= btnCreateY && mouseY <= btnCreateY + 16) {
                    submitCreateGroup();
                    return true;
                }
                if (mouseX >= btnJoinX && mouseX <= btnJoinX + 120 && mouseY >= btnJoinY && mouseY <= btnJoinY + 16) {
                    submitJoinGroup();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void submitCreateGroup() {
        String name = groupNameBox != null ? groupNameBox.getValue().trim() : "";
        String pass = groupPassBox != null ? groupPassBox.getValue().trim() : "";
        if (name.isEmpty() || pass.isEmpty()) {
            groupFeedback = "! Введите имя и пароль";
            feedbackTimer = 100;
            return;
        }
        if (Minecraft.getInstance().player.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
            Minecraft.getInstance().player.connection.send(
                    new GroupActionPayload("create", name, pass));
        }
        groupFeedback = "Группа " + name + " создана!";
        feedbackTimer = 100;
        if (groupNameBox != null) groupNameBox.setValue("");
        if (groupPassBox != null) groupPassBox.setValue("");
        if (Minecraft.getInstance().player.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
            Minecraft.getInstance().player.connection.send(
                    new GroupActionPayload("list", null, null));
        }
    }

    private void submitJoinGroup() {
        String name = groupNameBox != null ? groupNameBox.getValue().trim() : "";
        String pass = groupPassBox != null ? groupPassBox.getValue().trim() : "";
        if (name.isEmpty() || pass.isEmpty()) {
            groupFeedback = "! Введите имя и пароль";
            feedbackTimer = 100;
            return;
        }
        if (Minecraft.getInstance().player.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
            Minecraft.getInstance().player.connection.send(
                    new GroupActionPayload("join", name, pass));
        }
        groupFeedback = "Вступаем в " + name + "...";
        feedbackTimer = 100;
        if (groupNameBox != null) groupNameBox.setValue("");
        if (groupPassBox != null) groupPassBox.setValue("");
        if (Minecraft.getInstance().player.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
            Minecraft.getInstance().player.connection.send(
                    new GroupActionPayload("list", null, null));
        }
    }

    private void submitGroupAction() {
        if (groupNameBox != null && groupNameBox.isFocused()) {
            groupNameBox.setFocused(false);
            if (groupPassBox != null) groupPassBox.setFocused(true);
            return;
        }
        if (groupPassBox != null && groupPassBox.isFocused()) {
            submitCreateGroup();
            return;
        }
    }

    private int getMaxVisibleLines() {
        int msgAreaTop = 28;
        int msgAreaBottom = this.height - 30;
        return (msgAreaBottom - msgAreaTop) / 12;
    }

    private void sendMessage() {
        if (inputBox == null) return;
        String text = inputBox.getValue().trim();
        if (text.isEmpty()) return;

        if (text.startsWith("/")) {
            if (Minecraft.getInstance().player.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
                Minecraft.getInstance().player.connection.sendCommand(text.substring(1));
            }
            inputBox.setValue("");
            return;
        }

        ChatTab active = tabs.get(activeTabIndex);

        if (active.channel == ChatChannel.GLOBAL) {
            if (Minecraft.getInstance().player.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
                Minecraft.getInstance().player.connection.send(
                        new ChatSendPayload(text, active.channel.id, null));
            }
        } else if (active.channel == ChatChannel.LOCAL) {
            if (Minecraft.getInstance().player.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
                Minecraft.getInstance().player.connection.send(
                        new ChatSendPayload(text, active.channel.id, null));
            }
        } else if (active.channel == null && selectedGroup != null) {
            if (Minecraft.getInstance().player.connection.hasChannel(ResourceLocation.fromNamespaceAndPath(MODID, "sync_groups"))) {
                Minecraft.getInstance().player.connection.send(
                        new ChatSendPayload(text, (byte) 2, selectedGroup));
            }
        } else {
            return;
        }
//        Minecraft.getInstance().player.connection.send(
//                new ChatSendPayload(text, (byte) 1, selectedGroup));

        inputBox.setValue("");
        inputBox.setFocused(true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawPanel(GuiGraphics guiGraphics, int x, int y, int w, int h, int fillColor, int borderColor, int accentColor) {
        if (w <= 0 || h <= 0) {
            return;
        }
        guiGraphics.fill(x, y, x + w, y + h, fillColor);
        guiGraphics.fill(x, y, x + w, y + 1, accentColor);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        guiGraphics.fill(x, y, x + 1, y + h, borderColor);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, borderColor);
    }

    public static class ChatTab {
        public final String name;
        public final ChatChannel channel;
        public final String groupName;

        public ChatTab(String name, ChatChannel channel, String groupName) {
            this.name = name;
            this.channel = channel;
            this.groupName = groupName;
        }
    }

    public static class ChatLineData {
        public final String sender;
        public final String message;
        public final ChatFormatting color;
        public final String clickCommand;

        public ChatLineData(String sender, String message, ChatFormatting color) {
            this(sender, message, color, null);
        }

        public ChatLineData(String sender, String message, ChatFormatting color, String clickCommand) {
            this.sender = sender;
            this.message = message;
            this.color = color;
            this.clickCommand = clickCommand;
        }
    }

    private record ClickArea(int x, int y, int w, int h, String command) {
    }
}
