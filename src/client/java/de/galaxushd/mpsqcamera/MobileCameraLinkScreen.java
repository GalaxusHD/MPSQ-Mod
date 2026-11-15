package de.galaxushd.mpsqcamera;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Chooses which cameras belong to the player's mobile clock screen. */
public final class MobileCameraLinkScreen extends Screen {
    private final List<UUID> selected = new ArrayList<>(MobileCameraLinkStore.linkedCameras());
    private int scroll;

    public MobileCameraLinkScreen() {
        super(Component.translatable("gui.mpsqcamera.mobile.title"));
    }

    @Override
    protected void init() {
        int y = height - 36;
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.mobile.save"), button -> {
            MobileCameraLinkStore.replace(selected);
            onClose();
        }).bounds(width / 2 - 154, y, 150, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.back"), button -> onClose())
                .bounds(width / 2 + 4, y, 150, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        MpsqTheme.drawBackground(context, width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.centeredText(font, title, width / 2, 22, MpsqTheme.TEXT_TITEL);
        context.fill(32, 42, width - 32, 43, 0xFF888888);
        List<LocalCameraStore.CameraData> cameras = LocalCameraStore.getAll();
        if (cameras.isEmpty()) {
            context.centeredText(font,
                    Component.translatable("gui.mpsqcamera.mobile.empty"), width / 2, 72, MpsqTheme.TEXT_GEDAEMPT);
            return;
        }
        int row = 56 - scroll;
        for (LocalCameraStore.CameraData camera : cameras) {
            if (row >= 50 && row < height - 62) {
                context.fill(width / 2 - 150, row, width / 2 + 150, row + 22, 0x66000000);
                int boxX = width / 2 - 138;
                context.text(font, Component.literal("[ ]"), boxX, row + 7,
                        MpsqTheme.TEXT_NORMAL);
                if (selected.contains(camera.id())) {
                    drawGreenCheck(context, boxX + 3, row + 8);
                }
                context.text(font, Component.literal(camera.name()), boxX + 26, row + 7,
                        MpsqTheme.TEXT_NORMAL);
            }
            row += 25;
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();

        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && mouseX >= width / 2.0 - 150 && mouseX < width / 2.0 + 150) {
            int row = 56 - scroll;
            for (LocalCameraStore.CameraData camera : LocalCameraStore.getAll()) {
                if (row >= 50 && row < height - 62 && mouseY >= row && mouseY < row + 22) {
                    if (!selected.remove(camera.id())) selected.add(camera.id());
                    return true;
                }
                row += 25;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int max = Math.max(0, LocalCameraStore.getAll().size() * 25 - Math.max(1, height - 120));
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(verticalAmount) * 50));
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }

    /** Drawn from pixels so the check does not depend on a Unicode font glyph. */
    private static void drawGreenCheck(GuiGraphicsExtractor context, int x, int y) {
        int green = 0xFF55FF55;
        context.fill(x, y + 4, x + 2, y + 7, green);
        context.fill(x + 2, y + 6, x + 4, y + 9, green);
        context.fill(x + 4, y + 3, x + 6, y + 8, green);
        context.fill(x + 6, y + 1, x + 8, y + 5, green);
    }
}
