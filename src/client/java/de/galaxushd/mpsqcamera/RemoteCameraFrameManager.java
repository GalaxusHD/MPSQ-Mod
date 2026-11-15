package de.galaxushd.mpsqcamera;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import de.galaxushd.mpsqcamera.compat.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Stable camera-frame transport.
 *
 * <p>Only Minecraft's already completed normal render is captured. This is
 * intentional: rendering the world a second time while Minecraft is drawing
 * caused camera jumps, hand artefacts and broken freecam movement.</p>
 */
public final class RemoteCameraFrameManager {
    // Capturing a framebuffer forces a GPU read-back. At ten 540p frames per
    // second this noticeably stalls the player who operates the camera,
    // particularly while a direct R2 upload is also active. Five FPS at 360p
    // remains sufficiently live for an in-world monitor while keeping camera
    // movement responsive.
    private static final long FRAME_INTERVAL_MS = 200L;
    // A bodycam is captured from its wearer's normal game render. Capturing it
    // Bodycams use the same live rate as a static camera. The expensive image
    // transformation is performed asynchronously further below, so the wearer
    // does not pay the PNG-processing cost in the render tick.
    private static final long BODYCAM_INTERVAL_MS = 200L;
    // 360p halves the PNG and texture-upload work compared with 540p.
    // The actual send rate remains bounded by the completed upload, so a slow
    // connection cannot queue an unlimited number of screenshots.
    private static final int STREAM_WIDTH = 640;
    private static final int STREAM_HEIGHT = 360;
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Map<UUID, Identifier> TEXTURE_IDS = new ConcurrentHashMap<>();
    private static final Map<UUID, DynamicTexture> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_REQUEST_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> FRAME_URLS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> FRAME_URL_EXPIRY_MS = new ConcurrentHashMap<>();
    private static final Set<UUID> URL_REQUESTS_IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> FRAME_DOWNLOADS_IN_FLIGHT = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> OWN_BODYCAMS = new HashSet<>();

    private static UUID providerCamera;
    private static Runnable snapshotCompleteCallback;
    private static long lastPublishMs;
    private static long lastPresenceMs;
    private static long lastBodycamRefreshMs;
    private static long lastBodycamPublishMs;
    private static boolean publishing;
    private static boolean bodycamRefreshInFlight;

    private RemoteCameraFrameManager() { }

