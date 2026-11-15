package de.galaxushd.mpsqcamera;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/** Screen creation, including selecting the initial camera before a camera screen is saved. */
public final class BildschirmErstellenScreen extends Screen {
    private final BlockPos pos1, pos2;
    private final Direction clickedSide;
    private final int breite, hoehe, tiefe;
    private EditBox nameField, urlField;
    private CycleButton<LocalScreenStore.ScreenInputType> modeButton;
    private Button cameraButton, createButton;
    private UUID selectedCameraId;
    private String selectedCameraName = "Keine Kamera gewählt";
    private String status = "";
    private String nameDraft = "";
    private String urlDraft = "";
    /* Persists the selected mode while returning from CameraPickerScreen. */
    private LocalScreenStore.ScreenInputType selectedMode = LocalScreenStore.ScreenInputType.LINK;

    public BildschirmErstellenScreen(BlockPos pos1, BlockPos pos2, Direction clickedSide) {
        super(Component.translatable("gui.mpsqcamera.erstellen.titel"));
        this.pos1 = pos1; this.pos2 = pos2; this.clickedSide = clickedSide == null ? Direction.NORTH : clickedSide;
        breite = Math.abs(pos2.getX() - pos1.getX()) + 1; hoehe = Math.abs(pos2.getY() - pos1.getY()) + 1; tiefe = Math.abs(pos2.getZ() - pos1.getZ()) + 1;
    }

