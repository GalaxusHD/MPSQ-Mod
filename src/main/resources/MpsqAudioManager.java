package de.galaxushd.mpsqcamera;

import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.Identifier;

/** Resource-pack audio, played at the listener rather than a world position. */
public final class MpsqAudioManager {
    private static boolean playing;
    private static float volume = 1;
    private static int trackIndex;
    private static int startupTicks;
    private static PositionedSoundInstance sound;
    private MpsqAudioManager() {}
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) { if (playing) stop(); return; }
            if (!playing || client.isPaused()) return;
            if (startupTicks > 0) { startupTicks--; return; }
            if (sound != null && !client.getSoundManager().isPlaying(sound)) nextTrack();
        });
    }
    public static void startPlaylist(String name, List<String> tracks) {
        Identifier firstTrack = tracks == null || tracks.isEmpty() ? null : Identifier.tryParse(tracks.get(0));
        startPlaylist(name, tracks, categoryForSoundId(firstTrack));
    }
    public static void startPlaylist(String name, List<String> tracks, SoundCategory category) {
        stop();
        List<String> valid = tracks == null ? List.of() : tracks.stream()
                .filter(id -> id != null && Identifier.tryParse(id) != null).toList();
        MpsqPlaylistManager.start(name, valid);
        trackIndex = 0;
        soundCategory = category == null ? SoundCategory.BLOCKS : category;
        playing = !valid.isEmpty();
        playCurrent();
    }
    private static void playCurrent() {
        var manager = MinecraftClient.getInstance().getSoundManager();
        if (sound != null) manager.stop(sound);
        sound = null;
        if (!playing) return;
        Identifier id = Identifier.tryParse(currentTrack());
        if (id == null || manager.get(id) == null) {
            MpsqCameraClient.LOGGER.warn("MPSQ-Audiodatei fehlt im Ressourcenpaket: {}", currentTrack());
            stop();
            return;
        }
        sound = new PositionedSoundInstance(id, soundCategory, volume, 1.0f, Random.create(), false, 0,
                SoundInstance.AttenuationType.NONE, 0.0, 0.0, 0.0, true);
        manager.play(sound);
        startupTicks = 20;
    }
    public static void stop() {
        if (sound != null) MinecraftClient.getInstance().getSoundManager().stop(sound);
        sound = null;
        playing = false;
        MpsqPlaylistManager.stop();
    }
    public static void setVolume(float value) {
        if (!Float.isFinite(value)) return;
        volume = Math.max(0, Math.min(1, value));
        if (sound != null) MinecraftClient.getInstance().getSoundManager().setVolume(sound, volume);
    }
    public static boolean playing() { return playing; }
    public static float volume() { return volume; }
    public static String currentTrack() {
        var tracks = MpsqPlaylistManager.tracks();
        return tracks.isEmpty() ? "" : tracks.get(Math.min(trackIndex, tracks.size() - 1));
    }
    public static void nextTrack() {
        if (!playing) return;
        trackIndex++;
        if (trackIndex >= MpsqPlaylistManager.tracks().size()) { stop(); return; }
        playCurrent();
    }
    private static SoundCategory soundCategory = SoundCategory.BLOCKS;
    public static SoundCategory categoryForSoundId(Identifier id) {
        return id != null && id.getPath().startsWith("entity.") ? SoundCategory.NEUTRAL : SoundCategory.BLOCKS;
    }
}
