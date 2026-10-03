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

    /** Resolves an uploaded MP3/MP4 ID first, then falls back to a Minecraft sound ID. */
    public static void playAuto(String id) {
        stop();
        if (id == null) return;
        String value=id.trim();
        if (value.isEmpty() || value.length()>128 || !value.matches("[a-zA-Z0-9_.:/-]+")) return;
        if (value.contains(":")) {
            Identifier identifier=Identifier.tryParse(value);
            if (identifier!=null) { MpsqAudioManager.startPlaylist("MPSQ", List.of(identifier.toString())); return; }
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
            Identifier identifier=Identifier.tryParse(value);
            if(identifier!=null) MpsqAudioManager.startPlaylist("MPSQ",List.of(identifier.toString()));
            else MpsqCameraClient.LOGGER.warn("MPSQ-Sound-ID ist weder eine verfügbare Datei noch eine Minecraft-Sound-ID: {}",value);
        }));
    }

    public static void play(String type, List<String> assetIds) {
        stop();
        if (!(type.equals("mp3") || type.equals("mp4")) || assetIds == null || assetIds.isEmpty() || assetIds.size() > 100) return;
        long request = ++generation;
        List<java.util.concurrent.CompletableFuture<String>> lookups = new ArrayList<>();
        for (String id : assetIds) {
            if (id == null || !id.matches("[a-zA-Z0-9_-]{1,64}")) return;
            lookups.add(MpsqApiClient.get("/sounds/" + id + "?type=" + type).thenApply(data -> {
                JsonObject result = data.getAsJsonObject();
                String url = result.has("url") ? result.get("url").getAsString() : "";
                return safeHttps(url) ? url : "";
            }));
        }
        java.util.concurrent.CompletableFuture.allOf(lookups.toArray(java.util.concurrent.CompletableFuture[]::new))
                .whenComplete((ignored, error) -> MinecraftClient.getInstance().execute(() -> {
                    if (request != generation || error != null) {
                        if (error != null) MpsqCameraClient.LOGGER.warn("MPSQ-Audiodatei konnte nicht geladen werden", error);
                        return;
                    }
                    List<String> urls = lookups.stream().map(java.util.concurrent.CompletableFuture::join).toList();
                    if (urls.isEmpty() || urls.stream().anyMatch(String::isBlank)) {
                        MpsqCameraClient.LOGGER.warn("Mindestens eine MPSQ-Audiodatei-ID ist ungültig oder nicht verfügbar.");
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
            try { current.close(); }
            catch (RuntimeException exception) { MpsqCameraClient.LOGGER.debug("MPSQ-Medienbrowser ließ sich nicht schließen", exception); }
            finally { CinemaBrowserManager.requestGameMouseRestore(); }
        }
    }

    private static void closeEffectBrowser() {
        MCEFBrowser current=effectBrowser;
        effectBrowser=null;effectExpiresAt=0L;
        if(current!=null)try{current.close();}catch(RuntimeException exception){MpsqCameraClient.LOGGER.debug("MPSQ-Effektbrowser ließ sich nicht schließen",exception);}finally{CinemaBrowserManager.requestGameMouseRestore();}
    }
}
