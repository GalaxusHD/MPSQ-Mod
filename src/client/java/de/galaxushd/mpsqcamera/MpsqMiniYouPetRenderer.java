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

/** Local roaming Mini You pets using the supplied Blockbench animation states. */
final class MpsqMiniYouPetRenderer {
    private static final double ROAM_RADIUS = 8.0;
    private static final double RETURN_DISTANCE = 6.0;
    private static final float MODEL_SCALE = 0.48f;
    private static final long INTERACTION_COOLDOWN = 3000;
    private static boolean registered, positioned, moving, alternateWalk;
    private static double x, y, z, targetX, targetZ;
    private static float yaw;
    private static long nextWanderAt, pauseUntil, nextWalkVariantAt, waveStartedAt, waveUntil, nextAllowedWaveAt;
    private static String activeId, trackedId;
    private static net.minecraft.client.world.ClientWorld trackedWorld;

    private MpsqMiniYouPetRenderer() { }

    static boolean isMiniYouId(String id) { MpsqPetCatalog.Pet pet=MpsqPetCatalog.byId(id);return pet!=null&&pet.miniModel()!=null; }

    static MpsqPetPresenceClient.Snapshot snapshot() {
        return positioned && isMiniYouId(MpsqPetSelectionStore.selectedId())
                ? new MpsqPetPresenceClient.Snapshot(null, MpsqPetSelectionStore.selectedId(), "",
                x, y, z, yaw, System.currentTimeMillis()) : null;
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
            String animation = now < waveUntil ? "wave" : moving
                    ? (alternateWalk ? "alternate_walk" : "walk") : "idle";
            double animTime = now < waveUntil ? (now - waveStartedAt) / 1000.0 : now / 1000.0;
            int light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(x, y, z));
            MpsqPetCatalog.Pet pet=MpsqPetCatalog.byId(id);MpsqNpcSkinRenderer.Skin skin=pet==null?null:MpsqPetRenderer.skinFor(pet);
            MpsqMiniYouModel.render(matrices, consumers, light, id,skin==null?null:skin.texture(),skin!=null&&skin.slim(),
                    (float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z),
                    yaw, MODEL_SCALE, animation, animTime);
        });
    }

    private static void tick(MinecraftClient client) {
        var owner = client.player;
        String selected = MpsqPetSelectionStore.selectedId();
        if (owner == null || client.world == null || !TeamVisibilitySettings.visible() || !isMiniYouId(selected)) {
            positioned = false;
            trackedWorld = null;
            trackedId = null;
            activeId = null;
            moving = false;
            return;
        }
        long now = System.currentTimeMillis();
        if (trackedWorld != client.world || !selected.equals(trackedId)) {
            trackedWorld = client.world;
            trackedId = selected;
            activeId = selected;
            double angle = Math.toRadians(owner.getYaw());
            x = owner.getX() - Math.sin(angle) * ThreadLocalRandom.current().nextDouble(0.6, 1.0);
            z = owner.getZ() + Math.cos(angle) * ThreadLocalRandom.current().nextDouble(0.6, 1.0);
            double initialY = groundedY(client, x, owner.getY(), z);
            y = Double.isFinite(initialY) ? initialY : owner.getY();
            yaw = owner.getYaw();
            positioned = true;
            waveUntil = 0;
            chooseTarget(owner, now);
        }
        if (now >= nextWalkVariantAt) {
            alternateWalk = ThreadLocalRandom.current().nextBoolean();
            nextWalkVariantAt = now + ThreadLocalRandom.current().nextLong(6500, 14000);
        }

        if (client.options.useKey.wasPressed() && rayHits(owner)) {
            if (now >= nextAllowedWaveAt) startWave(client, now);
        } else if (client.options.attackKey.wasPressed() && rayHits(owner)) {
            playSound(client, "entity.axolotl.hurt", 1.0f, 1.4f);
        }
        if (now < waveUntil) {
            moving = false;
            return;
        }

        double ownerDistance = horizontalDistance(x, z, owner.getX(), owner.getZ());
        boolean returning = ownerDistance > RETURN_DISTANCE;
        if (returning || now >= nextWanderAt) {
            if (returning) {
                targetX = owner.getX();
                targetZ = owner.getZ();
                pauseUntil = 0;
            } else chooseTarget(owner, now);
        }
        double dx = targetX - x, dz = targetZ - z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.20) {
            moving = false;
            if (pauseUntil == 0) pauseUntil = now + ThreadLocalRandom.current().nextLong(900, 3500);
            if (now >= pauseUntil) chooseTarget(owner, now);
            return;
        }
        if (now < pauseUntil) { moving = false; return; }

        moving = true;
        double speed = returning ? 0.10 : 0.055;
        double step = Math.min(speed, distance);
        double nx = x + dx / distance * step;
        double nz = z + dz / distance * step;
        double ny = groundedY(client, nx, y, nz);
        // A Mini-Me can climb ordinary steps and slabs. A missing floor,
        // a rise higher than one block, or a real body collision means the
        // current wander route is obstructed; it is not a bonk trigger.
        if (!Double.isFinite(ny) || ny - y > 1.001 || y - ny > 1.001
                || MpsqMiniMeGrounding.blocked(client, nx, ny - 0.01, nz)) {
            chooseTarget(owner, now + 1000);
            return;
        }
        x = nx;
        z = nz;
        y = ny;
        yaw = approachAngle(yaw, (float) Math.toDegrees(Math.atan2(-dx, dz)), 8.0f);
    }

    private static void startWave(MinecraftClient client, long now) {
        waveStartedAt = now;
        waveUntil = now + Math.round(MpsqMiniYouModel.animationLength(activeId, "wave") * 1000);
        nextAllowedWaveAt = now + INTERACTION_COOLDOWN;
        moving = false;
        playSound(client, "entity.axolotl.ambient", 0.7f, 1.5f);
    }

    private static void playSound(MinecraftClient client, String soundId, float volume, float pitch) {
        var sound = Registries.SOUND_EVENT.get(Identifier.ofVanilla(soundId));
        client.world.playSound(null, BlockPos.ofFloored(x, y, z), sound,
                SoundCategory.NEUTRAL, volume, pitch);
    }

    private static boolean rayHits(net.minecraft.client.network.ClientPlayerEntity player) {
        Vec3d start = player.getCameraPosVec(1.0f);
        Vec3d end = start.add(player.getRotationVec(1.0f).multiply(4.0));
        return new Box(x - 0.48, y, z - 0.48, x + 0.48, y + 1.55, z + 0.48)
                .raycast(start, end).isPresent();
    }

    private static void chooseTarget(net.minecraft.client.network.ClientPlayerEntity owner, long now) {
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double radius = ThreadLocalRandom.current().nextDouble(1.0, ROAM_RADIUS);
        targetX = owner.getX() + Math.cos(angle) * radius;
        targetZ = owner.getZ() + Math.sin(angle) * radius;
        nextWanderAt = now + ThreadLocalRandom.current().nextLong(3500, 8500);
        pauseUntil = 0;
    }

    private static double groundedY(MinecraftClient client, double px, double referenceY, double pz) {
        BlockPos probe = BlockPos.ofFloored(px, referenceY, pz);
        if (client.world != null && !client.world.getFluidState(probe).isEmpty()) return probe.getY() + 0.08;
        double surface = MpsqMiniMeGrounding.surfaceY(client, px, referenceY, pz);
        // The authored outer leg layer extends a quarter Blockbench unit
        // below the base. This small lift places its lowest pixels at the floor.
        return Double.isFinite(surface) ? surface + 0.01 : Double.NaN;
    }

    private static double horizontalDistance(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1, dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static float approachAngle(float current, float target, float amount) {
        float difference = net.minecraft.util.math.MathHelper.wrapDegrees(target - current);
        return current + Math.max(-amount, Math.min(amount, difference));
    }
}
