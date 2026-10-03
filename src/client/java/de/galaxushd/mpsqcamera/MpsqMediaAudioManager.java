package de.galaxushd.mpsqcamera;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Plays uploaded MP3/MP4 assets through the MCEF audio bridge used by cinema screens. */
public final class MpsqMediaAudioManager {
    private static MCEFBrowser browser;
    private static MCEFBrowser effectBrowser;
    private static long effectExpiresAt;
    private static long generation;
    private static boolean active;

    private MpsqMediaAudioManager() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) {
                if (browser != null) stop();
                closeEffectBrowser();
            } else if (effectBrowser != null && System.currentTimeMillis() >= effectExpiresAt) {
                closeEffectBrowser();
            }
        });
    }

    /** Namespaced IDs are Minecraft registry entries; unqualified IDs are MPSQ uploads. */
    public static void playAuto(String id) {
        stop();
        if (id == null) return;
        String value=id.trim();
        if (value.isEmpty() || value.length()>128 || !value.matches("[a-zA-Z0-9_.:/-]+")) return;
        if (value.contains(":")) {
            Identifier identifier=Identifier.tryParse(value);
            if (identifier==null || !net.minecraft.registry.Registries.SOUND_EVENT.containsId(identifier)) {
                MpsqCameraClient.LOGGER.warn("Minecraft-Sound-ID ist nicht registriert: {}",value);
                return;
            }
            MpsqAudioManager.startPlaylist("MPSQ", List.of(identifier.toString()), MpsqAudioManager.categoryForSoundId(identifier));
            return;
        }
        if (!value.matches("[a-zA-Z0-9_-]{1,64}")) {
            MpsqCameraClient.LOGGER.warn("Ungültige namespacefreie MPSQ-Sound-ID: {}",value);
            return;
        }
        long request=++generation;
        MpsqApiClient.get("/sounds/"+value).whenComplete((json,error)->MinecraftClient.getInstance().execute(()->{
            if(request!=generation)return;
            if(error==null&&json!=null&&json.isJsonObject()) {
                JsonObject result=json.getAsJsonObject();
                String type=result.has("type")?result.get("type").getAsString():"";
                String url=result.has("url")?result.get("url").getAsString():"";
                if ((type.equals("mp3")||type.equals("mp4"))&&safeHttps(url)) { openPlayer(List.of(url),1.0f); return; }
            }
            MpsqCameraClient.LOGGER.warn("MPSQ-Sound-ID ist nicht verfügbar oder keine gültige MP3/MP4-Datei: {}",value);
        }));
    }

    public static void play(String type, List<String> assetIds) {
        stop();
        if (!(type.equals("mp3") || type.equals("mp4")) || assetIds == null || assetIds.isEmpty() || assetIds.size() > 100) return;
        long request = ++generation;
        List<java.util.concurrent.CompletableFuture<String>> lookups = new ArrayList<>();
        for (String id : assetIds) {
            if (id == null || !id.matches("[a-zA-Z0-9_-]{1,64}")) {
                MpsqCameraClient.LOGGER.warn("Ungültige MPSQ-Playlist-ID übersprungen: {}", id);
                continue;
            }
            lookups.add(MpsqApiClient.get("/sounds/" + id + "?type=" + type).handle((data, error) -> {
                if (error != null) {
                    MpsqCameraClient.LOGGER.warn("MPSQ-Playlist-Titel '{}' konnte nicht geladen werden und wird übersprungen", id, error);
                    return "";
                }
                try {
                    JsonObject result = data.getAsJsonObject();
                    String url = result.has("url") ? result.get("url").getAsString() : "";
                    if (safeHttps(url)) return url;
                } catch (RuntimeException ignored) { }
                MpsqCameraClient.LOGGER.warn("MPSQ-Playlist-Titel '{}' lieferte keine gültige Audio-URL und wird übersprungen", id);
                return "";
            }));
        }
        if (lookups.isEmpty()) {
            MpsqCameraClient.LOGGER.warn("MPSQ-Playlist enthält keine gültigen Audio-IDs.");
            return;
        }
        java.util.concurrent.CompletableFuture.allOf(lookups.toArray(java.util.concurrent.CompletableFuture[]::new))
                .whenComplete((ignored, error) -> MinecraftClient.getInstance().execute(() -> {
                    if (request != generation || error != null) {
                        if (error != null) MpsqCameraClient.LOGGER.warn("MPSQ-Audiodatei konnte nicht geladen werden", error);
                        return;
                    }
                    List<String> urls = lookups.stream().map(java.util.concurrent.CompletableFuture::join)
                            .filter(url -> !url.isBlank()).toList();
                    if (urls.isEmpty()) {
                        MpsqCameraClient.LOGGER.warn("Kein Titel der MPSQ-Playlist konnte geladen werden; Playlist bleibt ohne Wiedergabe.");
                        return;
                    }
                    openPlayer(urls,1.0f);
                }));
    }

    /** Plays the bundled short kick effect at a reduced volume without interrupting music. */
    public static void playBundledMp3(String resourcePath,float volume) {
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.world==null||!CinemaBrowserManager.ensureAudioReady())return;
        try(var stream=MpsqMediaAudioManager.class.getResourceAsStream(resourcePath)) {
            if(stream==null){MpsqCameraClient.LOGGER.warn("MPSQ-Sounddatei fehlt: {}",resourcePath);return;}
            String encoded=Base64.getEncoder().encodeToString(stream.readAllBytes());
            String audioUrl="data:audio/mpeg;base64,"+encoded;
            JsonArray urls=new JsonArray();urls.add(audioUrl);
            float safeVolume=Math.max(0.0f,Math.min(1.0f,volume));
            String html="<!doctype html><meta charset=utf-8><audio id=a autoplay></audio><script>const q="+urls+";let i=0,a=document.getElementById('a');a.volume="+safeVolume+";a.src=q[0];a.play().catch(()=>{});</script>";
            String page="data:text/html;base64,"+Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8));
            closeEffectBrowser();
            effectBrowser=MCEF.createBrowser(page,false);
            CinemaAudioManager.registerBrowser(effectBrowser, CinemaAudioManager.AudioRoute.AMBIENT);
            effectBrowser.setFocus(false);
            effectBrowser.resize(64,64);
            effectExpiresAt=System.currentTimeMillis()+8_000L;
        } catch(Exception exception) {
            closeEffectBrowser();
            MpsqCameraClient.LOGGER.warn("MPSQ-Kick-Sound konnte nicht abgespielt werden",exception);
        }
    }

    private static void openPlayer(List<String> urls,float volume) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !CinemaBrowserManager.ensureAudioReady()) return;
        JsonArray json = new JsonArray();
        urls.forEach(json::add);
        // A tiny off-screen Chromium page owns the HTML audio element; PCM is
        // routed back into Minecraft by the existing MCEF/OpenAL bridge.
        String html = "<!doctype html><meta charset=utf-8><audio id=a preload=auto></audio><script>const q=" + json
                + ";let i=0,a=document.getElementById('a');a.preload='auto';a.volume="+Math.max(0.0f,Math.min(1.0f,volume))+";const next=()=>{i++;if(i<q.length){a.dataset.started='';a.src=q[i];a.load()}};a.onended=next;a.onerror=next;a.oncanplay=()=>{if(a.dataset.started===String(i))return;a.dataset.started=String(i);a.currentTime=0;a.play().catch(()=>{a.dataset.started=''})};a.src=q[0];a.load();</script>";
        String page = "data:text/html;base64," + Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8));
        try {
            browser = MCEF.createBrowser(page, false);
            CinemaAudioManager.registerBrowser(browser, CinemaAudioManager.AudioRoute.AMBIENT);
            // The hidden audio browser must never capture Minecraft mouse or camera input.
            browser.setFocus(false);
            browser.resize(64, 64);
            CinemaBrowserManager.requestGameMouseRestore();
            active = true;
        } catch (RuntimeException exception) {
            browser = null;
            active = false;
            CinemaBrowserManager.requestGameMouseRestore();
            MpsqCameraClient.LOGGER.warn("MPSQ-Medienplayer konnte nicht gestartet werden", exception);
        }
    }

    private static boolean safeHttps(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.toString().length() <= 2048;
        } catch (IllegalArgumentException exception) { return false; }
    }

    public static boolean playing(){return active;}

    public static void stop() {
        generation++;
        active = false;
        MCEFBrowser current = browser;
        browser = null;
        if (current != null) {
            CinemaAudioManager.unregisterBrowser(current);
            try { current.close(); }
            catch (RuntimeException exception) { MpsqCameraClient.LOGGER.debug("MPSQ-Medienbrowser ließ sich nicht schließen", exception); }
            finally { CinemaBrowserManager.requestGameMouseRestore(); }
        }
    }

    private static void closeEffectBrowser() {
        MCEFBrowser current=effectBrowser;
        effectBrowser=null;effectExpiresAt=0L;
        if(current!=null){CinemaAudioManager.unregisterBrowser(current);try{current.close();}catch(RuntimeException exception){MpsqCameraClient.LOGGER.debug("MPSQ-Effektbrowser ließ sich nicht schließen",exception);}finally{CinemaBrowserManager.requestGameMouseRestore();}}
    }
}
