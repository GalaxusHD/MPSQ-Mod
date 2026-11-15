package de.galaxushd.mpsqcamera;

import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Resource-pack audio, played at the listener rather than a world position. */
public final class MpsqAudioManager {
    private static boolean playing;
    private static float volume = 1;
    private static int trackIndex;
    private static int startupTicks;
    private static SimpleSoundInstance sound;
    private MpsqAudioManager() {}
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || !TeamVisibilitySettings.visible()) { if (playing) stop(); return; }
            if (!playing || client.isPaused()) return;
            if (startupTicks > 0) { startupTicks--; return; }
            if (sound != null && !client.getSoundManager().isActive(sound)) nextTrack();
        });
    }
    public static void startPlaylist(String name, List<String> tracks) {
        Identifier firstTrack = tracks == null || tracks.isEmpty() ? null : Identifier.tryParse(tracks.get(0));
        startPlaylist(name, tracks, categoryForSoundId(firstTrack));
    }
    public static void startPlaylist(String name, List<String> tracks, SoundSource category) {
        stop();
        List<String> valid = tracks == null ? List.of() : tracks.stream()
                .filter(id -> id != null && Identifier.tryParse(id) != null).toList();
        MpsqPlaylistManager.start(name, valid);
        trackIndex = 0;
        soundCategory = category == null ? SoundSource.AMBIENT : category;
        playing = !valid.isEmpty();
        playCurrent();
    }
    private static void playCurrent() {
        var manager = Minecraft.getInstance().getSoundManager();
        if (sound != null) manager.stop(sound);
        sound = null;
        if (!playing || !TeamVisibilitySettings.visible()) return;
        Identifier id = Identifier.tryParse(currentTrack());
        if (id == null || manager.getSoundEvent(id) == null) {
            MpsqCameraClient.LOGGER.warn("MPSQ-Audiodatei fehlt im Ressourcenpaket: {}", currentTrack());
            stop();
            return;
        }
        sound = new PlaylistSound(id);
        manager.play(sound);
        startupTicks = 20;
    }
    public static void stop() {
        if (sound != null) Minecraft.getInstance().getSoundManager().stop(sound);
        sound = null;
        playing = false;
        MpsqPlaylistManager.stop();
    }
    public static void setVolume(float value) {
        if (!Float.isFinite(value)) return;
        volume = Math.max(0, Math.min(1, value));
        // The tickable sound exposes the current volume to the sound engine every tick.
    }
    public static boolean playing() { return playing; }
    public static float volume() { return volume; }
    public static String currentTrack() {
        var tracks = MpsqPlaylistManager.tracks();
        return tracks.isEmpty() ? "" : tracks.get(Math.min(trackIndex, tracks.size() - 1));
    }
    public static void nextTrack() {
        if (!playing || !TeamVisibilitySettings.visible()) return;
        trackIndex++;
        if (trackIndex >= MpsqPlaylistManager.tracks().size()) { stop(); return; }
        playCurrent();
    }
    private static final class PlaylistSound extends SimpleSoundInstance implements net.minecraft.client.resources.sounds.TickableSoundInstance {
        PlaylistSound(Identifier id) { super(id,soundCategory,1f,1f,RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,0,0,0,true); }
        @Override public float getVolume(){return MpsqAudioManager.volume;}
        @Override public void tick(){ /* No position changes: playback is relative to the listener. */ }
        @Override public boolean isStopped(){return !playing;}
    }
    private static SoundSource soundCategory = SoundSource.AMBIENT;
    public static SoundSource categoryForSoundId(Identifier id) {
        return SoundSource.AMBIENT;
    }
}
