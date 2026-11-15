package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** One entry point for creating the three currently supported MPSQ objects. */
public final class MpsqCreateMenuScreen extends Screen {
    public MpsqCreateMenuScreen() { super(Component.literal("MPSQ erstellen")); }

    @Override protected void init() {
        int x = width / 2 - 105, y = height / 2 - 48;
        addRenderableWidget(Button.builder(Component.literal("Kamera"), b -> minecraft.gui.setScreen(new CameraCreateScreen()))
                .bounds(x, y, 210, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Möbel"), b -> {
            if (minecraft.player != null) minecraft.gui.setScreen(new MpsqObjectScreen(minecraft.player.blockPosition().below()));
        }).bounds(x, y + 31, 210, 24).build());
        addRenderableWidget(Button.builder(Component.literal("NPC"), b -> {
            if (minecraft.player != null) MpsqScenes.openCreate();
        }).bounds(x, y + 62, 210, 24).build());
        addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> onClose())
                .bounds(x, y + 98, 210, 20).build());
    }

    @Override public void extractBackground(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
        super.extractBackground(c, mouseX, mouseY, delta);
        MpsqTheme.drawBackground(c, width, height);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor c, int mouseX, int mouseY, float delta) {
        super.extractRenderState(c, mouseX, mouseY, delta);
        c.centeredText(font, title, width / 2, height / 2 - 82, MpsqTheme.TEXT_TITEL);
        c.centeredText(font, "C · Kamera, Möbel oder NPC erstellen", width / 2, height / 2 - 66, 0xFFBBBBBB);
    }

    @Override public void onClose(){CameraCreationManager.clearAnchor();super.onClose();}

    @Override public boolean isPauseScreen() { return false; }
}
