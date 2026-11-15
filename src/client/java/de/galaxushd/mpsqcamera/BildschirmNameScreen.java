package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Small owner-only editor for a screen name. */
public final class BildschirmNameScreen extends Screen {
    private static final int FIELD_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private final UUID screenId;
    private final String previousName;
    private EditBox nameField;
    private Button saveButton;
    private String status = "";

    public BildschirmNameScreen(Screen parent, UUID screenId, String previousName) {
        super(Component.literal("Bildschirm umbenennen"));
        this.parent = parent;
        this.screenId = screenId;
        this.previousName = previousName;
    }

    @Override
    protected void init() {
        int x = width / 2 - FIELD_WIDTH / 2;
        int y = height / 2 - 26;
        nameField = new EditBox(font, x, y, FIELD_WIDTH, BUTTON_HEIGHT, Component.literal("Bildschirmname"));
        nameField.setMaxLength(64);
        nameField.setValue(previousName);
        nameField.setResponder(ignored -> updateSaveButton());
        addRenderableWidget(nameField);

        saveButton = addRenderableWidget(Button.builder(Component.literal("Speichern"), button -> save())
                .bounds(x, y + 28, 108, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.back"), button -> minecraft.gui.setScreen(parent))
                .bounds(x + 112, y + 28, 108, BUTTON_HEIGHT).build());
        updateSaveButton();
        setInitialFocus(nameField);
    }

    private void updateSaveButton() {
        if (saveButton == null || nameField == null) return;
        String wanted = nameField.getValue().trim();
        boolean duplicate = LocalScreenStore.getAllScreens().stream()
                .anyMatch(screen -> !screen.id().equals(screenId) && screen.name().trim().equalsIgnoreCase(wanted));
        saveButton.active = !wanted.isEmpty() && !duplicate;
        status = duplicate ? "Dieser Bildschirmname wird bereits verwendet." : "";
    }

    private void save() {
        String wanted = nameField.getValue().trim();
        if (wanted.isEmpty()) return;
        JsonObject body = new JsonObject();
        body.addProperty("name", wanted);
        saveButton.active = false;
        MpsqApiClient.patch("/screens/" + screenId, body)
                .thenCompose(ignored -> ScreenSyncManager.refresh())
                .whenComplete((ignored, error) -> minecraft.execute(() -> {
                    if (error != null) {
                        status = "Name konnte nicht gespeichert werden.";
                        updateSaveButton();
                    } else {
                        minecraft.gui.setScreen(parent);
                    }
                }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int centerX = width / 2;
        context.centeredText(font, title, centerX, height / 2 - 52, MpsqTheme.TEXT_TITEL);
        if (!status.isEmpty()) {
            context.centeredText(font, Component.literal(status), centerX, height / 2 + 56, MpsqTheme.TEXT_GEDAEMPT);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        MpsqTheme.drawBackground(context, width, height);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
