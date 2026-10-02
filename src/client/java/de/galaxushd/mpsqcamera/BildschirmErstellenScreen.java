package de.galaxushd.mpsqcamera;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/** Screen creation, including selecting the initial camera before a camera screen is saved. */
public final class BildschirmErstellenScreen extends Screen {
    private final BlockPos pos1, pos2;
    private final Direction clickedSide;
    private final int breite, hoehe, tiefe;
    private TextFieldWidget nameField, urlField;
    private CyclingButtonWidget<LocalScreenStore.ScreenInputType> modeButton;
    private ButtonWidget cameraButton, createButton;
    private UUID selectedCameraId;
    private String selectedCameraName = "Keine Kamera gewählt";
    private String status = "";
    private String nameDraft = "";
    private String urlDraft = "";
    /* Persists the selected mode while returning from CameraPickerScreen. */
    private LocalScreenStore.ScreenInputType selectedMode = LocalScreenStore.ScreenInputType.LINK;

    public BildschirmErstellenScreen(BlockPos pos1, BlockPos pos2, Direction clickedSide) {
        super(Text.translatable("gui.mpsqcamera.erstellen.titel"));
        this.pos1 = pos1; this.pos2 = pos2; this.clickedSide = clickedSide == null ? Direction.NORTH : clickedSide;
        breite = Math.abs(pos2.getX() - pos1.getX()) + 1; hoehe = Math.abs(pos2.getY() - pos1.getY()) + 1; tiefe = Math.abs(pos2.getZ() - pos1.getZ()) + 1;
    }

