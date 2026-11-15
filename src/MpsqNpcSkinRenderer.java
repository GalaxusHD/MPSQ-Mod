package de.galaxushd.mpsqcamera;

import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.client.Minecraft;
import de.galaxushd.mpsqcamera.compat.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

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
        if("self".equals(url)&&Minecraft.getInstance().player!=null){var skin=Minecraft.getInstance().player.getSkin();return new Skin(skin.body().texturePath(),skin.model()==net.minecraft.world.entity.player.PlayerModelType.SLIM);}
        return SKINS.get(key(url, slim));
    }

    static void load(String url, int generation, boolean slim) {
        if("self".equals(url))return;
        String key = key(url, slim);
        if (SKINS.containsKey(key) || !LOADING.add(key)) return;
        CompletableFuture.supplyAsync(() -> {
            try {
                byte[] bytes=MpsqLocalWorldStore.readCachedSkin(url,2_250_000);
                String bundled=MpsqBuiltinNpcCatalog.skinResource(url);
                if(bundled!=null)try(var in=MpsqNpcSkinRenderer.class.getResourceAsStream(bundled)){if(in==null)throw new IOException("Mod-Skin fehlt");bytes=in.readAllBytes();}
                URI uri = URI.create(url);
                URI api = URI.create(MpsqApiClient.API_URL);
                if (bytes==null && (!"https".equals(api.getScheme()) || !"https".equals(uri.getScheme()) || !java.util.Objects.equals(api.getHost(),uri.getHost()))) {
                    throw new IOException("Unzulässige Skin-Quelle");
                }

                if(bytes==null){HttpResponse<InputStream> response = HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());try(InputStream stream=response.body()){if(response.statusCode()!=200)throw new IOException("Skin nicht verfügbar");bytes=stream.readNBytes(2_250_001);if(bytes.length>2_250_000)throw new IOException("Ungültige Skin-Datei");MpsqLocalWorldStore.writeAsset(url,bytes);}}
                if (bytes.length < 24 || bytes.length > 2_250_000) throw new IOException("Ungültige Skin-Datei");
                int width = java.nio.ByteBuffer.wrap(bytes).getInt(16);
                int height = java.nio.ByteBuffer.wrap(bytes).getInt(20);
                if (width != 64 || (height != 64 && height != 32)) throw new IOException("Skin muss 64 × 64 oder 64 × 32 Pixel groß sein");
                return bytes;
            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }).whenComplete((bytes, error) -> Minecraft.getInstance().execute(() -> {
            if (!MpsqAccessoryRenderer.isCurrentGeneration(generation)) return;
            LOADING.remove(key);
            if (error != null) {
                MpsqCameraClient.LOGGER.warn("NPC-Skin konnte nicht geladen werden: {}", url, error);
                return;
            }
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                boolean detectedSlim=url.equals("builtin://mpsq/skin/sr_offizier")||MpsqSkinImages.slim(image,slim);
                image = MpsqSkinImages.normalize(image);
                Identifier textureId = Identifier.fromNamespaceAndPath("mpsqcamera", "npc_skin/" + UUID.randomUUID());
                DynamicTexture texture = new DynamicTexture(() -> "MPSQ NPC Skin", image);
                Minecraft.getInstance().getTextureManager().register(textureId, texture);
                texture.upload();
                SKINS.put(key, new Skin(textureId, detectedSlim));
            } catch (Exception e) {
                MpsqCameraClient.LOGGER.warn("NPC-Skin ist ungültig: {}", url, e);
            }
        }));
    }

    static void render(AvatarRenderState state, double x, double y, double z, float scale,
                       com.mojang.blaze3d.vertex.PoseStack matrices,
                       MultiBufferSource consumers, int light, int outlineColor) {
        Minecraft client = Minecraft.getInstance();
        var camera = consumers.camera().pos;
        MpsqSkinLayersCompat.position(state,x+camera.x,y+camera.y,z+camera.z);
        matrices.pushPose();
        matrices.translate(x, y, z);
        matrices.scale(scale, scale, scale);
        state.lightCoords = light;
        if (state.appearsGlowing()) state.outlineColor = 0xFF000000 | outlineColor;
        MpsqIsolatedSkinRenderer.render(state,matrices,consumers,false);
        matrices.popPose();
    }

    private static String key(String url, boolean slim) {
        return url + (slim ? "#slim" : "#wide");
    }

    static AvatarRenderState createState(Skin skin, float yaw, float pitch, float age, boolean glowing) {
        AvatarRenderState state = new AvatarRenderState();
        state.entityType = Minecraft.getInstance().player.getType();
        state.boundingBoxWidth = 0.6f;
        state.boundingBoxHeight = 1.8f;
        state.eyeHeight = 1.62f;
        state.skin = new PlayerSkin(new net.minecraft.core.ClientAsset.ResourceTexture(skin.texture(), skin.texture()), null, null,
                skin.slim() ? net.minecraft.world.entity.player.PlayerModelType.SLIM : net.minecraft.world.entity.player.PlayerModelType.WIDE, false);
        state.bodyRot = yaw;
        state.yRot = 0.0f;
        state.xRot = pitch;
        state.ageInTicks = age;
        state.scale = 1.0f;
        state.ageScale = 1.0f;
        state.walkAnimationPos = 0.0f;
        state.walkAnimationSpeed = 0.0f;
        state.pose = Pose.STANDING;
        state.isCrouching = false;
        state.mainArm = HumanoidArm.RIGHT;
        state.useItemHand = InteractionHand.MAIN_HAND;
        state.headEquipment = ItemStack.EMPTY;
        state.chestEquipment = ItemStack.EMPTY;
        state.legsEquipment = ItemStack.EMPTY;
        state.feetEquipment = ItemStack.EMPTY;
        state.showHat = true;
        state.showJacket = true;
        state.showLeftPants = true;
        state.showRightPants = true;
        state.showLeftSleeve = true;
        state.showRightSleeve = true;
        state.showCape = false;
        state.nameTag = null;
        state.scoreText = null;
        de.galaxushd.mpsqcamera.compat.AvatarIdentity.set(state, null);
        state.outlineColor = glowing ? 0xFFFFFFFF : 0;
        return state;
    }

    /** Creates an NPC player state with separately controlled body and head yaw. */
    static AvatarRenderState createState(Skin skin, float bodyYaw, float relativeHeadYaw, float pitch, float age, boolean glowing) {
        AvatarRenderState state = createState(skin, bodyYaw, pitch, age, glowing);
        state.yRot = relativeHeadYaw;
        return state;
    }
    static void clear(Minecraft client) {
        for (Skin skin : SKINS.values()) client.getTextureManager().release(skin.texture());
        MpsqSkinLayersCompat.clear();
        SKINS.clear();
        LOADING.clear();
    }
}
