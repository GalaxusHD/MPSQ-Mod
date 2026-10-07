package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.concurrent.ThreadLocalRandom;

/** Client-side roaming, interaction and animation state for the Nocsy otter companion. */
final class MpsqOtterPetRenderer {
    private static final double ROAM_RADIUS = 8.5;
    private static final double MAX_PLAYER_DISTANCE = 10.0;
    private static final float MODEL_SCALE = 0.62f;
    private static final long DESPAWN_MILLIS = 1900;
    private static final long PET_COOLDOWN_MILLIS = 6000;

    private static boolean registered;
    private static boolean positioned;
    private static double x, y, z, targetX, targetY, targetZ;
    private static float yaw;
    private static boolean movingFast;
    private static long nextWanderAt;
    private static long pauseUntil;
    private static long lastPetAt;
    private static long petAnimationUntil;
    private static long petAnimationStartedAt;
    private static String petAnimation;
    private static boolean water;
    private static String previousSelectedId;
    private static String despawningId;
    private static long despawnStartedAt;
    private static double despawnX, despawnY, despawnZ;
    private static float despawnYaw;

    private MpsqOtterPetRenderer() { }

    static MpsqPetPresenceClient.Snapshot snapshot() {
        String selected=MpsqPetSelectionStore.selectedId();
        return positioned && isOtterId(selected)
                ? new MpsqPetPresenceClient.Snapshot(null,selected,"",x,y,z,yaw,System.currentTimeMillis()) : null;
    }

