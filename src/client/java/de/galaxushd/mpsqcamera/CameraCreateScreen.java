package de.galaxushd.mpsqcamera;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Name confirmation shown before the C key creates a camera. */
public final class CameraCreateScreen extends Screen {
    private static final int WIDTH = 220;
    private EditBox nameField;
    private Button createButton;
    private String status = "";

    public CameraCreateScreen() {
        super(Component.literal("Kamera erstellen"));
    }

    @Override
    protected void init() {
        int x = width / 2 - WIDTH / 2;
        int y = height / 2 - 26;
        nameField = new EditBox(font, x, y, WIDTH, 20, Component.literal("Kameraname"));
        nameField.setMaxLength(64);
        nameField.setHint(Component.literal("Kameraname eingeben"));
        nameField.setResponder(ignored -> updateCreateButton());
        addRenderableWidget(nameField);
        createButton = addRenderableWidget(Button.builder(Component.literal("Erstellen"), button -> create())
                .bounds(x, y + 28, 108, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.back"), button -> onClose())
                .bounds(x + 112, y + 28, 108, 20).build());
        updateCreateButton();
        setInitialFocus(nameField);
    }

    private void updateCreateButton() {
        if (nameField == null || createButton == null) return;
        String wanted = nameField.getValue().trim();
        boolean duplicate = LocalCameraStore.getAll().stream()
                .anyMatch(camera -> camera.name().trim().equalsIgnoreCase(wanted));
        createButton.active = !wanted.isEmpty() && !duplicate;
        status = duplicate ? "Dieser Kameraname wird bereits verwendet." : "";
    }

    private void create() {
        String name = nameField.getValue().trim();
        if (name.isEmpty()) return;
        CameraCreationManager.createNamedCamera(minecraft, name);
        onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int centerX = width / 2;
        context.centeredText(font, title, centerX, height / 2 - 52, MpsqTheme.TEXT_TITEL);
        if (!status.isEmpty()) context.centeredText(font, Component.literal(status), centerX, height / 2 + 56, MpsqTheme.TEXT_GEDAEMPT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        MpsqTheme.drawBackground(context, width, height);
    }

    @Override public void onClose(){CameraCreationManager.clearAnchor();super.onClose();}

    @Override public boolean isPauseScreen() { return false; }
}
