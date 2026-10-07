package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/** Client-side roaming, sniffing, obstacle response and animation for Nog's hedgehog pet. */
final class MpsqHedgehogPetRenderer {
    private static final String PET_ID = "nogs_hedgehog";
    private static final double ROAM_RADIUS = 10.0;
    private static final double MAX_PLAYER_DISTANCE = 10.0;
    private static final float MODEL_SCALE = 1.0f;
    private static final long ACTION_COOLDOWN_MILLIS = 3000;
    private static final long SNIFF_SOUND_OFFSETS[] = {750, 950, 1150};

    private static boolean registered;
    private static boolean positioned;
    private static boolean moving;
    private static double x, y, z, targetX, targetY, targetZ;
    private static float yaw;
    private static long nextWanderAt;
    private static long pauseUntil;
    private static long actionCooldownUntil;
    private static long actionStartedAt;
    private static long actionUntil;
    private static int sniffSoundsPlayed;
    private static boolean retargetAfterAction;
    private static String actionAnimation;
    private static net.minecraft.client.world.ClientWorld trackedWorld;

    private MpsqHedgehogPetRenderer() { }

    static MpsqPetPresenceClient.Snapshot snapshot() {
        return positioned && PET_ID.equals(MpsqPetSelectionStore.selectedId())
                ? new MpsqPetPresenceClient.Snapshot(null,PET_ID,"",x,y,z,yaw,System.currentTimeMillis()) : null;
    }

    static void initialize() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(MpsqHedgehogPetRenderer::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null || !TeamVisibilitySettings.visible()
                    || !positioned || trackedWorld != client.world
                    || !PET_ID.equals(MpsqPetSelectionStore.selectedId())) return;
            var matrices = context.matrixStack();
            var consumers = context.consumers();
            if (matrices == null || consumers == null) return;
            Vec3d camera = context.camera().getPos();
            if (camera.squaredDistanceTo(x, y, z) > 4096) return;

