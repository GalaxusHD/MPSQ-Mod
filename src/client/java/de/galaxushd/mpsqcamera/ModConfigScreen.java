package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModConfigScreen extends Screen {
    private static final Identifier LOGO_TEXTURE = Identifier.of(MpsqCameraClient.MOD_ID, "textures/gui/mpsqlogo.png");
    private static final int LOGO_TEXTURE_SIZE = 860;
    private static final int LOGO_MAX_SIZE = 192;
    private static final int LOGO_TOP_MARGIN = 8;
    private static final int LOGO_BOTTOM_MARGIN = 12;
    private static final int HORIZONTAL_MARGIN = 12;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 6;
    private static final int MENU_CONTROL_COUNT = 5;
    private static final int LICENSE_MARGIN = 6;
    private static final int LICENSE_WIDTH = 60;

    private TextFieldWidget codeInputField;
    private ButtonWidget joinButton;

    public ModConfigScreen() { super(Text.translatable("gui.mpsqcamera.main.title")); }

    @Override
    protected void init() {
        int buttonWidth = Math.min(BUTTON_WIDTH, width - HORIZONTAL_MARGIN * 2);
        int menuHeight = MENU_CONTROL_COUNT * BUTTON_HEIGHT + (MENU_CONTROL_COUNT - 1) * BUTTON_SPACING;
        int menuTop = height / 2 + Math.max(0, (height / 2 - menuHeight) / 2);
        int menuX = (width - buttonWidth) / 2;
        codeInputField = new TextFieldWidget(textRenderer, menuX, menuTop, buttonWidth, BUTTON_HEIGHT,
                Text.translatable("gui.mpsqcamera.main.activation.placeholder"));
        codeInputField.setMaxLength(6);
        codeInputField.setChangedListener(code -> updateActivationCodeState());
        addDrawableChild(codeInputField);
        setInitialFocus(codeInputField);
        joinButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.main.join"), button -> submitCode())
                .dimensions(menuX, nextControlY(menuTop, 1), buttonWidth, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.main.screens"), button -> openScreens())
                .dimensions(menuX, nextControlY(menuTop, 2), buttonWidth, BUTTON_HEIGHT).build());
        ButtonWidget cameraButton = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.main.cameras"), button -> openCameras())
                .dimensions(menuX, nextControlY(menuTop, 3), buttonWidth, BUTTON_HEIGHT).build());
        cameraButton.active = TeamStateStore.self().map(TeamProfile::canViewCameras).orElse(false);
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.main.settings"), button -> openSettings())
                .dimensions(menuX, nextControlY(menuTop, 4), buttonWidth, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.hauptmenu.lizenz"), button -> openLicense())
                .dimensions(LICENSE_MARGIN, height - LICENSE_MARGIN - BUTTON_HEIGHT, LICENSE_WIDTH, BUTTON_HEIGHT).build());
        addDrawableChild(ButtonWidget.builder(visibilityButtonText(), button -> {
                    TeamVisibilitySettings.toggle();
                    client.setScreen(new ModConfigScreen());
                })
                .dimensions(width - LICENSE_MARGIN - LICENSE_WIDTH, height - LICENSE_MARGIN - BUTTON_HEIGHT, LICENSE_WIDTH, BUTTON_HEIGHT).build());
        if (TeamVisibilitySettings.visible()) {
            addDrawableChild(ButtonWidget.builder(nameVisibilityButtonText(), button -> toggleOwnNameVisibility(button))
                    .dimensions(width - LICENSE_MARGIN - LICENSE_WIDTH,
                            height - LICENSE_MARGIN - BUTTON_HEIGHT * 2 - BUTTON_SPACING,
                            LICENSE_WIDTH, BUTTON_HEIGHT).build());
        }
        int teamButtonY = height - LICENSE_MARGIN - BUTTON_HEIGHT * 2 - BUTTON_SPACING;
        // Keep the Ränge entry available while a staff member temporarily
        // uses the 001 event rank, so it can be removed again.
        TeamRank baseTeamRank = TeamStateStore.self().map(TeamProfile::baseRank).orElse(TeamRank.PLAYER);
        boolean isOfficerOrHigher = baseTeamRank.level() >= TeamRank.OFFICER.level();
        {
            // From the bottom upwards: Lizenz → Ränge → Todos → Texte.
            // This keeps the related team controls in the requested reading
            // order while leaving the normal licence button untouched.
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.team.members"), button -> openMembers())
                    .dimensions(LICENSE_MARGIN, teamButtonY, LICENSE_WIDTH, BUTTON_HEIGHT).build());
            teamButtonY -= BUTTON_HEIGHT + BUTTON_SPACING;
        }
        if (isOfficerOrHigher) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.team.todo"), button -> openTodo())
                    .dimensions(LICENSE_MARGIN, teamButtonY, LICENSE_WIDTH, BUTTON_HEIGHT).build());
            teamButtonY -= BUTTON_HEIGHT + BUTTON_SPACING;
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.team.templates"), button -> openTemplates())
                    .dimensions(LICENSE_MARGIN, teamButtonY, LICENSE_WIDTH, BUTTON_HEIGHT).build());
        } else if (baseTeamRank.level() < TeamRank.SOLDIER.level()) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("gui.mpsqcamera.team.todo"), button -> openTodo())
                    .dimensions(LICENSE_MARGIN, teamButtonY, LICENSE_WIDTH, BUTTON_HEIGHT).build());
        }
        updateActivationCodeState();
    }

    private int nextControlY(int menuTop, int index) { return menuTop + index * (BUTTON_HEIGHT + BUTTON_SPACING); }
    private void updateActivationCodeState() {
        boolean hasFocus = codeInputField.isFocused();
        boolean hasText = !codeInputField.getText().isEmpty();
        codeInputField.setPlaceholder(hasFocus || hasText ? Text.empty() : Text.translatable("gui.mpsqcamera.main.activation.placeholder"));
        if (joinButton != null) joinButton.active = codeInputField.getText().length() == 6;
    }
    private void submitCode() {
        if (codeInputField.getText().length() != 6) return;
        JsonObject body = new JsonObject();
        body.addProperty("code", codeInputField.getText());
        // A joined player receives not only the screen entry, but also every
        // camera that is linked to that screen.  Refresh both caches before the
        // list is shown, otherwise a viewer would see an offline camera screen.
        MpsqApiClient.post("/join", body)
                .thenCompose(result -> ScreenSyncManager.refresh())
                .thenCompose(ignored -> MpsqApiClient.refreshCameras())
                .thenRun(() ->
                client.execute(() -> { codeInputField.setText(""); client.setScreen(new BildschirmListScreen(this)); })
        ).exceptionally(error -> { MpsqCameraClient.LOGGER.warn("MPSQ-Code konnte nicht verwendet werden", error); return null; });
    }
    private void openScreens() { client.setScreen(new BildschirmListScreen(this)); }
    private void openCameras() { client.setScreen(new CameraListScreen(this)); }
    private void openSettings() { client.setScreen(new ModSettingsScreen(this)); }
    private void openTodo() { client.setScreen(new TeamTodoScreen(this)); }
    private void openTemplates() { client.setScreen(new TeamTemplatesScreen(this)); }
    private void openMembers() { client.setScreen(new TeamMembersScreen(this)); }
    private void openLicense() { client.setScreen(new LizenzScreen(this)); }
    private Text visibilityButtonText() { return Text.translatable(TeamVisibilitySettings.visible()
            ? "gui.mpsqcamera.team.visibility.on" : "gui.mpsqcamera.team.visibility.off"); }
    private Text nameVisibilityButtonText() {
        return Text.translatable(TeamStateStore.self().map(TeamProfile::nameVisible).orElse(true)
                ? "gui.mpsqcamera.team.name_visibility.on" : "gui.mpsqcamera.team.name_visibility.off");
    }
    private void toggleOwnNameVisibility(ButtonWidget button) {
        boolean next = !TeamStateStore.self().map(TeamProfile::nameVisible).orElse(true);
        button.active = false;
        MpsqApiClient.setOwnNameVisible(next).whenComplete((ignored, error) -> client.execute(() -> {
            button.active = true;
            button.setMessage(nameVisibilityButtonText());
        }));
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { context.fillGradient(0, 0, width, height, 0xCC1A1A1A, 0xCC050505); }
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateActivationCodeState(); super.render(context, mouseX, mouseY, delta);
        int availableLogoHeight = height / 2 - LOGO_TOP_MARGIN - LOGO_BOTTOM_MARGIN;
        int logoSize = Math.max(1, Math.min(LOGO_MAX_SIZE, Math.min(width - HORIZONTAL_MARGIN * 2, availableLogoHeight)));
        context.drawTexture(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE, (width - logoSize) / 2, LOGO_TOP_MARGIN, 0, 0, logoSize, logoSize,
                LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE);
    }
    @Override public boolean shouldPause() { return false; }
}
