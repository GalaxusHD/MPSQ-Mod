package de.galaxushd.mpsqcamera;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Plays uploaded MP3/MP4 assets through the MCEF audio bridge used by cinema screens. */
public final class MpsqMediaAudioManager {
    private static MCEFBrowser browser;
    private static long generation;

    private MpsqMediaAudioManager() { }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null && browser != null) stop();
        });
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
                    openPlayer(urls);
                }));
    }

    private static void openPlayer(List<String> urls) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !CinemaBrowserManager.ensureAudioReady()) return;
        JsonArray json = new JsonArray();
        urls.forEach(json::add);
        // A tiny off-screen Chromium page owns the HTML audio element; PCM is
        // routed back into Minecraft by the existing MCEF/OpenAL bridge.
        String html = "<!doctype html><meta charset=utf-8><audio id=a autoplay></audio><script>const q=" + json
                + ";let i=0,a=document.getElementById('a');a.onended=()=>{i++;if(i<q.length){a.src=q[i];a.play()}};a.onerror=()=>{i++;if(i<q.length){a.src=q[i];a.play()}};a.src=q[0];a.play().catch(()=>{});</script>";
        String page = "data:text/html;base64," + Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8));
        try {
            browser = MCEF.createBrowser(page, false);
            browser.resize(64, 64);
        } catch (RuntimeException exception) {
            browser = null;
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

    public static void stop() {
        generation++;
        MCEFBrowser current = browser;
        browser = null;
        if (current != null) {
            try { current.close(); }
            catch (RuntimeException exception) { MpsqCameraClient.LOGGER.debug("MPSQ-Medienbrowser ließ sich nicht schließen", exception); }
        }
    }
}
