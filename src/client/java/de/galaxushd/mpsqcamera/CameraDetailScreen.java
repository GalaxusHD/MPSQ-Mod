package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Owner management menu for one static camera. */
public final class CameraDetailScreen extends Screen {
    private static final int WIDTH = 220;
    private static final int ROW_GAP = 26;

    private final Screen parent;
    private final UUID cameraId;
    private EditBox nameField;
    private Button saveButton;
    private String status = "";

    public CameraDetailScreen(Screen parent, UUID cameraId) {
        super(Component.literal("Kamera verwalten"));
        this.parent = parent;
        this.cameraId = cameraId;
    }

    @Override
    protected void init() {
        LocalCameraStore.CameraData camera = LocalCameraStore.find(cameraId).orElse(null);
        if (camera == null) {
            minecraft.gui.setScreen(parent);
            return;
        }

        int x = width / 2 - WIDTH / 2;
        int y = 30;
        int rowGap=Math.min(ROW_GAP,Math.max(14,(height-70)/7)),buttonHeight=rowGap-3;
        nameField = new EditBox(font, x, y, WIDTH, buttonHeight, Component.literal("Kameraname"));
        nameField.setMaxLength(64);
        nameField.setValue(camera.name());
        nameField.setResponder(ignored -> updateSaveButton());
        addRenderableWidget(nameField);
        y += rowGap;

        saveButton = addRenderableWidget(Button.builder(Component.literal("Name speichern"), button -> saveName())
                .bounds(x, y, WIDTH, buttonHeight).build());
        y += rowGap;

        if (camera.kind() == LocalCameraStore.CameraKind.STATIC) {
            addRenderableWidget(Button.builder(Component.literal("Position: X / Y / Z"), button -> minecraft.gui.setScreen(new MpsqPositionScreen(this,"Kamera positionieren",camera.position(),position->{
                JsonObject body=new JsonObject();body.addProperty("x",position.x);body.addProperty("y",position.y);body.addProperty("z",position.z);
                return MpsqApiClient.patch("/cameras/"+cameraId,body).thenCompose(ignored->MpsqApiClient.refreshCameras()).thenAccept(ignored->{});
            }))).bounds(x,y,WIDTH,buttonHeight).build());
            y+=rowGap;
            addRenderableWidget(Button.builder(Component.literal("Hier verschieben"), button -> moveHere())
                    .bounds(x, y, WIDTH, buttonHeight).build());
            y += rowGap;
            addRenderableWidget(Button.builder(Component.literal("Drehen −22,5°"),button->rotateStep(-22.5f)).bounds(x,y,108,buttonHeight).build());
            addRenderableWidget(Button.builder(Component.literal("Drehen +22,5°"),button->rotateStep(22.5f)).bounds(x+112,y,108,buttonHeight).build());y+=rowGap;
            addRenderableWidget(Button.builder(Component.literal("In Blickrichtung drehen"), button -> rotateHere())
                    .bounds(x, y, WIDTH, buttonHeight).build());
            y += rowGap;
        }

        addRenderableWidget(Button.builder(Component.literal("Kamera löschen").withStyle(ChatFormatting.RED), button -> confirmDelete())
                .bounds(x, y, WIDTH, buttonHeight).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.back"), button -> minecraft.gui.setScreen(parent))
                .bounds(width / 2 - 75, height - 28, 150, 20).build());
        updateSaveButton();
        setInitialFocus(nameField);
    }

    private void updateSaveButton() {
        if (nameField == null || saveButton == null) return;
        String wanted = nameField.getValue().trim();
        boolean duplicate = LocalCameraStore.getAll().stream()
                .anyMatch(camera -> !camera.id().equals(cameraId) && camera.name().trim().equalsIgnoreCase(wanted));
        saveButton.active = !wanted.isEmpty() && !duplicate;
        status = duplicate ? "Dieser Kameraname wird bereits verwendet." : "";
    }

    private void saveName() {
        JsonObject body = new JsonObject();
        body.addProperty("name", nameField.getValue().trim());
        saveButton.active = false;
        save(body, "Name gespeichert.");
    }

    private void moveHere() {
        if (minecraft.player == null || minecraft.level == null) return;
        Vec3 position = minecraft.player.getEyePosition(1.0F).add(minecraft.player.getViewVector(1.0F).scale(0.65)); try{TeamVisibilitySettings.requirePlacement(position.x,position.y,position.z);}catch(IllegalArgumentException e){status=e.getMessage();return;}
        JsonObject body = new JsonObject();
        body.addProperty("dimension", minecraft.level.dimension().identifier().toString());
        body.addProperty("x", position.x);
        body.addProperty("y", position.y);
        body.addProperty("z", position.z);
        body.addProperty("yaw", minecraft.player.getYRot());
        body.addProperty("pitch", minecraft.player.getXRot());
        save(body, "Kamera verschoben.");
    }

    private void rotateStep(float step){LocalCameraStore.find(cameraId).ifPresent(camera->{JsonObject body=new JsonObject();body.addProperty("yaw",net.minecraft.util.Mth.wrapDegrees(camera.yaw()+step));save(body,"Kamera gedreht.");});}
    private void rotateHere() {
        if (minecraft.player == null) return;
        JsonObject body = new JsonObject();
        body.addProperty("yaw", minecraft.player.getYRot());
        body.addProperty("pitch", minecraft.player.getXRot());
        save(body, "Kamera gedreht.");
    }

    private void save(JsonObject body, String success) {
        MpsqApiClient.patch("/cameras/" + cameraId, body)
                .thenCompose(ignored -> MpsqApiClient.refreshCameras())
                .whenComplete((ignored, error) -> minecraft.execute(() -> {
                    if (error != null) {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        status = "Nicht gespeichert: " + (cause.getMessage() == null ? "Serverfehler" : cause.getMessage());
                        updateSaveButton();
                    } else {
                        status = success;
                        rebuildWidgets();
                    }
                }));
    }

    private void confirmDelete() {
        minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
            if (!confirmed) {
                minecraft.gui.setScreen(this);
                return;
            }
            MpsqApiClient.delete("/cameras/" + cameraId)
                    .thenCompose(ignored -> MpsqApiClient.refreshCameras())
                    .whenComplete((ignored, error) -> minecraft.execute(() -> minecraft.gui.setScreen(parent)));
        }, Component.literal("Kamera löschen"), Component.literal("Kamera wirklich löschen?"),
                Component.literal("Löschen").withStyle(ChatFormatting.RED), Component.literal("Abbrechen")));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int centerX = width / 2;
        context.centeredText(font, title, centerX, 12, MpsqTheme.TEXT_TITEL);
        if (!status.isEmpty()) context.centeredText(font, Component.literal(status), centerX, height - 42, MpsqTheme.TEXT_GEDAEMPT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        MpsqTheme.drawBackground(context, width, height);
    }

    @Override public boolean isPauseScreen() { return false; }
}
