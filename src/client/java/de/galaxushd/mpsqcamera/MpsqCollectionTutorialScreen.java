package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Temporary tutorial destination until collection help content is ready. */
public final class MpsqCollectionTutorialScreen extends Screen {
    private final Screen parent;
    private final String topic;

    public MpsqCollectionTutorialScreen(Screen parent) {
        this(parent, "Accessoires & Pets");
    }

    public MpsqCollectionTutorialScreen(Screen parent, String topic) {
        super(Component.literal(topic + " · Tutorial"));
        this.parent = parent;
        this.topic = topic;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        context.fillGradient(0, 0, width, height, 0xD91A1A1A, 0xE6050505);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int centerX = width / 2;
        int panelWidth = Math.min(300, width - 24);
        int panelHeight = 92;
        int left = centerX - panelWidth / 2;
        int top = height / 2 - panelHeight / 2;
        context.fill(left + 3, top + 4, left + panelWidth + 3, top + panelHeight + 4, 0x99000000);
        context.fill(left, top, left + panelWidth, top + panelHeight, 0xEE101014);
        context.fill(left, top, left + panelWidth, top + 3, 0xFFFF1764);
        context.centeredText(font, "Tutorial · " + topic,
                centerX, top + 24, 0xFFF4F0F2);
        context.centeredText(font, "Kommt bald",
                centerX, top + 48, 0xFFFF4F83);
        context.centeredText(font, "Klicken oder Esc: zurück",
                centerX, top + 70, 0xFFB9B7BA);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();

        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
