package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import de.galaxushd.mpsqcamera.compat.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.ThreadLocalRandom;

/** Client-side roaming, sniffing, obstacle response and animation for Nog's hedgehog pet. */
final class MpsqHedgehogPetRenderer {
    private static final MpsqMiniNavigation navigation=new MpsqMiniNavigation(.23,.68);
    private static boolean relocate(Minecraft c){var p=MpsqPetGrounding.safeSpawn(c,c.player.getX(),c.player.getY(),c.player.getZ(),.23,.68);if(p==null)return false;x=p.x();y=p.y();z=p.z(); yaw=c.player.getYRot();positioned=true;motion.tick(x,y,z,yaw,false);navigation.reset(); return true;}

    private static final String PET_ID = "nogs_hedgehog";
    private static final double ROAM_RADIUS = 10.0;
    private static final double MAX_PLAYER_DISTANCE = 10.0;
    private static final float MODEL_SCALE = 1.0f;
    private static final long ACTION_COOLDOWN_MILLIS = 3000;
    private static final long SNIFF_SOUND_OFFSETS[] = {750, 950, 1150};

    private static boolean registered;
    private static boolean positioned;
    private static boolean moving;
    private static boolean useKeyWasDown;
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
    private static net.minecraft.client.multiplayer.ClientLevel trackedWorld;

    private static final MpsqPetMotion motion=new MpsqPetMotion();

    private MpsqHedgehogPetRenderer() { }

    static MpsqPetPresenceClient.Snapshot snapshot() {
        return positioned && PET_ID.equals(MpsqPetSelectionStore.activeId())
                ? new MpsqPetPresenceClient.Snapshot(null,PET_ID,"",x,y,z,yaw,System.currentTimeMillis()) : null;
    }