    @Override protected void init() {
        int cx = width / 2, fieldWidth = 220, y = height / 2 - 72;
        nameField = new EditBox(font, cx - fieldWidth / 2, y, fieldWidth, 20, Component.translatable("gui.mpsqcamera.erstellen.name"));
        nameField.setHint(Component.translatable("gui.mpsqcamera.erstellen.name.hinweis")); nameField.setMaxLength(64); nameField.setValue(nameDraft); nameField.setResponder(value -> { nameDraft = value; updateCreateButton(); }); addRenderableWidget(nameField); y += 28;
        modeButton = addRenderableWidget(CycleButton.builder(LocalScreenStore.ScreenInputType::text, selectedMode).withValues(LocalScreenStore.ScreenInputType.values())
                .create(cx - fieldWidth / 2, y, fieldWidth, 20, Component.translatable("gui.mpsqcamera.erstellen.modus"), (b, mode) -> {
                    selectedMode = mode;
                    updateMode(mode);
                })); y += 28;
        urlField = new EditBox(font, cx - fieldWidth / 2, y, fieldWidth, 20, Component.translatable("gui.mpsqcamera.erstellen.url")); urlField.setHint(Component.literal("https://...")); urlField.setMaxLength(2048); urlField.setValue(urlDraft); urlField.setResponder(value -> urlDraft = value); addRenderableWidget(urlField); y += 28;
        cameraButton = addRenderableWidget(Button.builder(Component.literal("Kamera: " + selectedCameraName), b -> openCameraPicker()).bounds(cx - fieldWidth / 2, y, fieldWidth, 20).build()); y += 32;
        createButton = addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.erstellen.erstellen"), b -> createScreen()).bounds(cx - fieldWidth / 2, y, 106, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.erstellen.abbrechen"), b -> onClose()).bounds(cx + 4, y, 106, 20).build());
        updateMode(selectedMode); updateCreateButton();
    }

    private void updateMode(LocalScreenStore.ScreenInputType mode) {
        boolean cinema = mode == LocalScreenStore.ScreenInputType.LINK;
        boolean camera = mode == LocalScreenStore.ScreenInputType.CAMERA;
        urlField.visible = cinema; urlField.active = cinema; urlField.setEditable(cinema); cameraButton.visible = camera; cameraButton.active = camera; updateCreateButton();
    }
    private void openCameraPicker() {
		nameDraft = nameField.getValue();
		urlDraft = urlField.getValue();
        minecraft.gui.setScreen(new CameraPickerScreen(this, camera -> {
            selectedCameraId = camera.id();
            selectedCameraName = camera.name();
            selectedMode = LocalScreenStore.ScreenInputType.CAMERA;
            status = "";
        }));
    }
    private void updateCreateButton() {
        if (createButton == null || nameField == null || modeButton == null) return;
        boolean validName = !nameField.getValue().trim().isBlank() && !screenNameExists(nameField.getValue());
        createButton.active = validName && (modeButton.getValue() != LocalScreenStore.ScreenInputType.CAMERA || selectedCameraId != null);
    }
    private void createScreen() {
        if (minecraft == null || minecraft.player == null || minecraft.level == null || !createButton.active) return;
        String name = nameField.getValue().trim(); LocalScreenStore.ScreenInputType mode = modeButton.getValue(); String url = (mode == LocalScreenStore.ScreenInputType.LINK) ? urlField.getValue().trim() : "";
        try{TeamVisibilitySettings.requireScreenPlacement(mode,pos1,pos2);}catch(IllegalArgumentException e){status=e.getMessage();return;}
        String apiMode = mode == LocalScreenStore.ScreenInputType.CAMERA ? "CAMERA" : mode == LocalScreenStore.ScreenInputType.MPSQ_REDSTONE ? "REDSTONE" : "KINO";
        JsonObject body = new JsonObject(); body.addProperty("name", name); body.addProperty("mode", apiMode); body.addProperty("dimension", minecraft.level.dimension().identifier().toString()); body.add("pos1", position(pos1)); body.add("pos2", position(pos2)); body.addProperty("front", clickedSide.getSerializedName().toUpperCase()); body.addProperty("cinemaUrl", url);
        status = "Bildschirm wird gespeichert ..."; createButton.active = false;
        MpsqApiClient.post("/screens", body).thenCompose(created -> {
            if (mode != LocalScreenStore.ScreenInputType.CAMERA) return ScreenSyncManager.refresh();
            UUID screenId = createdScreenId(created);
            if (screenId == null) return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Server lieferte keine Bildschirm-ID"));
            JsonObject assignment = new JsonObject(); assignment.addProperty("cameraId", selectedCameraId.toString()); assignment.addProperty("sortOrder", 0);
            return MpsqApiClient.post("/screens/" + screenId + "/cameras", assignment).thenCompose(ignored -> ScreenSyncManager.refresh());
        }).whenComplete((ignored, error) -> minecraft.execute(() -> {
            if (error == null) onClose(); else { Throwable cause = error.getCause() == null ? error : error.getCause(); status = "Nicht gespeichert: " + (cause.getMessage() == null ? "Serverfehler" : cause.getMessage()); updateCreateButton(); }
        }));
    }
    private static UUID createdScreenId(JsonElement result) {
        JsonObject row = result != null && result.isJsonArray() && result.getAsJsonArray().size() > 0 ? result.getAsJsonArray().get(0).getAsJsonObject() : result != null && result.isJsonObject() ? result.getAsJsonObject() : null;
        try { return row != null && row.has("id") ? UUID.fromString(row.get("id").getAsString()) : null; } catch (IllegalArgumentException ignored) { return null; }
    }
    private static boolean screenNameExists(String name) { return LocalScreenStore.getAllScreens().stream().anyMatch(s -> s.name() != null && s.name().trim().equalsIgnoreCase(name.trim())); }
    private static JsonObject position(BlockPos pos) { JsonObject r = new JsonObject(); r.addProperty("x", pos.getX()); r.addProperty("y", pos.getY()); r.addProperty("z", pos.getZ()); return r; }
    @Override public void extractBackground(GuiGraphicsExtractor c, int x, int y, float d) { super.extractBackground(c, x, y, d); MpsqTheme.drawBackground(c, width, height); MpsqTheme.drawPanel(c, (width - 260) / 2, height / 2 - 100, 260, 290); }
    @Override public void extractRenderState(GuiGraphicsExtractor c, int x, int y, float d) { super.extractRenderState(c, x, y, d); int cx = width / 2, titleY = height / 2 - 96; c.centeredText(font, title, cx, titleY, MpsqTheme.TEXT_TITEL); c.centeredText(font, Component.literal(breite + " × " + hoehe + " × " + tiefe + " Blöcke"), cx, titleY + 12, MpsqTheme.TEXT_GEDAEMPT); if (!status.isBlank()) c.centeredText(font, Component.literal(status), cx, height / 2 + 76, 0xFF5555); }
    @Override public boolean isPauseScreen() { return false; }
}
