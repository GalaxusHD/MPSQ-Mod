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
import net.minecraft.client.render.OutlineVertexConsumerProvider;
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
public final class MpsqNpcSkinRenderer {
    record Skin(Identifier texture, boolean slim) {}
    public record JointPose(float leftArmX,float leftArmY,float leftArmZ,float rightArmX,float rightArmY,float rightArmZ) {}

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private static final Map<String, Skin> SKINS = new HashMap<>();
    private static final Set<String> LOADING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<JointPose> ACTIVE_NPC_POSE = new ThreadLocal<>();

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
                byte[] bytes=MpsqLocalWorldStore.readAsset(url,2_250_000);
                if(bytes==null){HttpResponse<InputStream> response = HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());try(InputStream stream=response.body()){if(response.statusCode()!=200)throw new IOException("Skin nicht verfügbar");bytes=stream.readNBytes(2_250_001);if(bytes.length>2_250_000)throw new IOException("Ungültige Skin-Datei");MpsqLocalWorldStore.writeAsset(url,bytes);}}
                if (bytes.length < 24 || bytes.length > 2_250_000) throw new IOException("Ungültige Skin-Datei");
                int width = java.nio.ByteBuffer.wrap(bytes).getInt(16);
                int height = java.nio.ByteBuffer.wrap(bytes).getInt(20);
                if (width != 64 || (height != 64 && height != 32)) throw new IOException("Skin muss 64 × 64 oder 64 × 32 Pixel groß sein");
                return bytes;
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

    static void render(PlayerEntityRenderState state, double x, double y, double z, float scale, String glowColor,
                       net.minecraft.client.util.math.MatrixStack matrices,
                       VertexConsumerProvider consumers, int light) {
        MinecraftClient client = MinecraftClient.getInstance();
        matrices.push();
        matrices.translate(x, y, z);
        float phase=state.age*0.075f;
        float armSwing=(float)Math.toRadians(Math.sin(phase)*2.5f);
        ACTIVE_NPC_POSE.set(new JointPose(armSwing,0,0,-armSwing,0,0));
        try {
            int outlineColor = glowRgb(glowColor);
            matrices.scale(scale, scale, scale);
            if (outlineColor != 0 && consumers instanceof VertexConsumerProvider.Immediate immediate) {
                OutlineVertexConsumerProvider outlineConsumers = new OutlineVertexConsumerProvider(immediate);
                outlineConsumers.setColor((outlineColor >> 16) & 255, (outlineColor >> 8) & 255, outlineColor & 255, 255);
                state.hasOutline = true;
                client.getEntityRenderDispatcher().render(state, 0.0, 0.0, 0.0, matrices, outlineConsumers, light);
                outlineConsumers.draw();
            }
            state.hasOutline = false;
            client.getEntityRenderDispatcher().render(state, 0.0, 0.0, 0.0, matrices, consumers, light);
        } finally {
            ACTIVE_NPC_POSE.remove();
            state.hasOutline = false;
            matrices.pop();
        }
    }

    private static int glowRgb(String color) {
        return switch (color) {
            case "white" -> 0xFFFFFF; case "orange" -> 0xFF9800; case "magenta" -> 0xFF00FF;
            case "light_blue" -> 0x55AAFF; case "yellow" -> 0xFFFF00; case "lime" -> 0x55FF55;
            case "pink" -> 0xFF88BB; case "gray" -> 0x555555; case "light_gray" -> 0xAAAAAA;
            case "cyan" -> 0x00FFFF; case "purple" -> 0xAA00FF; case "blue" -> 0x5555FF;
            case "brown" -> 0x996633; case "green" -> 0x00AA00; case "red" -> 0xFF3333;
            case "black" -> 0x111111; default -> 0;
        };
    }
    private static String key(String url, boolean slim) {
        return url + (slim ? "#slim" : "#wide");
    }

    static PlayerEntityRenderState createState(Skin skin, float yaw, float headYaw, float pitch, float age) {
        PlayerEntityRenderState state = new PlayerEntityRenderState();
        state.entityType = EntityType.PLAYER;
        state.width = 0.6f;
        state.height = 1.8f;
        state.standingEyeHeight = 1.62f;
        state.skinTextures = new SkinTextures(skin.texture(), "", null, null,
                skin.slim() ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE, false);
        state.bodyYaw = yaw;
        state.relativeHeadYaw = headYaw;
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
        return state;
    }

    public static JointPose activeNpcPose(){return ACTIVE_NPC_POSE.get();}

    static void clear(MinecraftClient client) {
        for (Skin skin : SKINS.values()) client.getTextureManager().destroyTexture(skin.texture());
        SKINS.clear();
        LOADING.clear();
    }
}
