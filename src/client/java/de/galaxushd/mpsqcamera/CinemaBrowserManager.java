package de.galaxushd.mpsqcamera;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the off-screen MCEF browsers used as cinema-screen textures.
 * A browser exists only while a cinema screen is playing, keeping CPU and RAM use bounded.
 */
public final class CinemaBrowserManager {
    private static final int BROWSER_WIDTH = 1280;
    private static final int BROWSER_HEIGHT = 720;
    private static final int REFRESH_INTERVAL_TICKS = 20;
    private static final int STATE_POLL_INTERVAL_TICKS = 100;
    private static final Map<UUID, BrowserSession> BROWSERS = new HashMap<>();
    private static final Set<UUID> FAILED_BROWSERS = new HashSet<>();
    private static int ticks;
    private static volatile boolean refreshInProgress;
    private static boolean initializationAttempted;

    private CinemaBrowserManager() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) {
                clear();
                return;
            }
            ticks++;
            if (ticks % REFRESH_INTERVAL_TICKS == 0) synchronize();
            if (ticks % STATE_POLL_INTERVAL_TICKS == 0 && MpsqApiClient.isReady() && !refreshInProgress) {
                refreshInProgress = true;
                ScreenSyncManager.refresh().whenComplete((ignored, error) -> refreshInProgress = false);
            }
        });
    }

    /**
     * Returns MCEF's Minecraft texture identifier. MCEF changed the method name
     * between releases, so this deliberately supports both public variants.
     */
    public static Identifier texture(UUID screenId) {
        BrowserSession session = BROWSERS.get(screenId);
        if (session == null || !session.browser().isTextureReady()) return null;
        return McefTextureCompat.texture(session.browser());
    }

    /** Human-readable state used by the screen renderer while no browser image is available. */
    public static ScreenStatus status(LocalScreenStore.LocalScreenData screen) {
        if (screen.inputType() != LocalScreenStore.ScreenInputType.LINK) return ScreenStatus.NONE;
        if (screen.url().isBlank()) return ScreenStatus.NO_LINK;
        if (normalizeHttpUrl(screen.url()) == null || FAILED_BROWSERS.contains(screen.id())) return ScreenStatus.ERROR;
        if (!MCEF.isInitialized()) return ScreenStatus.LOADING;
        if (!CinemaPlaybackStore.get(screen.id()).playing()) return ScreenStatus.OFFLINE;
        return texture(screen.id()) == null ? ScreenStatus.LOADING : ScreenStatus.NONE;
    }

    public static void synchronize() {
        if (!MCEF.isInitialized() && !hasPlayingCinemaScreen()) return;
        if (!MCEF.isInitialized() && !ensureInitialized()) return;

        Set<UUID> wanted = new HashSet<>();
        for (LocalScreenStore.LocalScreenData screen : LocalScreenStore.getAllScreens()) {
            if (screen.inputType() != LocalScreenStore.ScreenInputType.LINK || screen.url().isBlank()) continue;
            CinemaPlaybackStore.PlaybackState playback = CinemaPlaybackStore.get(screen.id());
            if (!playback.playing()) continue;

            BrowserSession current = BROWSERS.get(screen.id());
            if (current != null && current.revision() == playback.revision()) {
                wanted.add(screen.id());
                continue;
            }

            String url = playableUrl(screen.url(), currentPosition(playback));
            if (url == null) {
                FAILED_BROWSERS.add(screen.id());
                continue;
            }
            wanted.add(screen.id());

            close(screen.id());
            try {
                MCEFBrowser browser = MCEF.createBrowser(url, false);
                browser.resize(BROWSER_WIDTH, BROWSER_HEIGHT);
                BROWSERS.put(screen.id(), new BrowserSession(playback.revision(), browser));
                FAILED_BROWSERS.remove(screen.id());
            } catch (RuntimeException error) {
                FAILED_BROWSERS.add(screen.id());
                MpsqCameraClient.LOGGER.warn("Kino-Browser für Bildschirm {} konnte nicht erstellt werden", screen.id(), error);
            }
        }

        for (UUID id : new HashSet<>(BROWSERS.keySet())) {
            if (!wanted.contains(id)) close(id);
        }
    }

    private static boolean hasPlayingCinemaScreen() {
        for (LocalScreenStore.LocalScreenData screen : LocalScreenStore.getAllScreens()) {
            if (screen.inputType() == LocalScreenStore.ScreenInputType.LINK
                    && !screen.url().isBlank()
                    && CinemaPlaybackStore.get(screen.id()).playing()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Chromium must not be started during Minecraft's own boot sequence.
     * On some PCs this leaves the game window black after the loading screen.
     * It is therefore initialized lazily, on the first world synchronization.
     */
    private static boolean ensureInitialized() {
        if (MCEF.isInitialized()) return true;
        if (initializationAttempted) return false;
        initializationAttempted = true;
        try {
            if (!MCEF.initialize()) {
                MpsqCameraClient.LOGGER.warn("MCEF konnte nicht initialisiert werden; Kino-Bildschirme bleiben offline.");
                return false;
            }
            CinemaAudioManager.initialize();
            return true;
        } catch (RuntimeException exception) {
            MpsqCameraClient.LOGGER.warn("MCEF konnte nicht initialisiert werden; Kino-Bildschirme bleiben offline.", exception);
            return false;
        }
    }

    /** Lazily starts the shared MCEF audio bridge for uploaded MPSQ sounds. */
    public static boolean ensureAudioReady() {
        if (!MCEF.isInitialized() && !ensureInitialized()) return false;
        CinemaAudioManager.initialize();
        return true;
    }

    public static void clear() {
        new HashSet<>(BROWSERS.keySet()).forEach(CinemaBrowserManager::close);
        FAILED_BROWSERS.clear();
        CinemaAudioManager.clear();
    }

    private static void close(UUID screenId) {
        BrowserSession session = BROWSERS.remove(screenId);
        if (session != null) session.browser().close();
        // Some MCEF versions report browser=null in their audio callbacks, so we
        // cannot associate a stream with a screen. Once the last cinema browser
        // has closed, force-close the fallback source as well.
        if (BROWSERS.isEmpty()) CinemaAudioManager.stopAll();
    }

    /** Converts common YouTube links to their player URL, including a synchronized start point. */
    private static String playableUrl(String originalUrl, long positionMs) {
        try {
            String normalized = normalizeHttpUrl(originalUrl);
            if (normalized == null) return null;
            URI uri = new URI(normalized);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            String videoId = null;
            if (host.endsWith("youtu.be")) {
                videoId = uri.getPath().replaceFirst("^/", "");
            } else if (host.endsWith("youtube.com") && "/watch".equals(uri.getPath())) {
                for (String part : uri.getQuery() == null ? new String[0] : uri.getQuery().split("&")) {
                    if (part.startsWith("v=")) videoId = part.substring(2);
                }
            }
            if (videoId != null && !videoId.isBlank()) {
                long seconds = Math.max(0L, positionMs / 1000L);
                // YouTube now rejects a top-level off-screen browser without a referrer (error 153).
                // The static MPSQ player page supplies the required embedding origin.
                return "https://www.mixelpixel-squidgame.net/mpsq-player.html?v="
                        + URLEncoder.encode(videoId, StandardCharsets.UTF_8)
                        + "&start=" + seconds
                        + "&player=2";
            }
        } catch (URISyntaxException ignored) {
            // The browser will show the normal error page for an invalid URL.
        }
        return normalizeHttpUrl(originalUrl);
    }

    /** Only web links are allowed; local file/data URLs must never be opened by the client browser. */
    public static String normalizeHttpUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return null;
        String candidate = rawUrl.trim();
        if (!candidate.contains("://")) candidate = "https://" + candidate;
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme();
            if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && uri.getHost() != null) {
                return uri.toString();
            }
        } catch (URISyntaxException ignored) { }
        return null;
    }

    private static long currentPosition(CinemaPlaybackStore.PlaybackState state) {
        if (!state.playing() || state.updatedAtMs() <= 0L) return state.positionMs();
        return Math.max(0L, state.positionMs() + System.currentTimeMillis() - state.updatedAtMs());
    }

    /** A browser is recreated only for an explicit Start/Stop/seek command, never for normal time progression. */
    private record BrowserSession(long revision, MCEFBrowser browser) { }

    /** Mirrors WatchParty's MCEF compatibility check without relying on raw OpenGL texture ids. */
    private static final class McefTextureCompat {
        private static final Method TEXTURE_METHOD = findTextureMethod();
        private static boolean warningLogged;

        private McefTextureCompat() { }

        private static Identifier texture(MCEFBrowser browser) {
            if (browser == null || TEXTURE_METHOD == null) return null;
            try {
                Object value = TEXTURE_METHOD.invoke(browser);
                return value instanceof Identifier identifier ? identifier : null;
            } catch (IllegalAccessException | InvocationTargetException exception) {
                if (!warningLogged) {
                    warningLogged = true;
                    MpsqCameraClient.LOGGER.warn("MCEF-Texturkennung konnte nicht gelesen werden", exception);
                }
                return null;
            }
        }

        private static Method findTextureMethod() {
            for (String name : new String[]{"getTextureIdentifier", "getTextureLocation"}) {
                try {
                    return MCEFBrowser.class.getMethod(name);
                } catch (NoSuchMethodException ignored) {
                    // Try the next MCEF API variant.
                }
            }
            MpsqCameraClient.LOGGER.warn("Diese MCEF-Version stellt keine Minecraft-Texturkennung bereit.");
            return null;
        }
    }

    public enum ScreenStatus {
        NONE("", 0, 0, 0),
        NO_LINK("KEIN LINK", 140, 140, 140),
        OFFLINE("OFFLINE", 155, 155, 155),
        BLOCKED("BLOCKIERT", 210, 60, 55),
        LOADING("LAEDT", 225, 180, 55),
        ERROR("FEHLER", 210, 60, 55);

        private final String label;
        private final int red;
        private final int green;
        private final int blue;

        ScreenStatus(String label, int red, int green, int blue) {
            this.label = label;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        public String label() { return label; }
        public int red() { return red; }
        public int green() { return green; }
        public int blue() { return blue; }
    }
}