    static void initialize() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(MpsqHedgehogPetRenderer::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (MpsqReplayPlayback.active() || client.level == null || client.player == null || !TeamVisibilitySettings.visible()
                    || !positioned || trackedWorld != client.level
                    || !PET_ID.equals(MpsqPetSelectionStore.activeId())) return;
            var matrices = context.matrixStack();
            var consumers = context.consumers();
            if (matrices == null || consumers == null) return;
            net.minecraft.world.phys.Vec3 camera = context.camera().getPos();
            double[] frame=motion.frame(x,y,z,yaw,context.tickCounter().getGameTimeDeltaPartialTick(false));
            if (camera.distanceToSqr(x, y, z) >MpsqPetGrounding.viewDistanceSquared()) return;

            long now = System.currentTimeMillis();
            String animation = actionAnimation != null && now < actionUntil
                    ? actionAnimation : moving ? "walk" : "idle";
            double animationTime = actionAnimation != null && now < actionUntil
                    ? (now - actionStartedAt) / 1000.0 : now / 1000.0;
            int light = net.minecraft.util.LightCoordsUtil.getLightCoords(client.level,
                    net.minecraft.core.BlockPos.containing(x, y, z));
            MpsqHedgehogModel.render(matrices, consumers, light,
                    (float)(frame[0]-camera.x), (float)(frame[1]-camera.y), (float)(frame[2]-camera.z),
                    (float)frame[3], MODEL_SCALE, animation, animationTime);
        });
    }

    private static void tick(Minecraft client) {
        motion.tick(x,y,z,yaw,positioned);
        boolean useDown = client.options.keyUse.isDown();
        boolean useStarted = useDown && !useKeyWasDown;
        useKeyWasDown = useDown;
        var player = client.player;
        if (player == null || client.level == null || !TeamVisibilitySettings.visible()
                || !PET_ID.equals(MpsqPetSelectionStore.activeId())) {
            positioned = false;
            moving = false;
            trackedWorld = null;
            actionAnimation = null;
            return;
        }

        long now = System.currentTimeMillis();
        if (trackedWorld != client.level) {
            positioned = false;
            actionAnimation = null;
            trackedWorld = client.level;
        }
        if (!positioned) { if(!relocate(client))return; chooseWanderTarget(client,now); }
        if(MpsqPetGrounding.blocked(client,x,y,z,.23,.68)){if(!relocate(client)){positioned=false;}return;}
        double ownerDistance=horizontalDistance(x,z,player.getX(),player.getZ());
        if(ownerDistance>20){if(!relocate(client))return;moving=false;chooseWanderTarget(client,now);return;}

        if (actionAnimation != null && now >= actionUntil) {
            boolean retarget = retargetAfterAction;
            actionAnimation = null;
            retargetAfterAction = false;
            if (retarget) chooseWanderTarget(client, now);
        }

        if (client.gui.screen() == null && useStarted && now >= actionCooldownUntil && rayHitsHedgehog(player)) {
            startSniff(client, now);
        }
        playSniffSounds(client, now);
        if (actionAnimation != null && now < actionUntil) {
            moving = false;
            return;
        }

        if (horizontalDistance(x, z, player.getX(), player.getZ()) >= MAX_PLAYER_DISTANCE && horizontalDistance(targetX,targetZ,player.getX(),player.getZ())>9) {
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

        var waypoint=navigation.next(client,x,y,z,targetX,targetZ,now,ownerDistance>10,player.getX(),player.getZ());
        if(waypoint==null){moving=false;return;}
        double dx = waypoint.x() - x, dz = waypoint.z() - z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.05) {
            moving = false;
            return;
        }
        moving = true;
        double step = movingFastTarget ? 0.105 : 0.055;
        double nx = x + dx / distance * Math.min(step, distance);
        double nz = z + dz / distance * Math.min(step, distance);
        double ny = MpsqPetGrounding.stepY(client, x, y, z, nx, nz, .23, .68);
        if (!Double.isFinite(ny)) {
            navigation.obstructed(now);
            moving = false;
            // A wall gets an impact response; a gap or a drop merely changes the route.
            if (MpsqPetGrounding.blocked(client, nx, y, nz, .23, .68)
                    && now >= actionCooldownUntil) {
                yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
                startBoink(client, now);
            } else chooseWanderTarget(client, now + 1000);
            return;
        }
        BlockPos feet = BlockPos.containing(nx, ny + .03, nz);
        if (client.level.getFluidState(feet).is(net.minecraft.tags.FluidTags.WATER)) {
            moving = false;
            chooseWanderTarget(client, now + 1000);
            return;
        }

        x = nx; y = ny; z = nz;
        float wantedYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        yaw = approachAngle(yaw, wantedYaw, 9.0f);
    }

    private static boolean movingFastTarget;

    private static void startSniff(Minecraft client, long now) {
        actionAnimation = "sniff";
        actionStartedAt = now;
        actionUntil = now + Math.round(MpsqHedgehogModel.animationLength("sniff") * 1000);
        actionCooldownUntil = now + ACTION_COOLDOWN_MILLIS;
        sniffSoundsPlayed = 0;
        retargetAfterAction = false;
    }

    private static void startBoink(Minecraft client, long now) {
        actionAnimation = "boink";
        actionStartedAt = now;
        actionUntil = now + Math.round(MpsqHedgehogModel.animationLength("boink") * 1000);
        actionCooldownUntil = now + ACTION_COOLDOWN_MILLIS;
        retargetAfterAction = true;
        moving = false;
        playSound(client, SoundEvents.ARMADILLO_BRUSH, 0.8f, 1.2f);
    }

    private static void playSniffSounds(Minecraft client, long now) {
        if (!"sniff".equals(actionAnimation)) return;
        long elapsed = now - actionStartedAt;
        while (sniffSoundsPlayed < SNIFF_SOUND_OFFSETS.length
                && elapsed >= SNIFF_SOUND_OFFSETS[sniffSoundsPlayed]) {
            playSound(client, SoundEvents.RABBIT_AMBIENT, 1.8f, 1.6f);
            sniffSoundsPlayed++;
        }
    }

    private static void playSound(Minecraft client, net.minecraft.sounds.SoundEvent sound,
                                  float volume, float pitch) {
        if (client.level == null) return;
        client.level.playSound(null, BlockPos.containing(x, y, z), sound,
                SoundSource.NEUTRAL, volume, pitch);
    }

    private static boolean rayHitsHedgehog(net.minecraft.client.player.LocalPlayer player) {
        Vec3 start = player.getEyePosition(1.0f);
        Vec3 end = start.add(player.getViewVector(1.0f).scale(3.2));
        AABB bounds = new AABB(x - 0.42, y, z - 0.42, x + 0.42, y + 0.72, z + 0.42);
        return bounds.clip(start, end).isPresent();
    }

    private static void chooseWanderTarget(Minecraft client, long now) {
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

    private static double[] findDrySpot(Minecraft client, double centerX, double centerY, double centerZ) {
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

    private static double groundY(Minecraft client, double px, double referenceY, double pz) {
        double surface = MpsqPetGrounding.groundY(client, px, referenceY, pz);
        if (!Double.isFinite(surface)) return Double.NaN;
        BlockPos feet = BlockPos.containing(px, surface + 0.03, pz);
        if (client.level.getFluidState(feet).is(net.minecraft.world.level.material.Fluids.WATER)) return Double.NaN;
        return surface;
    }

    private static double horizontalDistance(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1, dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static float approachAngle(float current, float target, float amount) {
        float difference = net.minecraft.util.Mth.wrapDegrees(target - current);
        return current + Math.max(-amount, Math.min(amount, difference));
    }
}
