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

public final class BildschirmDetailScreen extends Screen {
    private static final int BUTTON_WIDTH = 180;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_GAP = 26;
    private static final int CONTENT_TOP = 54;
    private static final int CONTENT_BOTTOM = 56;
    private static final long SEEK_AMOUNT_MS = 10_000L;

    private final Screen parent;
    private final UUID screenId;
    private final boolean isCreator;

    private LocalScreenStore.ScreenInputType screenMode = LocalScreenStore.ScreenInputType.LINK;
    private String activationCode = "------";
    private String streamUrl = "";
    private boolean preserveUnsavedStreamUrl;

    private EditBox streamUrlField;
    private Button saveLinkButton;
    private int scrollOffset;
    private int maxScrollOffset;

    public BildschirmDetailScreen(Screen parent, String screenId, String name, boolean isCreator) {
        super(Component.literal(name));
        this.parent = parent;
        this.screenId = UUID.fromString(screenId);
        this.isCreator = isCreator;
        loadCachedScreenData();
    }

    @Override
    protected void init() {
        if (!preserveUnsavedStreamUrl) {
            loadCachedScreenData();
        }
        streamUrlField = null;
        saveLinkButton = null;
        activationCode = ScreenAccessStore.code(screenId);

        int x = width / 2 - BUTTON_WIDTH / 2;
        // Keep the actual content height separate from its visible, scrolled
        // position.  Calculating the scroll limit from an already shifted Y
        // coordinate made the limit shrink on every scroll and prevented
        // scrolling back up.
        int contentY = CONTENT_TOP;
        int y = contentY - scrollOffset;

        if (isCreator) {
            addRenderableWidget(Button.builder(
                            Component.literal("Modus: " + screenMode.text().getString()),
                            button -> toggleMode()
                    )
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            y += ROW_GAP;
            contentY += ROW_GAP;

            addRenderableWidget(Button.builder(
                            Component.literal("Umbenennen..."),
                            button -> minecraft.gui.setScreen(new BildschirmNameScreen(this, screenId, title.getString()))
                    )
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            y += ROW_GAP;
            contentY += ROW_GAP;

            if (screenMode == LocalScreenStore.ScreenInputType.CAMERA) {
                addRenderableWidget(Button.builder(
                                Component.literal("Kameras verwalten..."),
                                button -> minecraft.gui.setScreen(new CameraAssignmentScreen(this, screenId))
                        )
                        .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                        .build());
                y += ROW_GAP;
                contentY += ROW_GAP;
            } else if (screenMode == LocalScreenStore.ScreenInputType.LINK) {
                y = addCinemaControls(x, y);
                contentY += ROW_GAP * 3;
            }

            addRenderableWidget(Button.builder(
                            Component.literal("Code: " + activationCode),
                            button -> copyActivationCode()
                    )
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            y += ROW_GAP;
            contentY += ROW_GAP;

            addRenderableWidget(Button.builder(
                            Component.literal("Berechtigungen..."),
                            button -> minecraft.gui.setScreen(new BildschirmPermissionsScreen(this, screenId, activationCode))
                    )
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            y += ROW_GAP;
            contentY += ROW_GAP;

            addRenderableWidget(Button.builder(
                            Component.literal("Gruppieren..."),
                            button -> minecraft.gui.setScreen(new BildschirmGroupingScreen(this))
                    )
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            y += ROW_GAP;
            contentY += ROW_GAP;

            if (ScreenAccessStore.inGroup(screenId)) {
                addRenderableWidget(Button.builder(
                                Component.literal("Aus Gruppe entfernen"),
                                button -> removeFromGroup()
                        )
                        .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                        .build());
                y += ROW_GAP;
                contentY += ROW_GAP;
            }

            addRenderableWidget(Button.builder(
                            Component.literal("Bildschirm löschen").withStyle(ChatFormatting.RED),
                            button -> confirmDelete()
                    )
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            y += ROW_GAP;
            contentY += ROW_GAP;
        } else if (screenMode == LocalScreenStore.ScreenInputType.LINK) {
            CinemaPlaybackStore.PlaybackState state = CinemaPlaybackStore.get(screenId);

            addRenderableWidget(Button.builder(
                            Component.literal(state.playing() ? "Kino läuft" : "Kino angehalten"),
                            button -> {
                            }
                    )
                    .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .build());
            y += ROW_GAP;
            contentY += ROW_GAP;
        }

        maxScrollOffset = Math.max(0, contentY - (height - CONTENT_BOTTOM));
        scrollOffset = Math.min(scrollOffset, maxScrollOffset);

        addRenderableWidget(Button.builder(
                        Component.literal("Zurück zur Liste"),
                        button -> minecraft.gui.setScreen(parent)
                )
                .bounds(width / 2 - 75, height - 28, 150, BUTTON_HEIGHT)
                .build());
    }

   private int addCinemaControls(int x, int y) {
    int controlGap = 4;
    int halfWidth = (BUTTON_WIDTH - controlGap) / 2;
    int thirdWidth = (BUTTON_WIDTH - 2 * controlGap) / 3;

    streamUrlField = new EditBox(
            font, x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
            Component.literal("Video- oder Stream-Link")
    );
    streamUrlField.setMaxLength(2048);
    streamUrlField.setHint(Component.literal("https://..."));
    streamUrlField.setValue(streamUrl);
    addRenderableWidget(streamUrlField);
    y += ROW_GAP;

    saveLinkButton = addRenderableWidget(Button.builder(
                    Component.literal("Link speichern"),
                    button -> saveCinemaLink()
            )
            .bounds(x, y, halfWidth, BUTTON_HEIGHT)
            .build());

    addRenderableWidget(Button.builder(
                    Component.literal("Link reset"),
                    button -> resetCinemaLink()
            )
            .bounds(x + halfWidth + controlGap, y, halfWidth, BUTTON_HEIGHT)
            .build());
    y += ROW_GAP;

    updateLinkButtons(streamUrlField.getValue());
    streamUrlField.setResponder(this::updateLinkButtons);

    CinemaPlaybackStore.PlaybackState state = CinemaPlaybackStore.get(screenId);

    addRenderableWidget(Button.builder(
                    Component.literal("-10 Sek"),
                    button -> seek(-SEEK_AMOUNT_MS)
            )
            .bounds(x, y, thirdWidth, BUTTON_HEIGHT)
            .build());

    addRenderableWidget(Button.builder(
                    Component.literal(state.playing() ? "Stop" : "Start"),
                    button -> setPlaying(!state.playing())
            )
            .bounds(x + thirdWidth + controlGap, y, thirdWidth, BUTTON_HEIGHT)
            .build());

    addRenderableWidget(Button.builder(
                    Component.literal("+10 Sek"),
                    button -> seek(SEEK_AMOUNT_MS)
            )
            .bounds(
                    x + 2 * (thirdWidth + controlGap),
                    y,
                    BUTTON_WIDTH - 2 * (thirdWidth + controlGap),
                    BUTTON_HEIGHT
            )
            .build());

    return y + ROW_GAP;
}

    private void updateLinkButtons(String value) {
        boolean valid = CinemaBrowserManager.normalizeHttpUrl(value) != null;

        if (saveLinkButton != null) {
            saveLinkButton.active = valid;
        }
    }


    private void loadCachedScreenData() {
        LocalScreenStore.LocalScreenData screen = LocalScreenStore.findById(screenId).orElse(null);

        if (screen != null) {
            screenMode = screen.inputType();
            streamUrl = screen.url() == null ? "" : screen.url();
        }
    }

    private void toggleMode() {
        if (screenMode == LocalScreenStore.ScreenInputType.CAMERA && ScreenCameraStore.hasCameras(screenId)) {
            showStatus("Entferne zuerst alle Kameras von diesem Bildschirm.");
            return;
        }

        LocalScreenStore.ScreenInputType nextMode = switch (screenMode) {
            case CAMERA -> LocalScreenStore.ScreenInputType.LINK;
            case LINK -> LocalScreenStore.ScreenInputType.MPSQ_REDSTONE;
            case MPSQ_REDSTONE -> LocalScreenStore.ScreenInputType.CAMERA;
        };
        if (nextMode == LocalScreenStore.ScreenInputType.CAMERA
                && screenMode != LocalScreenStore.ScreenInputType.MPSQ_REDSTONE && !streamUrl.isBlank()) {
            showStatus("Setze zuerst den Kino-Link zurück.");
            return;
        }

        JsonObject body = new JsonObject();
        try{var screen=LocalScreenStore.findById(screenId).orElseThrow();TeamVisibilitySettings.requireScreenPlacement(nextMode,screen.pos1(),screen.pos2());}catch(Exception e){showStatus(e.getMessage());return;}
        body.addProperty("mode", apiMode(nextMode));
        if (screenMode == LocalScreenStore.ScreenInputType.MPSQ_REDSTONE
                && nextMode == LocalScreenStore.ScreenInputType.CAMERA) {
            // The last triggered video is internal state, not an editable cinema link.
            body.addProperty("cinemaUrl", "");
            body.add("playbackState", playbackJson(false, 0L));
        }
        preserveUnsavedStreamUrl = false;

        MpsqApiClient.patch("/screens/" + screenId, body)
                .thenCompose(ignored -> ScreenSyncManager.refresh())
                .whenComplete((ignored, error) -> minecraft.execute(() -> {
                    if (error != null) {
                        showStatus("Modus konnte nicht gespeichert werden.");
                    } else {
                        rebuildWidgets();
                    }
                }));
    }

    private static String apiMode(LocalScreenStore.ScreenInputType mode) { return mode == LocalScreenStore.ScreenInputType.CAMERA ? "CAMERA" : mode == LocalScreenStore.ScreenInputType.MPSQ_REDSTONE ? "REDSTONE" : "KINO"; }

    private void saveCinemaLink() {
        String url = CinemaBrowserManager.normalizeHttpUrl(streamUrlField.getValue());

        if (url == null) {
            showStatus("Bitte gib einen gültigen http(s)-Link ein.");
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("mode", apiMode(screenMode));
        body.addProperty("cinemaUrl", url);
        streamUrl = url;
        preserveUnsavedStreamUrl = false;

        saveScreenPatch(body, "Link gespeichert.");
    }

    private void resetCinemaLink() {
        JsonObject body = new JsonObject();
        body.addProperty("cinemaUrl", "");
        body.add("playbackState", playbackJson(false, 0L));
        streamUrl = "";
        preserveUnsavedStreamUrl = false;

        saveScreenPatch(body, "Link zurückgesetzt.");
    }

    private void setPlaying(boolean playing) {
        CinemaPlaybackStore.PlaybackState state = CinemaPlaybackStore.get(screenId);

        JsonObject body = new JsonObject();
        body.add("playbackState", playbackJson(
                playing,
                currentPlaybackPosition(state)
        ));

        saveScreenPatch(body, playing ? "Kino gestartet." : "Kino gestoppt.");
    }

    private void seek(long delta) {
        CinemaPlaybackStore.PlaybackState state = CinemaPlaybackStore.get(screenId);

        JsonObject body = new JsonObject();
        body.add("playbackState", playbackJson(
                state.playing(),
                Math.max(0L, currentPlaybackPosition(state) + delta)
        ));

        saveScreenPatch(body, "Position geändert.");
    }

    private JsonObject playbackJson(boolean playing, long positionMs) {
        CinemaPlaybackStore.PlaybackState previous = CinemaPlaybackStore.get(screenId);

        JsonObject playback = new JsonObject();
        playback.addProperty("playing", playing);
        playback.addProperty("positionMs", positionMs);
        playback.addProperty("revision", previous.revision() + 1L);

        return playback;
    }

    private long currentPlaybackPosition(CinemaPlaybackStore.PlaybackState state) {
        if (!state.playing() || state.updatedAtMs() <= 0L) {
            return state.positionMs();
        }

        return Math.max(
                0L,
                state.positionMs() + System.currentTimeMillis() - state.updatedAtMs()
        );
    }

    private void saveScreenPatch(JsonObject body, String successMessage) {
        MpsqApiClient.patch("/screens/" + screenId, body)
                .thenCompose(ignored -> ScreenSyncManager.refresh())
                .whenComplete((ignored, error) -> minecraft.execute(() -> {
                    if (error != null) {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        String message = cause.getMessage();
                        MpsqCameraClient.LOGGER.warn("Bildschirmänderung konnte nicht gespeichert werden", error);
                        showStatus("Nicht gespeichert: " + (message == null ? "Unbekannter Serverfehler" : message));
                    } else {
                        showStatus(successMessage);
                        rebuildWidgets();
                    }
                }));
    }

    private void copyActivationCode() {
        minecraft.keyboardHandler.setClipboard(activationCode);
        showStatus("Code kopiert.");
    }

    private void removeFromGroup() {
        MpsqApiClient.post("/screens/" + screenId + "/remove-from-group", new JsonObject())
                .thenCompose(ignored -> ScreenSyncManager.refresh())
                .whenComplete((ignored, error) -> minecraft.execute(() -> minecraft.gui.setScreen(parent)));
    }

    private void confirmDelete() {
        boolean inGroup = ScreenAccessStore.inGroup(screenId);

        minecraft.gui.setScreen(new ConfirmScreen(
                confirmed -> {
                    if (confirmed) {
                        MpsqApiClient.delete("/screens/" + screenId)
                                .thenCompose(ignored -> ScreenSyncManager.refresh())
                                .whenComplete((ignored, error) ->
                                        minecraft.execute(() -> minecraft.gui.setScreen(parent))
                                );
                    } else {
                        minecraft.gui.setScreen(this);
                    }
                },
                Component.literal("Bildschirm löschen"),
                Component.literal(inGroup
                        ? "Die gesamte Gruppe wird gelöscht."
                        : "Bildschirm wirklich löschen?"),
                Component.literal("Löschen"),
                Component.literal("Abbrechen")
        ));
    }

    private void showStatus(String message) {
        if (minecraft.player != null) {
            de.galaxushd.mpsqcamera.compat.PlayerMessages.send(minecraft.player, Component.literal(message), true);
        }
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

        context.centeredText(
                font,
                title,
                centerX,
                28,
                MpsqTheme.TEXT_TITEL
        );
        context.fill(centerX - 130, 44, centerX + 130, 45, 0x44FFFFFF);

        if (screenMode != LocalScreenStore.ScreenInputType.CAMERA) {
            LocalScreenStore.LocalScreenData screen = LocalScreenStore.findById(screenId).orElse(null);

            if (screen != null) {
                CinemaBrowserManager.ScreenStatus status = CinemaBrowserManager.status(screen);
                String label = status == CinemaBrowserManager.ScreenStatus.NONE
                        ? (screenMode == LocalScreenStore.ScreenInputType.MPSQ_REDSTONE ? "MPSQ Redstone bereit" : "Kino bereit")
                        : status.label();

                int color = status == CinemaBrowserManager.ScreenStatus.NONE
                        ? MpsqTheme.TEXT_GEDAEMPT
                        : (status.red() << 16) | (status.green() << 8) | status.blue();

                context.centeredText(
                        font,
                        Component.literal(label),
                        centerX,
                        46,
                        color
                );
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScrollOffset <= 0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        if (streamUrlField != null) {
            streamUrl = streamUrlField.getValue();
            preserveUnsavedStreamUrl = true;
        }

        int previousOffset = scrollOffset;
        scrollOffset = Math.max(0, Math.min(maxScrollOffset,
                scrollOffset - (int) (verticalAmount * ROW_GAP)));

        if (scrollOffset != previousOffset) {
            rebuildWidgets();
        }
        return true;
    }
}
