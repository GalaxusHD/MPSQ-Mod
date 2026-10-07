package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/** Shared free-roaming and rendering path for every player-skin Mini-Me pet. */
final class MpsqMiniYouPetRenderer {
    private static final double ROAM_RADIUS = 7.0;
    private static final double LEASH_RADIUS = 10.0;
    private static final long INTERACTION_COOLDOWN = 3000;
    private static boolean registered, positioned, moving, alternateWalk, targetReturning;
    private static double x, y, z, targetX, targetZ;
    private static float yaw;
    private static long nextWanderAt, pauseUntil, nextWalkVariantAt, waveStartedAt, waveUntil, nextAllowedWaveAt;
    private static String trackedId;
    private static net.minecraft.client.world.ClientWorld trackedWorld;

    private MpsqMiniYouPetRenderer() { }

    static boolean isMiniYouId(String id) {
        MpsqPetCatalog.Pet pet = id == null ? null : MpsqPetCatalog.byId(id);
        return pet != null && pet.group() == MpsqPetCatalog.Group.MINI_YOU;
    }

    static MpsqPetPresenceClient.Snapshot snapshot() {
        String id = MpsqPetSelectionStore.selectedId();
        return positioned && isMiniYouId(id)
                ? new MpsqPetPresenceClient.Snapshot(null, id, "", x, y, z, yaw, System.currentTimeMillis()) : null;
    }