            long now = System.currentTimeMillis();
            String animation = actionAnimation != null && now < actionUntil
                    ? actionAnimation : moving ? "walk" : "idle";
            double animationTime = actionAnimation != null && now < actionUntil
                    ? (now - actionStartedAt) / 1000.0 : now / 1000.0;
            int light = WorldRenderer.getLightmapCoordinates(client.world,
                    BlockPos.ofFloored(x, y, z));
            MpsqHedgehogModel.render(matrices, consumers, light,
                    (float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z),
                    yaw, MODEL_SCALE, animation, animationTime);
        });
    }

    private static void tick(MinecraftClient client) {
        var player = client.player;
        if (player == null || client.world == null || !TeamVisibilitySettings.visible()
                || !PET_ID.equals(MpsqPetSelectionStore.selectedId())) {
            positioned = false;
            moving = false;
            trackedWorld = null;
            actionAnimation = null;
            return;
        }

        long now = System.currentTimeMillis();
        if (trackedWorld != client.world) {
            positioned = false;
            actionAnimation = null;
            trackedWorld = client.world;
        }
        if (!positioned) {
            double radians = Math.toRadians(player.getYaw());
            x = player.getX() + Math.sin(radians) * 1.7;
            z = player.getZ() - Math.cos(radians) * 1.7;
            y = groundY(client, x, player.getY(), z);
            if (!Double.isFinite(y)) {
                double[] dry = findDrySpot(client, player.getX(), player.getY(), player.getZ());
                if (dry == null) return;
                x = dry[0]; y = dry[1]; z = dry[2];
            }
            yaw = player.getYaw();
            positioned = true;
            chooseWanderTarget(client, now);
        }

        if (actionAnimation != null && now >= actionUntil) {
            boolean retarget = retargetAfterAction;
            actionAnimation = null;
            retargetAfterAction = false;
            if (retarget) chooseWanderTarget(client, now);
        }

        if (client.options.useKey.wasPressed() && now >= actionCooldownUntil && rayHitsHedgehog(player)) {
            startSniff(client, now);
        }
        playSniffSounds(client, now);
        if (actionAnimation != null && now < actionUntil) {
            moving = false;
            return;
        }

        if (horizontalDistance(x, z, player.getX(), player.getZ()) >= MAX_PLAYER_DISTANCE) {
            chooseWanderTarget(client, now);
        }
        if (now >= nextWanderAt) chooseWanderTarget(client, now);

        double targetDistance = horizontalDistance(x, z, targetX, targetZ);
        if (targetDistance < 0.55) {
            if (pauseUntil == 0) pauseUntil = now + ThreadLocalRandom.current().nextLong(900, 4200);
            moving = false;
            if (now >= pauseUntil) chooseWanderTarget(client, now);
            else return;
        }
        if (now < pauseUntil) {
            moving = false;
            return;
        }

        double dx = targetX - x, dz = targetZ - z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.05) {
            moving = false;
            return;
        }
        moving = true;
        double step = movingFastTarget ? 0.105 : 0.055;
        double nx = x + dx / distance * Math.min(step, distance);
        double nz = z + dz / distance * Math.min(step, distance);
        double ny = groundY(client, nx, y, nz);
        // A missing floor is a ledge or gap, not a wall: choose another route
        // instead of playing the impact animation. The nearest collision surface
        // may be up to one block higher; the body test catches anything above it.
        if (!Double.isFinite(ny)) {
            moving = false;
            chooseWanderTarget(client, now + 1000);
            return;
        }
        boolean stepTooHigh = ny - y > 1.0001;
        boolean deepDrop = y - ny > 1.1;
        boolean bodyBlocked = !stepTooHigh
                && MpsqPetGrounding.blocked(client, nx, ny, nz, 0.23, 0.68);
        if (deepDrop) {
            moving = false;
            chooseWanderTarget(client, now + 1000);
            return;
        }
        if (stepTooHigh || bodyBlocked) {
            if (now >= actionCooldownUntil) startBoink(client, now);
            else chooseWanderTarget(client, now + 1000);
            return;
        }

        x = nx; y = ny; z = nz;
        float wantedYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        yaw = approachAngle(yaw, wantedYaw, 9.0f);
    }

    private static boolean movingFastTarget;

    private static void startSniff(MinecraftClient client, long now) {
        actionAnimation = "sniff";
        actionStartedAt = now;
        actionUntil = now + Math.round(MpsqHedgehogModel.animationLength("sniff") * 1000);
        actionCooldownUntil = now + ACTION_COOLDOWN_MILLIS;
        sniffSoundsPlayed = 0;
        retargetAfterAction = false;
    }

    private static void startBoink(MinecraftClient client, long now) {
        actionAnimation = "boink";
        actionStartedAt = now;
        actionUntil = now + Math.round(MpsqHedgehogModel.animationLength("boink") * 1000);
        actionCooldownUntil = now + ACTION_COOLDOWN_MILLIS;
        retargetAfterAction = true;
        moving = false;
        playSound(client, SoundEvents.ENTITY_ARMADILLO_BRUSH, 0.8f, 1.2f);
    }

    private static void playSniffSounds(MinecraftClient client, long now) {
        if (!"sniff".equals(actionAnimation)) return;
        long elapsed = now - actionStartedAt;
        while (sniffSoundsPlayed < SNIFF_SOUND_OFFSETS.length
                && elapsed >= SNIFF_SOUND_OFFSETS[sniffSoundsPlayed]) {
            playSound(client, SoundEvents.ENTITY_RABBIT_AMBIENT, 1.8f, 1.6f);
            sniffSoundsPlayed++;
        }
    }

    private static void playSound(MinecraftClient client, net.minecraft.sound.SoundEvent sound,
                                  float volume, float pitch) {
        if (client.world == null) return;
        client.world.playSound(null, BlockPos.ofFloored(x, y, z), sound,
                SoundCategory.NEUTRAL, volume, pitch);
    }

    private static boolean rayHitsHedgehog(net.minecraft.client.network.ClientPlayerEntity player) {
        Vec3d start = player.getCameraPosVec(1.0f);
        Vec3d end = start.add(player.getRotationVec(1.0f).multiply(3.2));
        Box bounds = new Box(x - 0.42, y, z - 0.42, x + 0.42, y + 0.72, z + 0.42);
        return bounds.raycast(start, end).isPresent();
    }

    private static void chooseWanderTarget(MinecraftClient client, long now) {
        var player = client.player;
        if (player == null) return;
        double playerDistance = horizontalDistance(x, z, player.getX(), player.getZ());
        boolean approach = playerDistance > MAX_PLAYER_DISTANCE - 1.0
                || ThreadLocalRandom.current().nextInt(5) == 0;
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double radius = approach
                    ? (playerDistance > MAX_PLAYER_DISTANCE - 1.0 ? 2.0 : ThreadLocalRandom.current().nextDouble(1.3, 4.0))
                    : ThreadLocalRandom.current().nextDouble(2.0, ROAM_RADIUS);
            double candidateX = player.getX() + Math.cos(angle) * radius;
            double candidateZ = player.getZ() + Math.sin(angle) * radius;
            double candidateY = groundY(client, candidateX, player.getY(), candidateZ);
            if (!Double.isFinite(candidateY)) continue;
            targetX = candidateX; targetY = candidateY; targetZ = candidateZ;
            movingFastTarget = approach && playerDistance > 5.0;
            double remaining = horizontalDistance(x, z, targetX, targetZ);
            double travelSpeed = movingFastTarget ? 0.105 : 0.055;
            long travelTime = (long) (remaining / travelSpeed * 50.0);
            nextWanderAt = now + Math.max(5000, travelTime
                    + ThreadLocalRandom.current().nextLong(1500, 4500));
            pauseUntil = 0;
            return;
        }
        moving = false;
        pauseUntil = now + 1000;
        nextWanderAt = now + 1000;
    }

    private static double[] findDrySpot(MinecraftClient client, double centerX, double centerY, double centerZ) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double radius = ThreadLocalRandom.current().nextDouble(1.0, 7.0);
            double px = centerX + Math.cos(angle) * radius;
            double pz = centerZ + Math.sin(angle) * radius;
            double py = groundY(client, px, centerY, pz);
            if (Double.isFinite(py)) return new double[]{px, py, pz};
        }
        return null;
    }

    private static double groundY(MinecraftClient client, double px, double referenceY, double pz) {
        double surface = MpsqPetGrounding.groundY(client, px, referenceY, pz);
        if (!Double.isFinite(surface)) return Double.NaN;
        BlockPos feet = BlockPos.ofFloored(px, surface + 0.03, pz);
        if (client.world.getFluidState(feet).isOf(net.minecraft.fluid.Fluids.WATER)) return Double.NaN;
        return surface;
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
