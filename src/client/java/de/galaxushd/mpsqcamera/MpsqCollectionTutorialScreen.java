package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Temporary tutorial destination until collection help content is ready. */
public final class MpsqCollectionTutorialScreen extends Screen {
    private final Screen parent;

    public MpsqCollectionTutorialScreen(Screen parent) {
        super(Text.literal("Sammlung · Tutorial"));
        this.parent = parent;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        context.fillGradient(0, 0, width, height, 0xD91A1A1A, 0xE6050505);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        int centerX = width / 2;
        int panelWidth = Math.min(300, width - 24);
        int panelHeight = 92;
        int left = centerX - panelWidth / 2;
        int top = height / 2 - panelHeight / 2;
        context.fill(left + 3, top + 4, left + panelWidth + 3, top + panelHeight + 4, 0x99000000);
        context.fill(left, top, left + panelWidth, top + panelHeight, 0xEE101014);
        context.fill(left, top, left + panelWidth, top + 3, 0xFFFF1764);
        context.drawCenteredTextWithShadow(textRenderer, "Tutorial · Accessoires & Pets",
                centerX, top + 24, 0xFFF4F0F2);
        context.drawCenteredTextWithShadow(textRenderer, "Kommt bald",
                centerX, top + 48, 0xFFFF4F83);
        context.drawCenteredTextWithShadow(textRenderer, "Klicken oder Esc: zurück",
                centerX, top + 70, 0xFFB9B7BA);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
