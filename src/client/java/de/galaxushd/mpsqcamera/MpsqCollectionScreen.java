package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Main collection hub for the player's owned accessories and pets. */
public final class MpsqCollectionScreen extends Screen {
    private static final int ART_WIDTH = 352;
    private static final int ART_HEIGHT = 264;
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
            MpsqCameraClient.MOD_ID, "textures/gui/mpsq_collection.png");

    private final Screen parent;
    private float drawScale;
    private int drawLeft;
    private int drawTop;

    public MpsqCollectionScreen(Screen parent) {
        super(Component.literal("Sammlung"));
        this.parent = parent;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        context.fillGradient(0, 0, width, height, 0xD91A1A1A, 0xE6050505);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        drawScale = Math.min(1.0f, Math.min((width - 16.0f) / ART_WIDTH, (height - 16.0f) / ART_HEIGHT));
        drawScale = Math.max(0.1f, drawScale);
        int drawWidth = Math.round(ART_WIDTH * drawScale);
        int drawHeight = Math.round(ART_HEIGHT * drawScale);
        drawLeft = (width - drawWidth) / 2;
        drawTop = (height - drawHeight) / 2;

        context.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
                drawLeft, drawTop, 0, 0, drawWidth, drawHeight,
                ART_WIDTH, ART_HEIGHT, ART_WIDTH, 442);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();

        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT || drawScale <= 0) return super.mouseClicked(event, doubled);

        double guiX = (mouseX - drawLeft) / drawScale;
        double guiY = (mouseY - drawTop) / drawScale;

        // The blue and green 3x3 card faces each act as one single button.
        // There are intentionally no individual slot widgets or hover glows.
        if (inside(guiX, guiY, 52, 107, 105, 105)) {
            minecraft.gui.setScreen(new MpsqOwnedAccessoriesScreen(this));
            return true;
        }
        if (inside(guiX, guiY, 196, 107, 105, 105)) {
            minecraft.gui.setScreen(new MpsqPetMenuScreen(this));
            return true;
        }
        if (inside(guiX, guiY, 17, 218, 31, 32)) {
            onClose();
            return true;
        }
        if (inside(guiX, guiY, 306, 213, 34, 38)) {
            minecraft.gui.setScreen(new MpsqCollectionTutorialScreen(this));
            return true;
        }

        // All unlisted inventory-looking regions are decorative and inert.
        return true;
    }

    private static boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
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
