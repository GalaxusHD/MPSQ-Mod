package de.galaxushd.mpsqcamera;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.net.URI;

/** Inventory-style Discord invitation with a raised, clickable button. */
public final class MpsqWumpusDiscordScreen extends Screen {
    private static final Identifier BANNER = Identifier.of("mpsqcamera", "textures/gui/wumpus_discord_banner.png");
    private static final String INVITE_URL = "https://discord.gg/x8xTsxtVS9";
    private static final int IMAGE_WIDTH = 1536;
    private static final int IMAGE_HEIGHT = 1024;
    private final Screen parent;

    public MpsqWumpusDiscordScreen(Screen parent) {
        super(Text.literal("Discord"));
        this.parent = parent;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x99000000);
        Layout layout = layout();
        // Standard Minecraft inventory palette: dark shadow, black edge, light grey frame.
        context.fill(layout.left() + 5, layout.top() + 6, layout.left() + layout.panelWidth() + 5,
                layout.top() + layout.panelHeight() + 6, 0xFF101014);
        context.fill(layout.left() - 2, layout.top() - 2, layout.left() + layout.panelWidth() + 2,
                layout.top() + layout.panelHeight() + 2, 0xFF202020);
        context.fill(layout.left(), layout.top(), layout.left() + layout.panelWidth(),
                layout.top() + layout.panelHeight(), 0xFFC6C6C6);
        context.fill(layout.left() + 3, layout.top() + 3, layout.left() + layout.panelWidth() - 3,
                layout.top() + layout.panelHeight() - 3, 0xFF555555);
        context.fill(layout.left() + 5, layout.top() + 5, layout.left() + layout.panelWidth() - 5,
                layout.top() + layout.panelHeight() - 5, 0xFFC6C6C6);
        context.drawTextWithShadow(textRenderer, "Discord-Einladung", layout.left() + 11, layout.top() + 11, 0xFF404040);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        Layout layout = layout();
        context.drawTexture(RenderPipelines.GUI_TEXTURED, BANNER, layout.imageLeft(), layout.imageTop(), 0, 0,
                layout.imageWidth(), layout.imageHeight(), IMAGE_WIDTH, IMAGE_HEIGHT, IMAGE_WIDTH, IMAGE_HEIGHT);
        drawRaisedButton(context, layout, mouseX, mouseY);
    }

    private void drawRaisedButton(DrawContext context, Layout layout, int mouseX, int mouseY) {
        int x = layout.imageLeft() + Math.round(layout.imageWidth() * 0.30f);
        int y = layout.imageTop() + Math.round(layout.imageHeight() * 0.735f);
        int w = Math.round(layout.imageWidth() * 0.40f);
        int h = Math.max(24, Math.round(layout.imageHeight() * 0.19f));
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int lift = hovered ? 2 : 4;
        // Offset dark extrusion gives the button a visible 3D edge over the flat banner art.
        context.fill(x + 2, y + lift, x + w + 2, y + h + lift, 0xFF090518);
        context.fill(x, y + 3, x + w, y + h + 3, 0xFF21004E);
        context.fill(x + 3, y, x + w - 3, y + h - 2, 0xFF160A36);
        context.fill(x + 5, y + 3, x + w - 5, y + h - 5, hovered ? 0xFF6516D5 : 0xFF5210B8);
        context.fill(x + 7, y + 4, x + w - 7, y + 7, 0xFF9D55FF);
        context.fill(x + 7, y + h - 7, x + w - 7, y + h - 5, 0xFF351078);

        int iconSize = Math.max(11, h / 3);
        int iconX = x + Math.round(w * 0.19f) - iconSize / 2;
        int centerY = y + h / 2;
        context.fill(iconX + 2, centerY - iconSize / 3, iconX + iconSize - 2, centerY + iconSize / 3, 0xFFE0C9FF);
        context.fill(iconX, centerY - iconSize / 6, iconX + 3, centerY + iconSize / 4, 0xFFE0C9FF);
        context.fill(iconX + iconSize - 3, centerY - iconSize / 6, iconX + iconSize, centerY + iconSize / 4, 0xFFE0C9FF);
        context.fill(iconX + iconSize / 3, centerY - 1, iconX + iconSize / 3 + 2, centerY + 1, 0xFF3B1878);
        context.fill(iconX + iconSize * 2 / 3 - 1, centerY - 1, iconX + iconSize * 2 / 3 + 1, centerY + 1, 0xFF3B1878);

        Text label = Text.literal("Joine hier");
        context.drawCenteredTextWithShadow(textRenderer, label, x + Math.round(w * 0.59f), centerY - 4,
                hovered ? 0xFFFFFFFF : 0xFFE2D2FF);
    }

    private Layout layout() {
        float scale = Math.min((width - 32f) / IMAGE_WIDTH, (height - 72f) / IMAGE_HEIGHT);
        scale = Math.max(0.1f, scale);
        int imageWidth = Math.round(IMAGE_WIDTH * scale);
        int imageHeight = Math.round(IMAGE_HEIGHT * scale);
        int panelWidth = imageWidth + 20;
        int panelHeight = imageHeight + 38;
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        return new Layout(left, top, panelWidth, panelHeight, left + 10, top + 26, imageWidth, imageHeight);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        Layout layout = layout();
        int x = layout.imageLeft() + Math.round(layout.imageWidth() * 0.30f);
        int y = layout.imageTop() + Math.round(layout.imageHeight() * 0.735f);
        int w = Math.round(layout.imageWidth() * 0.40f);
        int h = Math.max(24, Math.round(layout.imageHeight() * 0.19f));
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            Util.getOperatingSystem().open(URI.create(INVITE_URL));
        }
        return true;
    }

    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }

    private record Layout(int left, int top, int panelWidth, int panelHeight,
                          int imageLeft, int imageTop, int imageWidth, int imageHeight) { }
}