    static void initialize() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(MpsqMiniYouPetRenderer::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            String id = MpsqPetSelectionStore.selectedId();
            if (client.world == null || client.player == null || !TeamVisibilitySettings.visible()
                    || !positioned || trackedWorld != client.world || !isMiniYouId(id)) return;
            var matrices = context.matrixStack();
            var consumers = context.consumers();
            if (matrices == null || consumers == null) return;
            Vec3d camera = context.camera().getPos();
            if (camera.squaredDistanceTo(x, y, z) > 4096) return;

            long now = System.currentTimeMillis();
            MpsqPetCatalog.Pet pet = MpsqPetCatalog.byId(id);
            MpsqNpcSkinRenderer.Skin skin = pet == null ? null : MpsqPetRenderer.skinFor(pet);
            int light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(x, y, z));
            float px = (float)(x - camera.x), py = (float)(y - camera.y), pz = (float)(z - camera.z);
            if (pet != null && pet.miniModel() != null) {
                String animation = now < waveUntil ? "wave" : moving
                        ? (alternateWalk ? "alternate_walk" : "walk") : "idle";
                double animTime = now < waveUntil ? (now - waveStartedAt) / 1000.0 : now / 1000.0;
                MpsqMiniYouModel.render(matrices, consumers, light, id,
                        skin == null ? null : skin.texture(), skin != null && skin.slim(),
                        px, py, pz, yaw, 0.48f, animation, animTime);
            } else if (skin != null) {
                float age = (now % 1_000_000L) / 50.0f;
                var state = MpsqNpcSkinRenderer.createState(skin, yaw, 0.0f, age, false);
                state.limbSwingAnimationProgress = age * (moving ? 0.16f : 0.025f);
                state.limbSwingAmplitude = moving ? 0.65f : 0.0f;
                MpsqPetModelContext.render(() -> MpsqNpcSkinRenderer.render(state, px, py, pz,
                        0.42f, matrices, consumers, light, 0xFFFFFFFF));
            }
        });
    }

    private static void tick(MinecraftClient client) {
        var owner = client.player;
        String selected = MpsqPetSelectionStore.selectedId();
        if (owner == null || client.world == null || !TeamVisibilitySettings.visible() || !isMiniYouId(selected)) {
            positioned = false;
            trackedWorld = null;
            trackedId = null;
            moving = false;
            waveUntil = 0;
            return;
        }

        long now = System.currentTimeMillis();
        if (trackedWorld != client.world || !selected.equals(trackedId)) {
            trackedWorld = client.world;
            trackedId = selected;
            spawnNearOwner(client, owner);
            positioned = true;
            waveUntil = 0;
            chooseTarget(client, owner, now, false);
        }

        if (now >= nextWalkVariantAt) {
            alternateWalk = ThreadLocalRandom.current().nextBoolean();
            nextWalkVariantAt = now + ThreadLocalRandom.current().nextLong(6500, 14000);
        }
        if (client.options.useKey.wasPressed() && rayHits(owner) && now >= nextAllowedWaveAt) startWave(client, now);
        else if (client.options.attackKey.wasPressed() && rayHits(owner)) playSound(client, "entity.axolotl.hurt", 1.0f, 1.4f);
        if (now < waveUntil) { moving = false; return; }

        boolean returning = horizontalDistance(x, z, owner.getX(), owner.getZ()) > LEASH_RADIUS;
        if (returning != targetReturning || now >= nextWanderAt) chooseTarget(client, owner, now, returning);
        double dx = targetX - x, dz = targetZ - z, distance = Math.hypot(dx, dz);
        if (distance < 0.2) {
            moving = false;
            if (pauseUntil == 0) pauseUntil = now + ThreadLocalRandom.current().nextLong(900, 3500);
            if (now >= pauseUntil) chooseTarget(client, owner, now, false);
            return;
        }
        if (now < pauseUntil) { moving = false; return; }

        double step = Math.min(returning ? 0.11 : 0.055, distance);
        double nx = x + dx / distance * step, nz = z + dz / distance * step;
        double ny = surfaceY(client, nx, y, nz);
        if (!Double.isFinite(ny) || Math.abs(ny - y) > 1.1 || MpsqMiniMeGrounding.blocked(client, nx, ny, nz)) {
            moving = false;
            chooseTarget(client, owner, now, false);
            return;
        }

        moving = true;
        x = nx; y = ny; z = nz;
        yaw = approachAngle(yaw, (float)Math.toDegrees(Math.atan2(-dx, dz)), 8.0f);
    }

    private static double surfaceY(MinecraftClient client, double px, double referenceY, double pz) {
        BlockPos feet = BlockPos.ofFloored(px, referenceY + 0.03, pz);
        if (!client.world.getFluidState(feet).isEmpty()) return Double.NaN;
        return MpsqMiniMeGrounding.surfaceY(client, px, referenceY, pz);
    }

    private static void startWave(MinecraftClient client, long now) {
        MpsqPetCatalog.Pet pet = MpsqPetCatalog.byId(trackedId);
        double seconds = pet != null && pet.miniModel() != null
                ? MpsqMiniYouModel.animationLength(trackedId, "wave") : 1.2;
        waveStartedAt = now;
        waveUntil = now + Math.round(seconds * 1000.0);
        nextAllowedWaveAt = now + INTERACTION_COOLDOWN;
        moving = false;
        playSound(client, "entity.axolotl.ambient", 0.7f, 1.5f);
    }

    private static void playSound(MinecraftClient client, String soundId, float volume, float pitch) {
        if (client.world == null) return;
        var sound = Registries.SOUND_EVENT.get(Identifier.ofVanilla(soundId));
        client.world.playSound(null, BlockPos.ofFloored(x, y, z), sound, SoundCategory.NEUTRAL, volume, pitch);
    }

    private static boolean rayHits(net.minecraft.client.network.ClientPlayerEntity player) {
        Vec3d start = player.getCameraPosVec(1.0f);
        Vec3d end = start.add(player.getRotationVec(1.0f).multiply(4.0));
        return new Box(x - 0.45, y, z - 0.45, x + 0.45, y + 1.5, z + 0.45)
                .raycast(start, end).isPresent();
    }

    private static void chooseTarget(MinecraftClient client, net.minecraft.client.network.ClientPlayerEntity owner,
                                     long now, boolean returning) {
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double radius = returning ? ThreadLocalRandom.current().nextDouble(1.2, 2.8)
                    : ThreadLocalRandom.current().nextDouble(1.5, ROAM_RADIUS);
            double candidateX = owner.getX() + Math.cos(angle) * radius;
            double candidateZ = owner.getZ() + Math.sin(angle) * radius;
            double candidateY = surfaceY(client, candidateX, owner.getY(), candidateZ);
            if (!Double.isFinite(candidateY) || MpsqMiniMeGrounding.blocked(client, candidateX, candidateY, candidateZ)) continue;
            targetX = candidateX; targetZ = candidateZ;
            targetReturning = returning;
            nextWanderAt = now + (returning ? 4500 : ThreadLocalRandom.current().nextLong(4000, 9000));
            pauseUntil = 0;
            return;
        }
        moving = false;
        pauseUntil = now + 1000;
        nextWanderAt = now + 1000;
    }

    private static void spawnNearOwner(MinecraftClient client,
                                       net.minecraft.client.network.ClientPlayerEntity owner) {
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double radius = ThreadLocalRandom.current().nextDouble(1.2, 2.4);
            double candidateX = owner.getX() + Math.cos(angle) * radius;
            double candidateZ = owner.getZ() + Math.sin(angle) * radius;
            double candidateY = surfaceY(client, candidateX, owner.getY(), candidateZ);
            if (!Double.isFinite(candidateY) || MpsqMiniMeGrounding.blocked(client,candidateX,candidateY,candidateZ)) continue;
            x = candidateX; z = candidateZ; y = candidateY;
            yaw = (float)Math.toDegrees(Math.atan2(owner.getX() - x, owner.getZ() - z));
            return;
        }
        double angle = Math.toRadians(owner.getYaw());
        x = owner.getX() - Math.sin(angle) * 1.6;
        z = owner.getZ() + Math.cos(angle) * 1.6;
        y = surfaceY(client, x, owner.getY(), z);
        if (!Double.isFinite(y)) y = owner.getY();
        yaw = owner.getYaw();
    }

    private static double horizontalDistance(double x1, double z1, double x2, double z2) {
        return Math.hypot(x2 - x1, z2 - z1);
    }

    private static float approachAngle(float current, float target, float amount) {
        float difference = net.minecraft.util.math.MathHelper.wrapDegrees(target - current);
        return current + Math.max(-amount, Math.min(amount, difference));
    }
}