    public static void initialize() {
        WorldRenderEvents.END.register(context -> captureProviderFrame());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> clear());
    }

    /** Starts transmission from the camera view the player explicitly entered. */
    public static void startPublishing(UUID cameraId) {
        providerCamera = cameraId;
        lastPublishMs = 0L;
        lastPresenceMs = System.currentTimeMillis();
        announceCamera(cameraId, "start");
    }

    /** Stops static-camera transmission immediately after leaving the view. */
    public static void stopPublishing() {
        if (providerCamera != null) announceCamera(providerCamera, "stop");
        providerCamera = null;
    }

    private static void announceCamera(UUID cameraId, String action) {
        if (!MpsqApiClient.isReady()) return;
        JsonObject body = new JsonObject();
        body.addProperty("cameraId", cameraId.toString());
        body.addProperty("action", action);
        MpsqApiClient.post("/team/camera-events", body).exceptionally(ignored -> null);
    }

    /**
     * Sends one final frame from the camera's saved standard view. The caller
     * keeps the camera entity active until this callback runs, so no player
     * hand or HUD can end up in the stored picture.
     */
    public static void captureFinalSnapshot(UUID cameraId, Runnable onComplete) {
        providerCamera = cameraId;
        snapshotCompleteCallback = onComplete;
        lastPublishMs = 0L;
    }

    public static Identifier texture(UUID cameraId) {
        if (cameraId == null || !TeamVisibilitySettings.inArea()) return null;
        requestLatestFrame(cameraId);
        return TEXTURE_IDS.get(cameraId);
    }

    private static void captureProviderFrame() {
        Minecraft client = Minecraft.getInstance();
        if(!TeamVisibilitySettings.inArea()){if(snapshotCompleteCallback!=null)finishSnapshot();stopPublishing();return;}refreshMyBodycams();
        if (providerCamera != null && LocalCameraStore.find(providerCamera)
                .map(camera -> CameraSafety.isStaticCameraBlocked(client, camera)).orElse(false)) {
            // Never encode or upload a frame that was captured through terrain.
            if (snapshotCompleteCallback != null) {
                finishSnapshot();
            } else {
                stopPublishing();
            }
            return;
        }
        boolean hasBodycams;
        synchronized (OWN_BODYCAMS) { hasBodycams = !OWN_BODYCAMS.isEmpty(); }
        if ((providerCamera == null && !hasBodycams) || publishing || client.gameRenderer.mainRenderTarget() == null) return;
        long now = System.currentTimeMillis();
        if (providerCamera != null && now - lastPresenceMs >= 5_000L) {
            lastPresenceMs = now;
            announceCamera(providerCamera, "start");
        }
        long interval = providerCamera != null ? FRAME_INTERVAL_MS : BODYCAM_INTERVAL_MS;
        if (now - lastPublishMs < interval) return;
        if (providerCamera == null && now - lastBodycamPublishMs < BODYCAM_INTERVAL_MS) return;
        lastPublishMs = now;
        if (providerCamera == null) lastBodycamPublishMs = now;
        publishing = true;

        Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), 1, image -> {
            Set<UUID> targets;
            synchronized (OWN_BODYCAMS) {
                targets = new HashSet<>(OWN_BODYCAMS);
            }
            if (providerCamera != null) targets.add(providerCamera);
            if (targets.isEmpty()) {
                image.close();
                publishing = false;
                return;
            }
            // The NativeImage now belongs solely to this background task.
            // Resizing, PNG compression and the temporary file operation used
            // to happen on the source player's render thread. That was the
            // cause of bodycam wearer hitches. The receiving viewer still does
            // the normal texture update, which is the expected client cost.
            CompletableFuture.supplyAsync(() -> {
                NativeImage streamImage = null;
                try {
                    streamImage = resizeForStream(image);
                    return encode(streamImage);
                } catch (IOException | RuntimeException exception) {
                    throw new IllegalStateException("Kamera-Bild konnte nicht kodiert werden", exception);
                } finally {
                    if (streamImage != null) streamImage.close();
                    image.close();
                }
            }).thenCompose(png -> {
                CompletableFuture<?>[] uploads = targets.stream()
                        .map(cameraId -> MpsqApiClient.postCameraFrame(cameraId, png))
                        .toArray(CompletableFuture[]::new);
                return CompletableFuture.allOf(uploads);
            }).whenComplete((ignored, error) -> {
                publishing = false;
                if (error != null) MpsqCameraClient.LOGGER.warn("Kamera-Bild konnte nicht hochgeladen werden", error);
                finishSnapshot();
            });
        });
    }

	private static void finishSnapshot() {
		Runnable callback = snapshotCompleteCallback;
		if (callback == null) return;
		snapshotCompleteCallback = null;
		providerCamera = null;
		Minecraft.getInstance().execute(callback);
	}

    private static void refreshMyBodycams() {
        if (!MpsqApiClient.isReady() || bodycamRefreshInFlight) return;
        long now = System.currentTimeMillis();
        if (now - lastBodycamRefreshMs < 10_000L) return;
        lastBodycamRefreshMs = now;
        bodycamRefreshInFlight = true;
        MpsqApiClient.loadMyBodycamIds().whenComplete((ids, error) -> {
            bodycamRefreshInFlight = false;
            if (error == null && ids != null) {
                synchronized (OWN_BODYCAMS) {
                    OWN_BODYCAMS.clear();
                    OWN_BODYCAMS.addAll(ids);
                }
            }
        });
    }

    private static byte[] encode(NativeImage image) throws IOException {
        Path file = Files.createTempFile("mpsq-camera-frame-", ".png");
        try {
            image.writeToFile(file);
            return Files.readAllBytes(file);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static NativeImage resizeForStream(NativeImage source) {
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        double targetRatio = (double) STREAM_WIDTH / STREAM_HEIGHT;
        double sourceRatio = (double) sourceWidth / sourceHeight;
        int cropX = 0;
        int cropY = 0;
        int cropWidth = sourceWidth;
        int cropHeight = sourceHeight;
        if (sourceRatio > targetRatio) {
            cropWidth = (int) Math.round(sourceHeight * targetRatio);
            cropX = (sourceWidth - cropWidth) / 2;
        } else if (sourceRatio < targetRatio) {
            cropHeight = (int) Math.round(sourceWidth / targetRatio);
            cropY = (sourceHeight - cropHeight) / 2;
        }
        NativeImage target = new NativeImage(STREAM_WIDTH, STREAM_HEIGHT, false);
        source.resizeSubRectTo(cropX, cropY, cropWidth, cropHeight, target);
        return target;
    }

    private static void requestLatestFrame(UUID cameraId) {
        long now=System.currentTimeMillis();
        if(now-LAST_REQUEST_MS.getOrDefault(cameraId,0L)<FRAME_INTERVAL_MS || !LocalCameraFrameStore.hasFrame(cameraId))return;
        LAST_REQUEST_MS.put(cameraId,now);
        if(!FRAME_DOWNLOADS_IN_FLIGHT.add(cameraId))return;
        LocalCameraFrameStore.load(cameraId)
            .thenAccept(image->Minecraft.getInstance().execute(()->install(cameraId,image)))
            .whenComplete((ignored,error)->FRAME_DOWNLOADS_IN_FLIGHT.remove(cameraId));
    }

    private static CompletableFuture<NativeImage> rememberAndDownload(UUID cameraId, JsonElement result) {
        if (result == null || !result.isJsonObject() || !result.getAsJsonObject().has("url")) {
            throw new IllegalStateException("Kamera ist offline");
        }
        String url = result.getAsJsonObject().get("url").getAsString();
        long validForSeconds = result.getAsJsonObject().has("expiresIn")
                ? result.getAsJsonObject().get("expiresIn").getAsLong() : 60L;
        // Renew before the signed Storage URL reaches its expiry.
        FRAME_URLS.put(cameraId, url);
        // R2 provides a longer-lived direct link.  The actual frame bytes do
        // not pass through Supabase; only this small permission check is
        // renewed occasionally.
        FRAME_URL_EXPIRY_MS.put(cameraId, System.currentTimeMillis()
                + Math.max(10L, validForSeconds - 15L) * 1_000L);
        return downloadFrame(url).whenComplete((ignored, error) -> {
            if (error != null) invalidateFrameUrl(cameraId);
        });
    }

    private static NativeImage decodeInlineFrame(JsonElement result) {
        if (result == null || !result.isJsonObject()) {
            throw new IllegalStateException("Kamera-Bild konnte nicht geladen werden");
        }
        JsonObject object = result.getAsJsonObject();
        if (!object.has("pngBase64") || object.get("pngBase64").isJsonNull()) {
            throw new IllegalStateException("Kamera-Bild ist nicht verfügbar");
        }
        try {
            byte[] bytes = java.util.Base64.getDecoder().decode(object.get("pngBase64").getAsString());
            return NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Kamera-Bild ist ungültig", exception);
        }
    }

    private static CompletableFuture<NativeImage> downloadFrame(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Cache-Control", "no-cache, no-store, max-age=0")
                .header("Pragma", "no-cache")
                .GET()
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new IllegalStateException("Kamera-Bild konnte nicht geladen werden: " + response.statusCode());
                    }
                    return response.body();
                })
                .thenApply(bytes -> {
                    try {
                        return NativeImage.read(new ByteArrayInputStream(bytes));
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                });
    }

    private static void invalidateFrameUrl(UUID cameraId) {
        FRAME_URLS.remove(cameraId);
        FRAME_URL_EXPIRY_MS.remove(cameraId);
    }

    private static void install(UUID cameraId, NativeImage image) {
        DynamicTexture texture = TEXTURES.get(cameraId);
        if (texture == null) {
            texture = new DynamicTexture(() -> "mpsq-camera-" + cameraId, image);
            Identifier id = Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID, "remote_camera/" + cameraId);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            texture.upload();
            TEXTURES.put(cameraId, texture);
            TEXTURE_IDS.put(cameraId, id);
        } else {
            texture.setPixels(image);
            texture.upload();
        }
    }

    private static void removeTexture(UUID cameraId) {
        DynamicTexture texture = TEXTURES.remove(cameraId);
        if (texture != null) texture.close();
        TEXTURE_IDS.remove(cameraId);
    }

    private static void clear() {
        stopPublishing();
        snapshotCompleteCallback = null;
        TEXTURES.values().forEach(DynamicTexture::close);
        TEXTURES.clear();
        TEXTURE_IDS.clear();
        LAST_REQUEST_MS.clear();
        FRAME_URLS.clear();
        FRAME_URL_EXPIRY_MS.clear();
        URL_REQUESTS_IN_FLIGHT.clear();
        FRAME_DOWNLOADS_IN_FLIGHT.clear();
        OWN_BODYCAMS.clear();
    }
}
