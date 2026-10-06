package de.galaxushd.mpsqcamera;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

/** Renders the selected Mini-Me skin as a small local companion. */
final class MpsqPetRenderer {
    private static final float PET_SCALE = 0.42f;
    private static final double FOLLOW_DISTANCE = 1.15;
    private static final Map<String, MpsqNpcSkinRenderer.Skin> LEGACY_SKINS = new HashMap<>();

    private static net.minecraft.client.world.ClientWorld trackedWorld;
    private static double petX;
    private static double petY;
    private static double petZ;
    private static boolean positioned;
    private static String trackedPetId;

    private MpsqPetRenderer() { }

    static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(MpsqPetRenderer::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            var player = client.player;
            var matrices = context.matrixStack();
            var consumers = context.consumers();
            if (player == null || client.world == null || matrices == null || consumers == null
                    || !TeamVisibilitySettings.visible() || !positioned || trackedWorld != client.world) return;

            MpsqPetCatalog.Pet pet = selectedPet();
            if (pet == null || pet.issuerRole() != null) return;
            MpsqNpcSkinRenderer.Skin skin = skinFor(pet);
            if (skin == null) return;

            var camera = context.camera().getPos();
            if (camera.squaredDistanceTo(petX, petY, petZ) > 4096) return;
            float yaw = player.getYaw();
            float age = (System.currentTimeMillis() % 1_000_000L) / 50.0f;
            var state = MpsqNpcSkinRenderer.createState(skin, yaw, 0.0f, age, false);
            var velocity = player.getVelocity();
            float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            state.limbSwingAnimationProgress = age * (speed > 0.02f ? 0.18f : 0.035f);
            state.limbSwingAmplitude = Math.min(0.55f, speed * 2.2f);
            int light = WorldRenderer.getLightmapCoordinates(client.world,
                    net.minecraft.util.math.BlockPos.ofFloored(petX, petY, petZ));
            MpsqNpcSkinRenderer.render(state, petX - camera.x, petY - camera.y, petZ - camera.z,
                    PET_SCALE, matrices, consumers, light, 0xFFFFFFFF);
        });
    }

    private static void tick(MinecraftClient client) {
        var player = client.player;
        MpsqPetCatalog.Pet pet = selectedPet();
        if (player == null || client.world == null || pet == null || pet.issuerRole() != null
                || !TeamVisibilitySettings.visible()) {
            positioned = false;
            trackedWorld = null;
            trackedPetId = null;
            return;
        }

        float yaw = player.getYaw();
        double radians = Math.toRadians(yaw);
        double targetX = player.getX() + Math.sin(radians) * FOLLOW_DISTANCE;
        double targetY = player.getY();
        double targetZ = player.getZ() - Math.cos(radians) * FOLLOW_DISTANCE;
        boolean reset = !positioned || trackedWorld != client.world || !pet.id().equals(trackedPetId)
                || squaredDistance(petX, petY, petZ, targetX, targetY, targetZ) > 64.0;
        if (reset) {
            petX = targetX;
            petY = targetY;
            petZ = targetZ;
            positioned = true;
            trackedWorld = client.world;
            trackedPetId = pet.id();
        } else {
            petX += (targetX - petX) * 0.28;
            petY += (targetY - petY) * 0.28;
            petZ += (targetZ - petZ) * 0.28;
        }
    }

    private static MpsqPetCatalog.Pet selectedPet() {
        String id = MpsqPetSelectionStore.selectedId();
        return id == null ? null : MpsqPetCatalog.byId(id);
    }

    static MpsqNpcSkinRenderer.Skin skinFor(MpsqPetCatalog.Pet pet) {
        MinecraftClient client = MinecraftClient.getInstance();
        if ("__player__".equals(pet.texture())) {
            if (client.player == null) return null;
            SkinTextures textures = client.player.getSkinTextures();
            return new MpsqNpcSkinRenderer.Skin(textures.texture(),
                    textures.model() == SkinTextures.Model.SLIM);
        }
        if (pet.textureHeight() == 64) return new MpsqNpcSkinRenderer.Skin(pet.textureId(), false);
        MpsqNpcSkinRenderer.Skin cached = LEGACY_SKINS.get(pet.id());
        if (cached != null) return cached;
        try (InputStream stream = MpsqPetRenderer.class.getResourceAsStream(
                "/assets/mpsqcamera/textures/pets/" + pet.texture() + ".png")) {
            if (stream == null) return null;
            NativeImage source = NativeImage.read(stream);
            if (source.getWidth() != 64 || source.getHeight() != 32) {
                source.close();
                return null;
            }
            NativeImage expanded = new NativeImage(64, 64, true);
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 64; x++) expanded.setColorArgb(x, y, source.getColorArgb(x, y));
            }
            source.close();
            Identifier texture = Identifier.of(MpsqCameraClient.MOD_ID, "pet_runtime/" + UUID.randomUUID());
            NativeImageBackedTexture registered = new NativeImageBackedTexture(() -> "MPSQ Pet Skin", expanded);
            MinecraftClient.getInstance().getTextureManager().registerTexture(texture, registered);
            registered.upload();
            MpsqNpcSkinRenderer.Skin skin = new MpsqNpcSkinRenderer.Skin(texture, false);
            LEGACY_SKINS.put(pet.id(), skin);
            return skin;
        } catch (Exception error) {
            MpsqCameraClient.LOGGER.warn("Mini-Me-Skin konnte nicht geladen werden: {}", pet.id(), error);
            return null;
        }
    }

    private static double squaredDistance(double x1, double y1, double z1,
                                          double x2, double y2, double z2) {
        double dx = x1 - x2, dy = y1 - y2, dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }
}
