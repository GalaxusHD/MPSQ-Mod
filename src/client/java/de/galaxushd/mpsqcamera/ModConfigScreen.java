package de.galaxushd.mpsqcamera;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ModConfigScreen extends Screen {
    private static final Identifier LOGO_TEXTURE = Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID, "textures/gui/mpsqlogo.png");
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
    private static final int COLLECTION_WIDTH = 88;
    private static final int SYSTEM_MARGIN = 8;

    private final String page;
    private final Screen parent;
    private EditBox codeInputField;
    private Button joinButton;
    private boolean roleRefreshStarted;

    public ModConfigScreen() { this("",null); }
    private ModConfigScreen(String page,Screen parent) { super(page.isEmpty()?Component.translatable("gui.mpsqcamera.main.title"):Component.literal(page));this.page=page;this.parent=parent; }
    private void menuButton(String label,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());}
    private void section(String label){minecraft.gui.setScreen(new ModConfigScreen(label,this));}

    @Override
    protected void init() {
        refreshRoleForMenu();
        int buttonWidth = Math.min(BUTTON_WIDTH, width - HORIZONTAL_MARGIN * 2);
        int menuHeight = MENU_CONTROL_COUNT * BUTTON_HEIGHT + (MENU_CONTROL_COUNT - 1) * BUTTON_SPACING;
        int menuTop = height / 2 + Math.max(0, (height / 2 - menuHeight) / 2);
        int menuX = (width - buttonWidth) / 2;
        codeInputField=null;joinButton=null;
        TeamRank base=TeamStateStore.self().map(TeamProfile::baseRank).orElse(TeamRank.PLAYER);
        boolean officer=base.level()>=TeamRank.OFFICER.level();
        boolean manage=TeamStateStore.self().map(p->p.permissionRank().level()>=TeamRank.OFFICER.level()).orElse(false);
        if(!page.isEmpty()){
            int y=45;
            switch(page){
                case "Kino" -> {
                    codeInputField=addRenderableWidget(new EditBox(font,menuX,y,buttonWidth,20,Component.translatable("gui.mpsqcamera.main.activation.placeholder")));
                    codeInputField.setMaxLength(6);codeInputField.setResponder(code->updateActivationCodeState());setInitialFocus(codeInputField);
                    joinButton=addRenderableWidget(Button.builder(Component.translatable("gui.mpsqcamera.main.join"),b->submitCode()).bounds(menuX,y+26,buttonWidth,20).build());
                    menuButton("Bildschirme",menuX,y+52,buttonWidth,this::openScreens);updateActivationCodeState();
                }
                case "Bearbeiten" -> {
                    menuButton("Bildschirme",menuX,y,buttonWidth,this::openScreens);y+=26;
                    if(TeamStateStore.self().map(TeamProfile::canViewCameras).orElse(false)){menuButton("Kameras",menuX,y,buttonWidth,this::openCameras);y+=26;}
                    if(manage){menuButton("Kamerafahrten",menuX,y,buttonWidth,()->minecraft.gui.setScreen(new MpsqCinematicScreen(this)));y+=26;
                        
                        menuButton("Timer",menuX,y,buttonWidth,()->minecraft.gui.setScreen(new MpsqTimerScreen(this)));y+=26;
                        menuButton("Models",menuX,y,buttonWidth,()->minecraft.gui.setScreen(new MpsqModelsScreen(this)));}
                }
                case "Team" -> {
                    if(manage){menuButton("Rückkehr-Auslosung",menuX,y,buttonWidth,()->minecraft.gui.setScreen(new MpsqReturnScreen(this)));y+=26;}
                    if(officer){menuButton("Texte",menuX,y,buttonWidth,this::openTemplates);y+=26;}
                    if(officer||base.level()<TeamRank.SOLDIER.level()){menuButton("Todos",menuX,y,buttonWidth,this::openTodo);y+=26;}
                    if(officer){menuButton("Logs",menuX,y,buttonWidth,()->minecraft.gui.setScreen(new TeamLogsScreen(this)));y+=26;
                        menuButton("Replay",menuX,y,buttonWidth,()->minecraft.gui.setScreen(new MpsqReplayScreen(this)));}
                }
            }
            menuButton("Zurück",menuX,height-28,buttonWidth,this::onClose);return;
        }
        menuButton("Kino",menuX,nextControlY(menuTop,0),buttonWidth,()->section("Kino"));
        menuButton("Sammlung",menuX,nextControlY(menuTop,1),buttonWidth,()->minecraft.gui.setScreen(new MpsqCollectionScreen(this)));
        menuButton("Bildschirme",menuX,nextControlY(menuTop,2),buttonWidth,this::openScreens);
        menuButton("Team",LICENSE_MARGIN,height-LICENSE_MARGIN-72,LICENSE_WIDTH,()->section("Team"));
        menuButton("Bearbeiten",menuX,nextControlY(menuTop,3),buttonWidth,()->section("Bearbeiten"));
        menuButton("Einstellungen",menuX,nextControlY(menuTop,4),buttonWidth,this::openSettings);
        menuButton("Lizenz",LICENSE_MARGIN,height-LICENSE_MARGIN-20,LICENSE_WIDTH,this::openLicense);
        menuButton("Ränge",LICENSE_MARGIN,height-LICENSE_MARGIN-46,LICENSE_WIDTH,this::openMembers);
        addRenderableWidget(Button.builder(visibilityButtonText(),button->{TeamVisibilitySettings.toggle();minecraft.gui.setScreen(new ModConfigScreen());})
            .bounds(width-LICENSE_MARGIN-LICENSE_WIDTH,height-LICENSE_MARGIN-20,LICENSE_WIDTH,20).build());
        if(TeamVisibilitySettings.visible())addRenderableWidget(Button.builder(nameVisibilityButtonText(),this::toggleOwnNameVisibility)
            .bounds(width-LICENSE_MARGIN-LICENSE_WIDTH,height-LICENSE_MARGIN-46,LICENSE_WIDTH,20).build());
    }
    @Override public void onClose(){minecraft.gui.setScreen(parent);}

    private void refreshRoleForMenu() {
        if (roleRefreshStarted || minecraft == null) return;
        roleRefreshStarted = true;
        MpsqApiClient.initialize()
                .thenCompose(ignored -> MpsqApiClient.refreshTeamProfile())
                .thenCompose(ignored -> MpsqApiClient.refreshTeamMembers())
                .whenComplete((ignored, error) -> minecraft.execute(() -> {
                    if (error != null) {
                        MpsqCameraClient.LOGGER.debug("MPSQ-Rang konnte beim Öffnen des Menüs nicht geladen werden", error);
                    } else if (minecraft.gui.screen() == this) {
                        rebuildWidgets();
                    }
                }));
    }

    private int nextControlY(int menuTop, int index) { return menuTop + index * (BUTTON_HEIGHT + BUTTON_SPACING); }
    private void updateActivationCodeState() {
        if(codeInputField==null)return;
        boolean hasFocus = codeInputField.isFocused();
        boolean hasText = !codeInputField.getValue().isEmpty();
        codeInputField.setHint(hasFocus || hasText ? Component.empty() : Component.translatable("gui.mpsqcamera.main.activation.placeholder"));
        if (joinButton != null) joinButton.active = codeInputField.getValue().length() == 6;
    }
    private void submitCode() {
        if (codeInputField.getValue().length() != 6) return;
        JsonObject body = new JsonObject();
        body.addProperty("code", codeInputField.getValue());
        // A joined player receives not only the screen entry, but also every
        // camera that is linked to that screen.  Refresh both caches before the
        // list is shown, otherwise a viewer would see an offline camera screen.
        MpsqApiClient.post("/join", body)
                .thenCompose(result -> ScreenSyncManager.refresh())
                .thenCompose(ignored -> MpsqApiClient.refreshCameras())
                .thenRun(() ->
                minecraft.execute(() -> { codeInputField.setValue(""); minecraft.gui.setScreen(new BildschirmListScreen(this,"Kino".equals(page))); })
        ).exceptionally(error -> { MpsqCameraClient.LOGGER.warn("MPSQ-Code konnte nicht verwendet werden", error); return null; });
    }
    private void openScreens() { minecraft.gui.setScreen(new BildschirmListScreen(this,"Kino".equals(page))); }
    private void openCameras() { minecraft.gui.setScreen(new CameraListScreen(this)); }
    private void openSettings() { minecraft.gui.setScreen(new ModSettingsScreen(this)); }
    private void openTodo() { minecraft.gui.setScreen(new TeamTodoScreen(this)); }
    private void openTemplates() { minecraft.gui.setScreen(new TeamTemplatesScreen(this)); }
    private void openMembers() { minecraft.gui.setScreen(new TeamRankPreviewScreen(this)); }
    private void openLicense() { minecraft.gui.setScreen(new LizenzScreen(this)); }
    private Component visibilityButtonText() { return Component.translatable(TeamVisibilitySettings.visible()
            ? "gui.mpsqcamera.team.visibility.on" : "gui.mpsqcamera.team.visibility.off"); }
    private Component nameVisibilityButtonText() {
        return Component.translatable(TeamStateStore.self().map(TeamProfile::nameVisible).orElse(true)
                ? "gui.mpsqcamera.team.name_visibility.on" : "gui.mpsqcamera.team.name_visibility.off");
    }
    private void toggleOwnNameVisibility(Button button) {
        boolean next = !TeamStateStore.self().map(TeamProfile::nameVisible).orElse(true);
        button.active = false;
        MpsqApiClient.setOwnNameVisible(next).whenComplete((ignored, error) -> minecraft.execute(() -> {
            button.active = true;
            button.setMessage(nameVisibilityButtonText());
        }));
    }

    @Override public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) { context.fillGradient(0, 0, width, height, 0xCC1A1A1A, 0xCC050505); }
    private void drawAreaPlayerCount(GuiGraphicsExtractor context) {
        int menuHeight = MENU_CONTROL_COUNT * BUTTON_HEIGHT + (MENU_CONTROL_COUNT - 1) * BUTTON_SPACING;
        int menuTop = height / 2 + Math.max(0, (height / 2 - menuHeight) / 2);
        String label;
        if (minecraft.level == null) label = "Spieler im Bereich: keine Welt geöffnet";
        else if (!MpsqZoneCatalog.hasMaster()) label = "Spieler im Bereich: Gesamtfläche fehlt";
        else {
            long count = minecraft.level.players().stream()
                    .filter(player -> player == minecraft.player || minecraft.getConnection() != null && minecraft.getConnection().getPlayerInfo(player.getUUID()) != null)
                    .filter(MpsqGamesRuntime::activeParticipant)
                    .filter(player -> !MpsqKickAnimationManager.isClone(player))
                    .filter(MpsqZoneCatalog::inMaster).count();
            label = "Spieler im Bereich: " + count + " (lokal erfasst)";
        }
        context.centeredText(font, Component.literal(label), width / 2, menuTop - 12, 0xFF55FFFF);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        updateActivationCodeState(); super.extractRenderState(context, mouseX, mouseY, delta);
        if(!page.isEmpty()){context.centeredText(font,title,width/2,20,0xFFFFFFFF);return;}
        drawAreaPlayerCount(context);
        String rankError = MpsqApiClient.teamProfileError();
        if (!rankError.isEmpty()) {
            context.centeredText(font, Component.literal(rankError), width / 2, height / 2 - 14, 0xFFFF7777);
        } else if (TeamStateStore.self().isEmpty()) {
            context.centeredText(font, Component.literal("Teamrang wird geladen …"), width / 2, height / 2 - 14, 0xFFFFFF77);
        }
        int availableLogoHeight = height / 2 - LOGO_TOP_MARGIN - LOGO_BOTTOM_MARGIN;
        int logoSize = Math.max(1, Math.min(LOGO_MAX_SIZE, Math.min(width - HORIZONTAL_MARGIN * 2, availableLogoHeight)));
        context.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE, (width - logoSize) / 2, LOGO_TOP_MARGIN, 0, 0, logoSize, logoSize,
                LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE, LOGO_TEXTURE_SIZE);
    }
    @Override public boolean isPauseScreen() { return false; }
}



