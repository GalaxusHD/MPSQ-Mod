package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Manages all cameras belonging to one screen. A click adds an unassigned
 * camera; clicking an already assigned camera removes only that assignment.
 */
public final class CameraAssignmentScreen extends Screen {
    private static final int TOP = 58;
    private static final int BOTTOM = 62;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 22;

    private final Screen parent;
    private final UUID screenId;
    private int scroll;
    private String status = "";
    private boolean saving;

    public CameraAssignmentScreen(Screen parent, UUID screenId) {
        super(Component.literal("Kameras verwalten"));
        this.parent = parent;
        this.screenId = screenId;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.mpsqcamera.back"),
                        button -> minecraft.gui.setScreen(parent))
                .bounds(width / 2 - 75, height - 36, 150, 20)
                .build());
        clampScroll();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        MpsqTheme.drawBackground(context, width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        int centerX = width / 2;
        int rowY = TOP - scroll;
        List<UUID> assigned = ScreenCameraStore.cameras(screenId);

        context.centeredText(font, title, centerX, 28, MpsqTheme.TEXT_TITEL);
        context.fill(centerX - 140, 44, centerX + 140, 45, 0x44FFFFFF);
        context.centeredText(
                font,
                Component.literal("Ausgewählt: " + assigned.size()),
                centerX,
                47,
                MpsqTheme.TEXT_GEDAEMPT
        );

        if (!status.isBlank()) {
            context.centeredText(font, Component.literal(status), centerX, height - 52, 0xFFFF5555);
        }

        List<LocalCameraStore.CameraData> cameras = LocalCameraStore.getAll();
        if (cameras.isEmpty()) {
            context.centeredText(font, Component.literal("Keine Kameras vorhanden."), centerX, 74, MpsqTheme.TEXT_GEDAEMPT);
            return;
        }

        for (LocalCameraStore.CameraData camera : cameras) {
            if (rowY + ROW_HEIGHT > TOP && rowY < height - BOTTOM) {
                boolean isAssigned = assigned.contains(camera.id());
                context.fill(centerX - 140, rowY, centerX + 140, rowY + ROW_HEIGHT,
                        isAssigned ? 0x77555555 : 0x55000000);
                String marker = isAssigned ? "✓ " : "+ ";
                String label = marker + camera.name();
                context.centeredText(font, Component.literal(label), centerX, rowY + 6,
                        isAssigned ? MpsqTheme.TEXT_NORMAL : MpsqTheme.TEXT_GEDAEMPT);
            }
            rowY += ROW_GAP;
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();

        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT || saving) return super.mouseClicked(event, doubled);

        int rowY = TOP - scroll;
        for (LocalCameraStore.CameraData camera : LocalCameraStore.getAll()) {
            if (mouseY >= rowY && mouseY < rowY + ROW_HEIGHT && rowY >= TOP && rowY < height - BOTTOM) {
                if (ScreenCameraStore.cameras(screenId).contains(camera.id())) {
                    remove(camera);
                } else {
                    add(camera);
                }
                return true;
            }
            rowY += ROW_GAP;
        }
        return super.mouseClicked(event, doubled);
    }

    private void add(LocalCameraStore.CameraData camera) {
        JsonObject body = new JsonObject();
        body.addProperty("cameraId", camera.id().toString());
        body.addProperty("sortOrder", ScreenCameraStore.cameras(screenId).size());
        save(MpsqApiClient.post("/screens/" + screenId + "/cameras", body), "Kamera hinzugefügt.");
    }

    private void remove(LocalCameraStore.CameraData camera) {
        save(MpsqApiClient.delete("/screens/" + screenId + "/cameras/" + camera.id()), "Kamera entfernt.");
    }

    private void save(java.util.concurrent.CompletableFuture<?> request, String success) {
        saving = true;
        status = "Speichere ...";
        request.thenCompose(ignored -> ScreenSyncManager.refresh()).whenComplete((ignored, error) -> minecraft.execute(() -> {
            saving = false;
            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                status = "Nicht gespeichert: " + (cause.getMessage() == null ? "Serverfehler" : cause.getMessage());
            } else {
                status = success;
            }
            rebuildWidgets();
        }));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll -= (int) Math.signum(verticalAmount) * ROW_GAP * 2;
        clampScroll();
        return true;
    }

    private void clampScroll() {
        int availableHeight = height - TOP - BOTTOM;
        int contentHeight = LocalCameraStore.getAll().size() * ROW_GAP;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentHeight - availableHeight)));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