    @Override protected void init() {
        int cx = width / 2, fieldWidth = 220, y = height / 2 - 72;
        nameField = new TextFieldWidget(textRenderer, cx - fieldWidth / 2, y, fieldWidth, 20, Text.translatable("gui.mpsqcamera.erstellen.name"));
        nameField.setPlaceholder(Text.translatable("gui.mpsqcamera.erstellen.name.hinweis")); nameField.setMaxLength(64); nameField.setText(nameDraft); nameField.setChangedListener(value -> { nameDraft = value; updateCreateButton(); }); addDrawableChild(nameField); y += 28;
        modeButton = addDrawableChild(CyclingButtonWidget.builder(LocalScreenStore.ScreenInputType::text).values(LocalScreenStore.ScreenInputType.values()).initially(selectedMode)
                .build(cx - fieldWidth / 2, y, fieldWidth, 20, Text.translatable("gui.mpsqcamera.erstellen.modus"), (b, mode) -> {
                    selectedMode = mode;
                    updateMode(mode);
                })); y += 28;
        urlField = new TextFieldWidget(textRenderer, cx - fieldWidth / 2, y, fieldWidth, 20, Text.translatable("gui.mpsqcamera.erstellen.url")); urlField.setPlaceholder(Text.literal("https://...")); urlField.setMaxLength(2048); urlField.setText(urlDraft); urlField.setChangedListener(value -> urlDraft = value); addDrawableChild(urlField); y += 28;
        cameraButton = addDrawableChild(ButtonWidget.builder(Text.literal("Kamera: " + selectedCameraName), b -> openCameraPicker()).dimensions(cx - fieldWidth / 2, y, fieldWidth, 20).build()); y += 32;
        createButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.erstellen.erstellen"), b -> createScreen()).dimensions(cx - fieldWidth / 2, y, 106, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.erstellen.abbrechen"), b -> close()).dimensions(cx + 4, y, 106, 20).build());
        updateMode(selectedMode); updateCreateButton();
    }

    private void updateMode(LocalScreenStore.ScreenInputType mode) {
        boolean cinema = mode == LocalScreenStore.ScreenInputType.LINK;
        boolean camera = mode == LocalScreenStore.ScreenInputType.CAMERA;
        urlField.visible = cinema; urlField.setEditable(cinema); cameraButton.visible = camera; cameraButton.active = camera; updateCreateButton();
    }
    private void openCameraPicker() {
		nameDraft = nameField.getText();
		urlDraft = urlField.getText();
        client.setScreen(new CameraPickerScreen(this, camera -> {
            selectedCameraId = camera.id();
            selectedCameraName = camera.name();
            selectedMode = LocalScreenStore.ScreenInputType.CAMERA;
            status = "";
        }));
    }
    private void updateCreateButton() {
        if (createButton == null || nameField == null || modeButton == null) return;
        boolean validName = !nameField.getText().trim().isBlank() && !screenNameExists(nameField.getText());
        createButton.active = validName && (modeButton.getValue() != LocalScreenStore.ScreenInputType.CAMERA || selectedCameraId != null);
    }
    private void createScreen() {
        if (client == null || client.player == null || client.world == null || !createButton.active) return;
        String name = nameField.getText().trim(); LocalScreenStore.ScreenInputType mode = modeButton.getValue(); String url = mode == LocalScreenStore.ScreenInputType.LINK ? urlField.getText().trim() : "";
        String apiMode = switch (mode) { case CAMERA -> "CAMERA"; case REDSTONE -> "REDSTONE"; case LINK -> "KINO"; };
        JsonObject body = new JsonObject(); body.addProperty("name", name); body.addProperty("mode", apiMode); body.addProperty("dimension", client.world.getRegistryKey().getValue().toString()); body.add("pos1", position(pos1)); body.add("pos2", position(pos2)); body.addProperty("front", clickedSide.asString().toUpperCase()); body.addProperty("cinemaUrl", url);
        status = "Bildschirm wird gespeichert ..."; createButton.active = false;
        MpsqApiClient.post("/screens", body).thenCompose(created -> {
            if (mode != LocalScreenStore.ScreenInputType.CAMERA) return ScreenSyncManager.refresh();
            UUID screenId = createdScreenId(created);
            if (screenId == null) return java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("Server lieferte keine Bildschirm-ID"));
            JsonObject assignment = new JsonObject(); assignment.addProperty("cameraId", selectedCameraId.toString()); assignment.addProperty("sortOrder", 0);
            return MpsqApiClient.post("/screens/" + screenId + "/cameras", assignment).thenCompose(ignored -> ScreenSyncManager.refresh());
        }).whenComplete((ignored, error) -> client.execute(() -> {
            if (error == null) close(); else { Throwable cause = error.getCause() == null ? error : error.getCause(); status = "Nicht gespeichert: " + (cause.getMessage() == null ? "Serverfehler" : cause.getMessage()); updateCreateButton(); }
        }));
    }
    private static UUID createdScreenId(JsonElement result) {
        JsonObject row = result != null && result.isJsonArray() && result.getAsJsonArray().size() > 0 ? result.getAsJsonArray().get(0).getAsJsonObject() : result != null && result.isJsonObject() ? result.getAsJsonObject() : null;
        try { return row != null && row.has("id") ? UUID.fromString(row.get("id").getAsString()) : null; } catch (IllegalArgumentException ignored) { return null; }
    }
    private static boolean screenNameExists(String name) { return LocalScreenStore.getAllScreens().stream().anyMatch(s -> s.name() != null && s.name().trim().equalsIgnoreCase(name.trim())); }
    private static JsonObject position(BlockPos pos) { JsonObject r = new JsonObject(); r.addProperty("x", pos.getX()); r.addProperty("y", pos.getY()); r.addProperty("z", pos.getZ()); return r; }
    @Override public void renderBackground(DrawContext c, int x, int y, float d) { super.renderBackground(c, x, y, d); MpsqTheme.drawBackground(c, width, height); MpsqTheme.drawPanel(c, (width - 260) / 2, height / 2 - 100, 260, 290); }
    @Override public void render(DrawContext c, int x, int y, float d) { super.render(c, x, y, d); int cx = width / 2, titleY = height / 2 - 96; c.drawCenteredTextWithShadow(textRenderer, title, cx, titleY, MpsqTheme.TEXT_TITEL); c.drawCenteredTextWithShadow(textRenderer, Text.literal(breite + " × " + hoehe + " × " + tiefe + " Blöcke"), cx, titleY + 12, MpsqTheme.TEXT_GEDAEMPT); if (!status.isBlank()) c.drawCenteredTextWithShadow(textRenderer, Text.literal(status), cx, height / 2 + 76, 0xFF5555); }
    @Override public boolean shouldPause() { return false; }
}