    static void initialize() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(MpsqOtterPetRenderer::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null || !TeamVisibilitySettings.visible()) return;
            var matrices = context.matrixStack();
            var consumers = context.consumers();
            if (matrices == null || consumers == null) return;
            Vec3d camera = context.camera().getPos();
            int light;
            long now = System.currentTimeMillis();
            if (despawningId != null) {
                double elapsed = (now - despawnStartedAt) / 1000.0;
                if (elapsed >= MpsqOtterModel.animationLength("despawn")) {
                    despawningId = null;
                } else {
                    light = WorldRenderer.getLightmapCoordinates(client.world,
                            BlockPos.ofFloored(despawnX, despawnY, despawnZ));
                    MpsqOtterModel.render(matrices, consumers, light,
                            (float)(despawnX-camera.x), (float)(despawnY-camera.y), (float)(despawnZ-camera.z),
                            despawnYaw, MODEL_SCALE, "despawn", elapsed);
                    return;
                }
            }
            if (!isOtterSelected() || !positioned) return;
            if (camera.squaredDistanceTo(x, y, z) > 4096) return;
            String animation = water ? "swim" : currentPetAnimation(now);
            double animationTime = animation.equals(petAnimation) && petAnimation != null
                    ? (now - petAnimationStartedAt) / 1000.0
                    : (now / 1000.0);
            light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(x, y, z));
            MpsqOtterModel.render(matrices, consumers, light,
                    (float)(x-camera.x), (float)(y-camera.y), (float)(z-camera.z), yaw,
                    MODEL_SCALE, animation, animationTime);
        });
    }

    static boolean suppressOtherPets() { return despawningId != null; }

    private static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null || !TeamVisibilitySettings.visible()) {
            positioned = false;
            previousSelectedId = null;
            return;
        }
        long now = System.currentTimeMillis();
        String selected = MpsqPetSelectionStore.selectedId();
        boolean otterSelected = isOtterId(selected);
        if (isOtterId(previousSelectedId) && !otterSelected && despawningId == null && positioned) {
            beginDespawn(now);
        }
        if (despawningId != null && now - despawnStartedAt >= DESPAWN_MILLIS) despawningId = null;
        previousSelectedId = selected;
        if (!otterSelected || despawningId != null) return;

        var player = client.player;
        if (!positioned) {
            double radians = Math.toRadians(player.getYaw());
            x = player.getX() - Math.sin(radians) * 1.7;
            z = player.getZ() + Math.cos(radians) * 1.7;
            y = groundY(client, x, player.getY(), z);
            yaw = player.getYaw();
            positioned = true;
            chooseWanderTarget(client, now);
        }

        water = isWater(client, x, y, z);
        boolean usePressed = client.options.useKey.wasPressed();
        if (usePressed && !water && now - lastPetAt >= PET_COOLDOWN_MILLIS
                && rayHitsOtter(player)) {
            petAnimation = ThreadLocalRandom.current().nextBoolean() ? "pet1" : "pet2";
            petAnimationStartedAt = now;
            petAnimationUntil = now + Math.round(MpsqOtterModel.animationLength(petAnimation) * 1000);
            lastPetAt = now;
        }

        if (horizontalDistance(x,z,player.getX(),player.getZ()) >= MAX_PLAYER_DISTANCE) {
            chooseWanderTarget(client, now);
        }
        if (now < petAnimationUntil) return;
        double targetDistance = horizontalDistance(x, z, targetX, targetZ);
        if (now >= nextWanderAt) {
            chooseWanderTarget(client, now);
        } else if (targetDistance < 0.55) {
            if (pauseUntil == 0) pauseUntil = now + ThreadLocalRandom.current().nextLong(900, 4200);
            if (now < pauseUntil) return;
            chooseWanderTarget(client, now);
        }
        double dx = targetX - x, dz = targetZ - z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.05) return;
        double step = water ? 0.04 : movingFast ? 0.105 : 0.055;
        double nx = x + dx / distance * Math.min(step, distance);
        double nz = z + dz / distance * Math.min(step, distance);
        double ny = groundY(client, nx, y, nz);
        BlockPos feet = BlockPos.ofFloored(nx, ny + 0.05, nz);
        if (!client.world.getBlockState(feet).isAir()
                && !client.world.getFluidState(feet).isOf(net.minecraft.fluid.Fluids.WATER)) {
            chooseWanderTarget(client, now + 1500);
            return;
        }
        x = nx; z = nz; y = ny;
        water = isWater(client, x, y, z);
        float wantedYaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
        yaw = approachAngle(yaw, wantedYaw, 9.0f);
    }

    private static void beginDespawn(long now) {
        despawningId = "nocsy_otter";
        despawnStartedAt = now;
        despawnX = x; despawnY = y; despawnZ = z; despawnYaw = yaw;
        petAnimation = null; petAnimationUntil = 0;
        positioned = false;
    }

    private static boolean isOtterSelected() {
        return isOtterId(MpsqPetSelectionStore.selectedId());
    }

    private static boolean isOtterId(String id) { return "nocsy_otter".equals(id); }

    private static String currentPetAnimation(long now) {
        if (petAnimation != null && now < petAnimationUntil) return petAnimation;
        petAnimation = null;
        if (pauseUntil > now) return "idle";
        return movingFast || horizontalDistance(x,z,targetX,targetZ) > 0.15 ? "walk" : "idle";
    }

    private static boolean rayHitsOtter(net.minecraft.client.network.ClientPlayerEntity player) {
        Vec3d start = player.getCameraPosVec(1.0f);
        Vec3d end = start.add(player.getRotationVec(1.0f).multiply(3.2));
        Box bounds = new Box(x - 0.62, y, z - 0.62, x + 0.62, y + 0.9, z + 0.62);
        return bounds.raycast(start, end).isPresent();
    }

    private static void chooseWanderTarget(MinecraftClient client, long now) {
        var player = client.player;
        if (player == null) return;
        double playerDistance = horizontalDistance(x,z,player.getX(),player.getZ());
        movingFast = false;
        if (playerDistance > MAX_PLAYER_DISTANCE - 1.0 || ThreadLocalRandom.current().nextInt(5) == 0) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double radius = playerDistance > MAX_PLAYER_DISTANCE - 1.0 ? 3.0
                    : ThreadLocalRandom.current().nextDouble(1.3, 4.0);
            targetX = player.getX() + Math.cos(angle) * radius;
            targetZ = player.getZ() + Math.sin(angle) * radius;
            movingFast = playerDistance > 5.0;
        } else {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double radius = ThreadLocalRandom.current().nextDouble(2.0, ROAM_RADIUS);
            targetX = player.getX() + Math.cos(angle) * radius;
            targetZ = player.getZ() + Math.sin(angle) * radius;
        }
        targetY = groundY(client,targetX,player.getY(),targetZ);
        double remaining = horizontalDistance(x, z, targetX, targetZ);
        double travelSpeed = movingFast ? 0.105 : 0.055;
        long travelTime = (long) (remaining / travelSpeed * 50.0);
        nextWanderAt = now + Math.max(5000, travelTime + ThreadLocalRandom.current().nextLong(1500, 4500));
        pauseUntil = 0;
    }

    private static double groundY(MinecraftClient client, double px, double referenceY, double pz) {
        int bx = net.minecraft.util.math.MathHelper.floor(px), bz = net.minecraft.util.math.MathHelper.floor(pz);
        int by = net.minecraft.util.math.MathHelper.floor(referenceY);
        BlockPos probe = new BlockPos(bx, by, bz);
        if (client.world.getFluidState(probe).isOf(net.minecraft.fluid.Fluids.WATER)) return by + 0.08;
        int top = client.world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        return top;
    }

    private static boolean isWater(MinecraftClient client, double px, double py, double pz) {
        BlockPos feet = BlockPos.ofFloored(px, py + 0.1, pz);
        return client.world.getFluidState(feet).isOf(net.minecraft.fluid.Fluids.WATER)
                || client.world.getFluidState(feet.down()).isOf(net.minecraft.fluid.Fluids.WATER);
    }

    private static double horizontalDistance(double x1,double z1,double x2,double z2) {
        double dx=x2-x1,dz=z2-z1; return Math.sqrt(dx*dx+dz*dz);
    }
    private static float approachAngle(float current, float target, float amount) {
        float difference = net.minecraft.util.math.MathHelper.wrapDegrees(target-current);
        return current + Math.max(-amount,Math.min(amount,difference));
    }
}
