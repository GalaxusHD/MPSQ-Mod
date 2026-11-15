package de.galaxushd.mpsqcamera;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import de.galaxushd.mpsqcamera.compat.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.resources.Identifier;

/** Renders the selected Mini-Me skin as a small local companion. */
final class MpsqPetRenderer {
    private static final float PET_SCALE = 0.42f;
    private static final double FOLLOW_DISTANCE = 1.15;
    private static final Map<String, MpsqNpcSkinRenderer.Skin> LEGACY_SKINS = new HashMap<>();

    private static net.minecraft.client.multiplayer.ClientLevel trackedWorld;
    private static double petX;
    private static double petY;
    private static double petZ;
    private static boolean positioned;
    private static String trackedPetId;

    private MpsqPetRenderer() { }

    static MpsqPetPresenceClient.Snapshot snapshot() {
        MpsqPetCatalog.Pet pet=selectedPet();
        return positioned && pet!=null && pet.issuerRole()==null && !MpsqPetCatalog.isAnimal(pet)
                ? new MpsqPetPresenceClient.Snapshot(null,pet.id(),"",petX,petY,petZ,Minecraft.getInstance().player==null?0:Minecraft.getInstance().player.getYRot(),System.currentTimeMillis()) : null;
    }

    static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(MpsqPetRenderer::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            var player = client.player;
            var matrices = context.matrixStack();
            var consumers = context.consumers();
            if (MpsqReplayPlayback.active() || player == null || client.level == null || matrices == null || consumers == null
                    || !TeamVisibilitySettings.visible() || !positioned || trackedWorld != client.level) return;
            if (MpsqOtterPetRenderer.suppressOtherPets()) return;

            MpsqPetCatalog.Pet pet = selectedPet();
            if (pet == null || pet.issuerRole() != null || MpsqPetCatalog.isAnimal(pet)
                    || MpsqMiniYouPetRenderer.isMiniYouId(pet.id())) return;
            MpsqNpcSkinRenderer.Skin skin = skinFor(pet);
            if (skin == null) return;

            var camera = context.camera().getPos();
            if (camera.distanceToSqr(petX, petY, petZ) >MpsqPetGrounding.viewDistanceSquared()) return;
            float yaw = player.getYRot();
            float age = (System.currentTimeMillis() % 1_000_000L) / 50.0f;
            var state = MpsqNpcSkinRenderer.createState(skin, yaw, 0.0f, age, false);
            var velocity = player.getDeltaMovement();
            float speed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
            state.walkAnimationPos = age * (speed > 0.02f ? 0.18f : 0.035f);
            state.walkAnimationSpeed = Math.min(0.55f, speed * 2.2f);
            int light = net.minecraft.util.LightCoordsUtil.getLightCoords(client.level,
                    net.minecraft.core.BlockPos.containing(petX, petY, petZ));
            MpsqPetModelContext.render(() -> MpsqNpcSkinRenderer.render(state,
                    petX - camera.x, petY - camera.y, petZ - camera.z,
                    PET_SCALE, matrices, consumers, light, 0xFFFFFFFF));
        });
    }

    private static void tick(Minecraft client) {
        var player = client.player;
        MpsqPetCatalog.Pet pet = selectedPet();
        if (player == null || client.level == null || pet == null || pet.issuerRole() != null
                || MpsqMiniYouPetRenderer.isMiniYouId(pet.id())
                || !TeamVisibilitySettings.visible()) {
            positioned = false;
            trackedWorld = null;
            trackedPetId = null;
            return;
        }

        float yaw = player.getYRot();
        double radians = Math.toRadians(yaw);
        double targetX = player.getX() + Math.sin(radians) * FOLLOW_DISTANCE;
        double targetY = player.getY();
        double targetZ = player.getZ() - Math.cos(radians) * FOLLOW_DISTANCE;
        boolean reset = !positioned || trackedWorld != client.level || !pet.id().equals(trackedPetId)
                || squaredDistance(petX, petY, petZ, targetX, targetY, targetZ) > 64.0;
        if (reset) {
            petX = targetX;
            petY = targetY;
            petZ = targetZ;
            positioned = true;
            trackedWorld = client.level;
            trackedPetId = pet.id();
        } else {
            petX += (targetX - petX) * 0.28;
            petY += (targetY - petY) * 0.28;
            petZ += (targetZ - petZ) * 0.28;
        }
    }

    private static MpsqPetCatalog.Pet selectedPet() {
        String id = MpsqPetSelectionStore.activeId();
        return id == null ? null : MpsqPetCatalog.byId(id);
    }

    static MpsqNpcSkinRenderer.Skin skinFor(MpsqPetCatalog.Pet pet) {
        Minecraft client = Minecraft.getInstance();
        if ("__player__".equals(pet.texture())) {
            if (client.player == null) return null;
            PlayerSkin textures = client.player.getSkin();
            return new MpsqNpcSkinRenderer.Skin(textures.body().texturePath(),
                    textures.model() == net.minecraft.world.entity.player.PlayerModelType.SLIM);
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
            NativeImage expanded = MpsqSkinImages.normalize(source);
            Identifier texture = Identifier.fromNamespaceAndPath(MpsqCameraClient.MOD_ID, "pet_runtime/" + UUID.randomUUID());
            DynamicTexture registered = new DynamicTexture(() -> "MPSQ Pet Skin", expanded);
            Minecraft.getInstance().getTextureManager().register(texture, registered);
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
