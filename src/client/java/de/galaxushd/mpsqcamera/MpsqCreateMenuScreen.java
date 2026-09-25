package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;

/** One entry point for creating the three currently supported MPSQ objects. */
public final class MpsqCreateMenuScreen extends Screen {
    public MpsqCreateMenuScreen() { super(Text.literal("MPSQ erstellen")); }

    @Override protected void init() {
        int x = width / 2 - 105, y = height / 2 - 48;
        addDrawableChild(ButtonWidget.builder(Text.literal("Kamera"), b -> client.setScreen(new CameraCreateScreen()))
                .dimensions(x, y, 210, 24).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Möbel"), b -> {
            if (client.crosshairTarget instanceof BlockHitResult hit) client.setScreen(new MpsqObjectScreen(hit.getBlockPos()));
            else if (client.player != null) client.player.sendMessage(Text.literal("Schau den Block an, auf dem das Möbel stehen soll."), true);
        }).dimensions(x, y + 31, 210, 24).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("NPC"), b -> {
            if (client.player != null) client.setScreen(MpsqNpcPlacementScreen.atPlayer(this, client.player));
        }).dimensions(x, y + 62, 210, 24).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), b -> close())
                .dimensions(x, y + 98, 210, 20).build());
    }

    @Override public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        super.renderBackground(c, mouseX, mouseY, delta);
        MpsqTheme.drawBackground(c, width, height);
    }

    @Override public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        super.render(c, mouseX, mouseY, delta);
        c.drawCenteredTextWithShadow(textRenderer, title, width / 2, height / 2 - 82, MpsqTheme.TEXT_TITEL);
        c.drawCenteredTextWithShadow(textRenderer, "C · Kamera, Möbel oder NPC erstellen", width / 2, height / 2 - 66, 0xFFBBBBBB);
    }

    @Override public boolean shouldPause() { return false; }
}
