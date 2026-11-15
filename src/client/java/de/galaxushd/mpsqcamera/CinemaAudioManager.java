package de.galaxushd.mpsqcamera;

import net.dimaskama.mcef.api.MCEFBrowser;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import java.util.IdentityHashMap;
import java.util.Map;

/** MCEF Modern plays audio natively; HTML media volumes follow the Minecraft sliders. */
public final class CinemaAudioManager {
    private static final Map<MCEFBrowser, AudioRoute> ROUTES = new IdentityHashMap<>();
    private static final Map<MCEFBrowser, Float> GAINS = new IdentityHashMap<>();
    private static boolean initialized;
    private static int ticks;
    private CinemaAudioManager() {}
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || !TeamVisibilitySettings.visible()) { clear(); return; }
            if (++ticks % 5 == 0) ROUTES.forEach(CinemaAudioManager::applyVolume);
        });
    }
    public static void registerBrowser(MCEFBrowser browser, AudioRoute route) {
        registerBrowser(browser,route,1f);
    }
    public static void registerBrowser(MCEFBrowser browser, AudioRoute route,float gain) {
        if (browser == null || route == null) return;
        initialize();
        GAINS.put(browser,Float.isFinite(gain)?Math.max(0,Math.min(1,gain)):0);
        AudioRoute previous = ROUTES.put(browser, route);
        if (previous != route) applyVolume(browser, route);
    }
    public static void unregisterBrowser(MCEFBrowser browser) {
        GAINS.remove(browser);
        if (browser != null && ROUTES.remove(browser) != null) silence(browser);
    }
    public static void clear() {
        ROUTES.keySet().forEach(CinemaAudioManager::silence);
        ROUTES.clear();
        GAINS.clear();
    }
    public static void stopAll() { clear(); }
    public static boolean isMusicAudioActive() {
        return ROUTES.values().stream().anyMatch(route -> routeGain(route) > 0);
    }
    private static void applyVolume(MCEFBrowser browser, AudioRoute route) {
        String volume = Float.toString(routeGain(route)*GAINS.getOrDefault(browser,1f));
        execute(browser, volumeScript(volume));
    }
    static String volumeScript(String volume) {
        return """
            (()=>{const apply=d=>{
              d.querySelectorAll('video,audio').forEach(e=>{e.volume=__GAIN__;});
              d.querySelectorAll('iframe').forEach(f=>{
                try{if(f.contentDocument)apply(f.contentDocument)}catch(e){}
                try{const u=new URL(f.src);
                  if(u.hostname==='www.youtube.com'||u.hostname==='www.youtube-nocookie.com')
                    f.contentWindow.postMessage(JSON.stringify({event:'command',func:'setVolume',args:[__GAIN__*100]}),u.origin);
                }catch(e){}
              });
            };apply(document)})()
            """.replace("__GAIN__",volume);
    }

    private static void silence(MCEFBrowser browser) {
        execute(browser, "document.querySelectorAll('video,audio').forEach(e=>{e.pause();e.muted=true;});");
    }
    private static void execute(MCEFBrowser browser, String script) {
        try { browser.getCefBrowser().executeJavaScript(script, browser.getCefBrowser().getURL(), 0); }
        catch (RuntimeException error) { MpsqCameraClient.LOGGER.debug("Browser-Audio konnte nicht aktualisiert werden", error); }
    }
    private static float routeGain(AudioRoute route) {
        var options = Minecraft.getInstance().options;
        float master = options.getSoundSourceVolume(SoundSource.MASTER);
        float category = switch (route) {
            case CINEMA -> options.getSoundSourceVolume(SoundSource.BLOCKS);
            case AMBIENT -> options.getSoundSourceVolume(SoundSource.AMBIENT);
            case BLOCKS -> options.getSoundSourceVolume(SoundSource.BLOCKS);
        };
        return Math.max(0, Math.min(1, master * category));
    }
    public enum AudioRoute { CINEMA, AMBIENT, BLOCKS }
}
