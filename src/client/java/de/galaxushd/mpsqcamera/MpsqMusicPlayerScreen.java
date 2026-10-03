package de.galaxushd.mpsqcamera;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Shared playlist browser and Officer-controlled music player. */
public final class MpsqMusicPlayerScreen extends Screen {
    private static final int ACCENT = 0xFFFF526F;
    private static final int PANEL = 0xE5191D28;
    private static final int TRACK_ROW_HEIGHT = 21;
    private final List<Playlist> playlists = new ArrayList<>();
    private int selected = -1;
    private int selectedTrack;
    private int playlistScroll;
    private int trackScroll;
    private String status = "Playlists werden geladen…";
    private boolean loading;

    public MpsqMusicPlayerScreen() {
        super(Text.literal("MPSQ · Musikplayer"));
    }

    private boolean canControl() {
        return TeamStateStore.self().map(profile -> profile.permissionRank().level() >= TeamRank.OFFICER.level()).orElse(false);
    }

    @Override
    protected void init() {
        loadPlaylists();
        int panelWidth = Math.min(430, width - 32);
        int left = (width - panelWidth) / 2;
        int bottom = (height - panelHeight()) / 2 + panelHeight() - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal("Schließen"), button -> close())
                .dimensions(left, bottom, 130, 22).build());
        if (canControl()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("▶ Titel abspielen"), button -> playSelected())
                    .dimensions(left + 140, bottom, 142, 22).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("■ Stoppen"), button -> stopPlayback())
                    .dimensions(left + 292, bottom, panelWidth - 292, 22).build());
        }
    }

    private void loadPlaylists() {
        if (loading) return;
        loading = true;
        MpsqApiClient.get("/playlists").whenComplete((json, error) -> MinecraftClient.getInstance().execute(() -> {
            loading = false;
            if (error != null) {
                status = "Playlists konnten nicht geladen werden: " + error.getMessage();
            } else {
                playlists.clear();
                if (json.isJsonArray()) {
                    for (JsonElement element : json.getAsJsonArray()) {
                        if (!element.isJsonObject()) continue;
                        JsonObject row = element.getAsJsonObject();
                        if (!row.has("id") || !row.has("name") || !row.has("tracks") || !row.get("tracks").isJsonArray()) continue;
                        List<String> tracks = new ArrayList<>();
                        row.getAsJsonArray("tracks").forEach(track -> { if (track.isJsonPrimitive()) tracks.add(track.getAsString()); });
                        List<String> names = new ArrayList<>();
                        if (row.has("trackNames") && row.get("trackNames").isJsonArray()) {
                            row.getAsJsonArray("trackNames").forEach(name -> { if (name.isJsonPrimitive()) names.add(name.getAsString()); });
                        }
                        while (names.size() < tracks.size()) names.add(tracks.get(names.size()));
                        if (!tracks.isEmpty()) playlists.add(new Playlist(row.get("id").getAsString(), row.get("name").getAsString(), tracks, names));
                    }
                }
                selected = playlists.isEmpty() ? -1 : Math.min(Math.max(selected, 0), playlists.size() - 1);
                selectedTrack = selected < 0 ? 0 : Math.min(selectedTrack, playlists.get(selected).tracks().size() - 1);
                status = playlists.isEmpty() ? "Noch keine Playlists verfügbar." : "Wähle eine Playlist und dann einen Titel aus.";
            }
        }));
    }

    private void playSelected() {
        if (!canControl()) { status = "Abspielen dürfen nur Offiziere und höhere Ränge."; return; }
        if (selected < 0 || selected >= playlists.size()) { status = "Bitte zuerst eine Playlist auswählen."; return; }
        if (!MpsqActionSync.isMpsqServer()) { status = "Der gemeinsame Player ist auf dem MPSQ-Server verfügbar."; return; }
        Playlist playlist = playlists.get(selected);
        int start = Math.max(0, Math.min(selectedTrack, playlist.tracks().size() - 1));
        JsonObject action = new JsonObject();
        action.addProperty("sourceType", "mp3");
        action.addProperty("playlistName", playlist.name());
        com.google.gson.JsonArray ids = new com.google.gson.JsonArray();
        playlist.tracks().subList(start, playlist.tracks().size()).forEach(ids::add);
        action.add("tracks", ids);
        String title = playlist.trackNames().get(start);
        sendAction("START_PLAYLIST", action, "Wiedergabe ab „" + title + "“ für alle Mod-Nutzer gestartet.");
    }

    private void stopPlayback() {
        if (!canControl()) { status = "Stoppen dürfen nur Offiziere und höhere Ränge."; return; }
        if (!MpsqActionSync.isMpsqServer()) { status = "Der gemeinsame Player ist auf dem MPSQ-Server verfügbar."; return; }
        sendAction("STOP_AUDIO", new JsonObject(), "Wiedergabe für alle Mod-Nutzer gestoppt.");
    }

    private void sendAction(String type, JsonObject data, String success) {
        JsonObject body = new JsonObject();
        body.addProperty("serverId", MpsqActionSync.server());
        body.addProperty("worldId", MpsqActionSync.world());
        body.addProperty("actionType", type);
        body.add("actionData", data);
        status = "Sende Aktion…";
        MpsqApiClient.post("/actions", body).whenComplete((ignored, error) -> MinecraftClient.getInstance().execute(() -> {
            status = error == null ? success : "Aktion fehlgeschlagen: " + error.getMessage();
        }));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int panelWidth = Math.min(430, width - 32);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight()) / 2;
        int playlistTop = top + 78;
        int playlistBottom = playlistTop + visiblePlaylistRows() * 28;
        if (mouseX >= left + 16 && mouseX <= left + panelWidth - 16
                && mouseY >= playlistTop && mouseY < playlistBottom) {
            int row = (int) ((mouseY - playlistTop) / 28) + playlistScroll;
            if (row >= 0 && row < playlists.size()) {
                selected = row;
                selectedTrack = 0;
                trackScroll = 0;
                status = "Wähle einen Titel aus „" + playlists.get(row).name() + "“.";
                return true;
            }
        }

        if (selected >= 0 && selected < playlists.size()) {
            int trackTop = trackListTop(top);
            int visible = visibleTrackRows(top);
            int trackBottom = trackTop + visible * TRACK_ROW_HEIGHT;
            if (mouseX >= left + 16 && mouseX <= left + panelWidth - 16
                    && mouseY >= trackTop && mouseY < trackBottom) {
                int row = (int) ((mouseY - trackTop) / TRACK_ROW_HEIGHT) + trackScroll;
                if (row >= 0 && row < playlists.get(selected).tracks().size()) {
                    selectedTrack = row;
                    status = "Ausgewählt: " + playlists.get(selected).trackNames().get(row);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int panelWidth = Math.min(430, width - 32);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight()) / 2;
        if (mouseX >= left && mouseX <= left + panelWidth && mouseY < trackListTop(top)) {
            int visible = visiblePlaylistRows();
            playlistScroll = Math.max(0, Math.min(Math.max(0, playlists.size() - visible),
                    playlistScroll - (int) Math.signum(verticalAmount)));
            return true;
        }
        if (selected >= 0 && selected < playlists.size() && mouseY >= trackListTop(top)) {
            int max = Math.max(0, playlists.get(selected).tracks().size() - visibleTrackRows(top));
            trackScroll = Math.max(0, Math.min(max, trackScroll - (int) Math.signum(verticalAmount)));
            return true;
        }
        return true;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);
        int panelWidth = Math.min(430, width - 32);
        int panelHeight = panelHeight();
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        context.fill(left - 3, top - 3, left + panelWidth + 3, top + panelHeight + 3, 0xFF080A10);
        context.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
        context.fill(left, top, left + panelWidth, top + 3, ACCENT);
        context.fill(left, top, left + 3, top + panelHeight, 0xFF8F1738);
        context.drawTextWithShadow(textRenderer, "MPSQ  /  MUSIKPLAYER", left + 16, top + 14, ACCENT);
        context.drawTextWithShadow(textRenderer, "Gemeinsame Playlists", left + 16, top + 34, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "Playlist anklicken, dann gewünschten Titel auswählen", left + 16, top + 54, 0xFFBFC3CF);

        int playlistTop = top + 78;
        int visiblePlaylists = visiblePlaylistRows();
        for (int i = 0; i < visiblePlaylists; i++) {
            int index = playlistScroll + i;
            if (index >= playlists.size()) break;
            Playlist playlist = playlists.get(index);
            int y = playlistTop + i * 28;
            int bg = index == selected ? 0xFF8E2040 : 0xFF272B36;
            context.fill(left + 16, y, left + panelWidth - 16, y + 24, bg);
            context.drawTextWithShadow(textRenderer, playlist.name(), left + 25, y + 5, 0xFFFFFFFF);
            String trackLabel = playlist.tracks().size() + " Titel";
            context.drawTextWithShadow(textRenderer, trackLabel, left + panelWidth - 25 - textRenderer.getWidth(trackLabel), y + 5, 0xFFD2D4DC);
        }
        if (playlists.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "Keine Playlists vorhanden.", left + 18, playlistTop + 8, 0xFFD2D4DC);
        } else if (selected >= 0 && selected < playlists.size()) {
            Playlist current = playlists.get(selected);
            int trackTop = trackListTop(top);
            context.drawTextWithShadow(textRenderer, "Titel · ab hier wird die Playlist abgespielt", left + 16, trackTop - 14, 0xFFFFA4B4);
            int visibleTracks = visibleTrackRows(top);
            for (int i = 0; i < visibleTracks; i++) {
                int index = trackScroll + i;
                if (index >= current.trackNames().size()) break;
                int y = trackTop + i * TRACK_ROW_HEIGHT;
                if (index == selectedTrack) context.fill(left + 16, y - 2, left + panelWidth - 16, y + TRACK_ROW_HEIGHT - 1, 0xFF8E2040);
                String title = (index + 1) + ". " + current.trackNames().get(index);
                int maxWidth = panelWidth - 50;
                while (textRenderer.getWidth(title) > maxWidth && title.length() > 4) title = title.substring(0, title.length() - 2) + "…";
                context.drawTextWithShadow(textRenderer, title, left + 22, y + 3, 0xFFE5E6EB);
            }
        }
        context.drawTextWithShadow(textRenderer, status, left + 16, top + panelHeight - 40, 0xFFBFC3CF);
    }

    private int visiblePlaylistRows() {
        return Math.max(1, Math.min(4, (panelHeight() - 230) / 28));
    }

    private int trackListTop(int top) {
        return top + 78 + visiblePlaylistRows() * 28 + 25;
    }

    private int visibleTrackRows(int top) {
        int bottom = top + panelHeight() - 54;
        return Math.max(1, (bottom - trackListTop(top)) / TRACK_ROW_HEIGHT);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() { MinecraftClient.getInstance().setScreen(null); }

    private int panelHeight() { return Math.min(height - 36, 390); }

    private record Playlist(String id, String name, List<String> tracks, List<String> trackNames) { }
}
