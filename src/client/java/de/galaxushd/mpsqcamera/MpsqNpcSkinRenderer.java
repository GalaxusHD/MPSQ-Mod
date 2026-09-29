package de.galaxushd.mpsqcamera;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

/** Loads uploaded player skins and renders them through Minecraft's player renderer. */
final class MpsqNpcSkinRenderer {
    record Skin(Identifier texture, boolean slim) {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<String, Skin> SKINS = new HashMap<>();
    private static final Set<String> LOADING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private MpsqNpcSkinRenderer() {}

    static Skin get(String url) {
        return SKINS.get(url);
    }

    static Skin get(String url, boolean slim) {
        return SKINS.get(key(url, slim));
    }

    static void load(String url, int generation, boolean slim) {
        String key = key(url, slim);
        if (SKINS.containsKey(key) || !LOADING.add(key)) return;
        CompletableFuture.supplyAsync(() -> {
            try {
                URI uri = URI.create(url);
                URI api = URI.create(MpsqApiClient.API_URL);
                if (!"https".equals(uri.getScheme()) || !api.getHost().equals(uri.getHost())) {
                    throw new IOException("Unzulässige Skin-Quelle");
                }
                HttpResponse<InputStream> response = HTTP.send(
                        HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream stream = response.body()) {
                    if (response.statusCode() != 200) throw new IOException("Skin nicht verfügbar");
                    byte[] bytes = stream.readNBytes(2_250_001);
                    if (bytes.length < 24 || bytes.length > 2_250_000) throw new IOException("Ungültige Skin-Datei");
                    int width = java.nio.ByteBuffer.wrap(bytes).getInt(16);
                    int height = java.nio.ByteBuffer.wrap(bytes).getInt(20);
                    if (width != 64 || (height != 64 && height != 32)) {
                        throw new IOException("Skin muss 64 × 64 oder 64 × 32 Pixel groß sein");
                    }
                    return bytes;
                }
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }).whenComplete((bytes, error) -> MinecraftClient.getInstance().execute(() -> {
            if (!MpsqAccessoryRenderer.isCurrentGeneration(generation)) return;
            LOADING.remove(key);
            if (error != null) {
                MpsqCameraClient.LOGGER.warn("NPC-Skin konnte nicht geladen werden: {}", url, error);
                return;
            }
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                if (image.getHeight() == 32) {
                    NativeImage expanded = new NativeImage(64, 64, true);
                    for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) {
                        expanded.setColorArgb(x, y, image.getColorArgb(x, y));
                    }
                    image.close();
                    image = expanded;
                }
                Identifier textureId = Identifier.of("mpsqcamera", "npc_skin/" + UUID.randomUUID());
                NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "MPSQ NPC Skin", image);
                MinecraftClient.getInstance().getTextureManager().registerTexture(textureId, texture);
                texture.upload();
                SKINS.put(key, new Skin(textureId, slim));
            } catch (Exception e) {
                MpsqCameraClient.LOGGER.warn("NPC-Skin ist ungültig: {}", url, e);
            }
        }));
    }

    static void render(PlayerEntityRenderState state, double x, double y, double z, float scale,
                       net.minecraft.client.util.math.MatrixStack matrices,
                       VertexConsumerProvider consumers, int light, int outlineColor) {
        MinecraftClient client = MinecraftClient.getInstance();
        matrices.push();
        matrices.translate(x, y, z);
        matrices.scale(scale, scale, scale);
        boolean glowing = state.hasOutline;
        state.hasOutline = false;
        client.getEntityRenderDispatcher().render(state, 0.0, 0.0, 0.0, matrices, consumers, light);
        if (glowing) {
            state.hasOutline = true;
            var outline = client.getBufferBuilders().getOutlineVertexConsumers();
            outline.setColor((outlineColor >> 16) & 255, (outlineColor >> 8) & 255,
                    outlineColor & 255, 255);
            client.getEntityRenderDispatcher().render(state, 0.0, 0.0, 0.0, matrices, outline, light);
            outline.draw();
        }
        state.hasOutline = false;
        matrices.pop();
    }

    private static String key(String url, boolean slim) {
        return url + (slim ? "#slim" : "#wide");
    }

    static PlayerEntityRenderState createState(Skin skin, float yaw, float pitch, float age, boolean glowing) {
        PlayerEntityRenderState state = new PlayerEntityRenderState();
        state.entityType = EntityType.PLAYER;
        state.width = 0.6f;
        state.height = 1.8f;
        state.standingEyeHeight = 1.62f;
        state.skinTextures = new SkinTextures(skin.texture(), "", null, null,
                skin.slim() ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE, false);
        state.bodyYaw = yaw;
        state.relativeHeadYaw = 0.0f;
        state.pitch = pitch;
        state.age = age;
        state.baseScale = 1.0f;
        state.ageScale = 1.0f;
        state.limbSwingAnimationProgress = 0.0f;
        state.limbSwingAmplitude = 0.0f;
        state.pose = EntityPose.STANDING;
        state.isInSneakingPose = false;
        state.preferredArm = Arm.RIGHT;
        state.activeHand = Hand.MAIN_HAND;
        state.equippedHeadStack = ItemStack.EMPTY;
        state.equippedChestStack = ItemStack.EMPTY;
        state.equippedLegsStack = ItemStack.EMPTY;
        state.equippedFeetStack = ItemStack.EMPTY;
        state.hatVisible = true;
        state.jacketVisible = true;
        state.leftPantsLegVisible = true;
        state.rightPantsLegVisible = true;
        state.leftSleeveVisible = true;
        state.rightSleeveVisible = true;
        state.capeVisible = false;
        state.displayName = null;
        state.playerName = null;
        state.name = null;
        state.hasOutline = glowing;
        return state;
    }

    static void clear(MinecraftClient client) {
        for (Skin skin : SKINS.values()) client.getTextureManager().destroyTexture(skin.texture());
        SKINS.clear();
        LOADING.clear();
    }
}
