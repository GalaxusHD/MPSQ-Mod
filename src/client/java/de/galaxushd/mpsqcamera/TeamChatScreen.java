package de.galaxushd.mpsqcamera;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Private MPSQ Team chat. /mpsq <message> sends to this same chat without opening the screen. */
public final class TeamChatScreen extends Screen {
    private final Screen parent;
    private EditBox input;
    private List<TeamChatMessage> messages = List.of();

    public TeamChatScreen(Screen parent) { super(Component.translatable("gui.mpsqcamera.team.chat")); this.parent = parent; }

    @Override protected void init() {
        input = new EditBox(font, width / 2 - 145, height - 62, 220, 20, Component.translatable("gui.mpsqcamera.team.chat.placeholder"));
        input.setMaxLength(256);
        input.setHint(Component.translatable("gui.mpsqcamera.team.chat.placeholder"));
        addRenderableWidget(input);
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.team.chat.send"), b -> send())
                .bounds(width / 2 + 81, height - 62, 64, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.back"), b -> minecraft.gui.setScreen(parent))
                .bounds(width / 2 - 75, height - 36, 150, 20).build());
        setInitialFocus(input);
        reload();
    }

    private void reload() { MpsqApiClient.loadTeamMessages().whenComplete((rows, error) -> { if (error == null) minecraft.execute(() -> messages = rows); }); }
    private void send() {
        String message = TeamChatPolicy.prepare(input.getValue());
        if (message.isEmpty()) return;
        if (TeamChatPolicy.containsForbiddenContent(message)) {
            if (minecraft.player != null) de.galaxushd.mpsqcamera.compat.PlayerMessages.send(minecraft.player, Component.translatable("gui.mpsqcamera.team.command.filtered"), true);
            return;
        }
        MpsqApiClient.sendTeamMessage(message).whenComplete((v, error) -> minecraft.execute(() -> {
            if (error == null) { input.setValue(""); reload(); }
            else if (minecraft.player != null) de.galaxushd.mpsqcamera.compat.PlayerMessages.send(minecraft.player, Component.translatable("gui.mpsqcamera.team.command.failed"), true);
        }));
    }

    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
 int keyCode=event.key(), scanCode=event.keycode(), modifiers=event.modifiers();

        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) { send(); return true; }
        return super.keyPressed(event);
    }
    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) { super.extractBackground(context, mouseX, mouseY, delta); MpsqTheme.drawBackground(context, width, height); }
    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, 24, MpsqTheme.TEXT_TITEL);
        int y = 48;
        int start = Math.max(0, messages.size() - Math.max(1, (height - 122) / 18));
        for (int i = start; i < messages.size(); i++) {
            TeamChatMessage message = messages.get(i);
            message.senderRank().draw(context, width / 2 - 145, y + 1, 12);
            int x = width / 2 - 139 + message.senderRank().widthForHeight(12);
            context.text(font,
                    Component.literal(message.senderName() + ": ").append(TeamChatText.fromAmpersandCodes(message.message(), net.minecraft.ChatFormatting.WHITE)),
                    x, y + 4, MpsqTheme.TEXT_NORMAL);
            y += 18;
        }
    }
    @Override public boolean isPauseScreen() { return false; }
}
