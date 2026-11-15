package de.galaxushd.mpsqcamera;


import net.dimaskama.mcef.api.MCEFBrowser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;


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
    private static final Map<UUID,Long> completed=new HashMap<>();
    private static int mouseRestoreDelayTicks;
    private static volatile long cursorRestoreSpikeGuardUntilNanos;
    private static volatile boolean refreshInProgress;
    private static boolean initializationAttempted;
    private static net.minecraft.client.multiplayer.ClientLevel playbackWorld;

    private CinemaBrowserManager() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (mouseRestoreDelayTicks > 0) {
                restoreGameMouse();
                mouseRestoreDelayTicks--;
            }
            checkWorld();
            if (client.level == null || !TeamVisibilitySettings.visible()) {
                clear();
                return;
            }
            BROWSERS.values().forEach(session -> removeLegacyCaptionShield(session.browser()));
            ticks++;
            if (ticks % REFRESH_INTERVAL_TICKS == 0) {checkVideoEnds();synchronize();}
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
        if (session == null || session.browser().getTextureView() == null) return null;
        return McefModernBridge.texture(session.browser());
    }

    /** Human-readable state used by the screen renderer while no browser image is available. */
    public static ScreenStatus status(LocalScreenStore.LocalScreenData screen) {
        if (!isVideoScreen(screen)) return ScreenStatus.NONE;
        if (screen.inputType()==LocalScreenStore.ScreenInputType.MPSQ_REDSTONE && completed.getOrDefault(screen.id(),-1L)==CinemaPlaybackStore.get(screen.id()).revision()) return ScreenStatus.OFFLINE;
        if (screen.inputType() == LocalScreenStore.ScreenInputType.MPSQ_REDSTONE) {
            if (!MpsqTriggerManager.hasScreenLink(screen.id())) return ScreenStatus.OFFLINE;
            if (screen.url().isBlank()) return ScreenStatus.BUTTON_PRESS;
        }
        if (screen.url().isBlank()) return ScreenStatus.OFFLINE;
        if (normalizeHttpUrl(screen.url()) == null || FAILED_BROWSERS.contains(screen.id())) return ScreenStatus.INACTIVE;
        if (!CinemaPlaybackStore.get(screen.id()).playing()) {
            return screen.inputType() == LocalScreenStore.ScreenInputType.MPSQ_REDSTONE
                    ? ScreenStatus.BUTTON_PRESS : ScreenStatus.INACTIVE;
        }
        if (!McefModernBridge.ready() || texture(screen.id()) == null) return ScreenStatus.INACTIVE;
        return ScreenStatus.NONE;
    }

    static void checkWorld() {
        var world = Minecraft.getInstance().level;
        if (world == playbackWorld) return;
        clear();
        playbackWorld = world;
        completed.clear();
        CinemaPlaybackStore.replace(Map.of());
        LocalScreenStore.replaceAll(java.util.List.of());
        ticks = 0;
    }

    public static void synchronize() { if(!TeamVisibilitySettings.visible()){clear();return;}
        checkWorld();
        if (playbackWorld == null) return;
        if (!McefModernBridge.ready() && !hasPlayingCinemaScreen()) return;
        if (!McefModernBridge.ready() && !ensureInitialized()) return;

        Set<UUID> wanted = new HashSet<>();
        for (LocalScreenStore.LocalScreenData screen : LocalScreenStore.getAllScreens()) {
            if (!isVideoScreen(screen) || screen.url().isBlank()) continue;
            CinemaPlaybackStore.PlaybackState playback = CinemaPlaybackStore.get(screen.id());
            if (!playback.playing() || completed.getOrDefault(screen.id(),-1L)==playback.revision()) continue;

            BrowserSession current = BROWSERS.get(screen.id());
            if (current != null && current.revision() == playback.revision()) {
                CinemaAudioManager.registerBrowser(current.browser(), audioRoute(screen));
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
                MCEFBrowser browser = McefModernBridge.createBrowser(url, false);
                CinemaAudioManager.registerBrowser(browser, audioRoute(screen));
                // This browser is a texture source only. Never let its Chromium
                // view take focus from Minecraft's mouse and camera controls.
                browser.setFocus(false);
                browser.resize(BROWSER_WIDTH, BROWSER_HEIGHT);
                BROWSERS.put(screen.id(), new BrowserSession(playback.revision(), browser));
                requestGameMouseRestore();
                FAILED_BROWSERS.remove(screen.id());
            } catch (RuntimeException error) {
                FAILED_BROWSERS.add(screen.id());
                requestGameMouseRestore();
                MpsqCameraClient.LOGGER.warn("Kino-Browser für Bildschirm {} konnte nicht erstellt werden", screen.id(), error);
            }
        }

        for (UUID id : new HashSet<>(BROWSERS.keySet())) {
            if (!wanted.contains(id)) close(id);
        }
    }

    private static void checkVideoEnds(){
        for(var entry:new java.util.ArrayList<>(BROWSERS.entrySet())){
            var screen=LocalScreenStore.getAllScreens().stream().filter(v->v.id().equals(entry.getKey())).findFirst().orElse(null);
            if(screen==null||screen.inputType()!=LocalScreenStore.ScreenInputType.MPSQ_REDSTONE)continue;
            var session=entry.getValue();String marker="mpsq-ended-"+entry.getKey()+"-"+session.revision();
            try{
                var browser=session.browser().getCefBrowser();
                if(browser.getURL().endsWith("#"+marker)){completed.put(entry.getKey(),session.revision());close(entry.getKey());continue;}
                String script="""
                    (()=>{if(window.__mpsqEndWatcher)return;window.__mpsqEndWatcher=true;
                    const end=()=>{try{history.replaceState(null,'','#MARKER')}catch(e){location.hash='MARKER'}};
                    const attach=()=>document.querySelectorAll('video,audio').forEach(v=>{if(!v.__mpsqEnd){v.__mpsqEnd=true;v.addEventListener('ended',end)}if(v.ended&&v.currentTime>0)end()});
                    attach();new MutationObserver(attach).observe(document,{childList:true,subtree:true});
                    window.addEventListener('message',e=>{
                      if(!['https://www.youtube.com','https://youtube.com','https://www.youtube-nocookie.com'].includes(e.origin))return;
                      if(![...document.querySelectorAll('iframe')].some(f=>f.contentWindow===e.source))return;
                      let d;try{d=typeof e.data==='string'?JSON.parse(e.data):e.data}catch(_){return}
                      if(d?.event==='onStateChange'&&d.info===0||d?.event==='infoDelivery'&&d.info?.playerState===0)end();
                    });})();
                    """.replace("MARKER",marker);
                browser.executeJavaScript(script,browser.getURL(),0);
            }catch(RuntimeException e){MpsqCameraClient.LOGGER.debug("Video-Ende noch nicht verfügbar",e);}
        }
    }

    /** Older hosted player pages hide already-playing video behind a caption shield.
     * Remove only that MPSQ overlay, including when it is inserted after page load.
     * The observer runs before the next paint; subtitles can still be disabled normally. */
    private static void removeLegacyCaptionShield(MCEFBrowser browser) {
        try {
            browser.getCefBrowser().executeJavaScript("""
                (()=>{
                  if(location.hostname!=='www.mixelpixel-squidgame.net'&&location.hostname!=='mixelpixel-squidgame.net')return;
                  if(location.pathname!=='/mpsq-player.html'||window.__mpsqVisibleVideo)return;
                  window.__mpsqVisibleVideo=true;
                  const reveal=()=>document.getElementById('caption-shield')?.remove();
                  new MutationObserver(reveal).observe(document,{childList:true,subtree:true});
                  reveal();
                })();
                """,browser.getCefBrowser().getURL(),0);
        } catch(RuntimeException error) {
            MpsqCameraClient.LOGGER.debug("Video page is still loading",error);
        }
    }
    private static boolean hasPlayingCinemaScreen() {
        for (LocalScreenStore.LocalScreenData screen : LocalScreenStore.getAllScreens()) {
            if (isVideoScreen(screen)
                    && !screen.url().isBlank()
                    && CinemaPlaybackStore.get(screen.id()).playing()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVideoScreen(LocalScreenStore.LocalScreenData screen) {
        return screen.inputType() == LocalScreenStore.ScreenInputType.LINK || screen.inputType() == LocalScreenStore.ScreenInputType.MPSQ_REDSTONE;
    }

    private static CinemaAudioManager.AudioRoute audioRoute(LocalScreenStore.LocalScreenData screen) {
        return CinemaAudioManager.AudioRoute.CINEMA;
    }

    /**
     * Chromium must not be started during Minecraft's own boot sequence.
     * On some PCs this leaves the game window black after the loading screen.
     * It is therefore initialized lazily, on the first world synchronization.
     */
    private static boolean ensureInitialized() {
        if (McefModernBridge.ready()) return true;
        
        initializationAttempted = true;
        try {
            if (!McefModernBridge.initialize()) {
                // Initialization and native downloads complete asynchronously.
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
    public static boolean ensureAudioReady() { if(!TeamVisibilitySettings.visible())return false;
        if (!McefModernBridge.ready() && !ensureInitialized()) return false;
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
        if (session != null) {
            CinemaAudioManager.unregisterBrowser(session.browser());
            try {
                McefModernBridge.close(session.browser());
            } finally {
                requestGameMouseRestore();
            }
        }
        // Some MCEF versions report browser=null in their audio callbacks, so we
        // cannot associate a stream with a screen. Once the last cinema browser
        // has closed, force-close the fallback source as well.
    }

    /** MCEF can alter GLFW cursor capture while initializing a hidden browser. */
    static void restoreGameMouse() {
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() == null && client.isWindowActive()) {
            if (client.mouseHandler.isMouseGrabbed()) return;
            cursorRestoreSpikeGuardUntilNanos = System.nanoTime() + 400_000_000L;
            client.mouseHandler.grabMouse();
        }
    }

    /** Drops only a large stale cursor delta produced while recapturing mouse input. */
    public static boolean discardCursorRestoreSpike(double deltaX, double deltaY) {
        if (System.nanoTime() > cursorRestoreSpikeGuardUntilNanos) return false;
        if (Math.abs(deltaX) <= 40.0 && Math.abs(deltaY) <= 40.0) return false;
        cursorRestoreSpikeGuardUntilNanos = 0L;
        return true;
    }

    /** MCEF may release GLFW cursor capture asynchronously after browser creation or close. */
    static void requestGameMouseRestore() {
        Minecraft client = Minecraft.getInstance();
        // Browser close callbacks may arrive outside the client thread.
        client.execute(() -> {
            if (client.gui.screen() == null && client.isWindowActive()) {
                restoreGameMouse();
                // Some MCEF builds change GLFW cursor mode well after close returns.
                // Keep recapturing during the transition instead of relying on one retry.
                mouseRestoreDelayTicks = Math.max(mouseRestoreDelayTicks, 200);
            }
        });
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

    public enum ScreenStatus {
        NONE("", 0, 0, 0),
        OFFLINE("OFFLINE", 155, 155, 155, "screen_offline"),
        INACTIVE("INAKTIV", 155, 155, 155, "screen_inactive"),
        BUTTON_PRESS("KNOPF DRUECKEN", 155, 155, 155, "screen_button_press"),
        NO_LINK("OFFLINE", 155, 155, 155, "screen_offline"),
        REDSTONE_OFFLINE("OFFLINE", 155, 155, 155, "screen_offline"),
        BLOCKED("INAKTIV", 155, 155, 155, "screen_inactive"),
        LOADING("INAKTIV", 155, 155, 155, "screen_inactive"),
        ERROR("INAKTIV", 155, 155, 155, "screen_inactive");

        private final String label;
        private final int red;
        private final int green;
        private final int blue;
        private final String overlay;

        ScreenStatus(String label, int red, int green, int blue) {
            this(label, red, green, blue, null);
        }

        ScreenStatus(String label, int red, int green, int blue, String overlay) {
            this.label = label;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.overlay = overlay;
        }

        public Identifier overlayTexture() {
            return overlay == null ? null : Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID, "textures/screens/" + overlay + ".png");
        }
        public String label() { return label; }
        public int red() { return red; }
        public int green() { return green; }
        public int blue() { return blue; }
    }
}
