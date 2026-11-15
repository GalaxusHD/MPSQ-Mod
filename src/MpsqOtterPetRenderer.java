package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import de.galaxushd.mpsqcamera.compat.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.ThreadLocalRandom;

/** Client-side roaming, interaction and animation state for the Nocsy otter companion. */
final class MpsqOtterPetRenderer {
    private static final MpsqMiniNavigation navigation=new MpsqMiniNavigation(.28,.58);
    private static boolean relocate(Minecraft c){var p=MpsqPetGrounding.safeSpawn(c,c.player.getX(),c.player.getY(),c.player.getZ(),.28,.58);if(p==null)return false;x=p.x();y=p.y();z=p.z(); yaw=c.player.getYRot();positioned=true;motion.tick(x,y,z,yaw,false);navigation.reset(); return true;}

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
    private static boolean moving;
    private static net.minecraft.client.multiplayer.ClientLevel trackedWorld;
    private static long nextWanderAt;
    private static long pauseUntil;
    private static long lastPetAt;
    private static boolean useKeyWasDown;
    private static long petAnimationUntil;
    private static long petAnimationStartedAt;
    private static String petAnimation;
    private static boolean water;
    private static String previousSelectedId;
    private static String despawningId;
    private static long despawnStartedAt;
    private static double despawnX, despawnY, despawnZ;
    private static float despawnYaw;

    private static final MpsqPetMotion motion=new MpsqPetMotion();

    private MpsqOtterPetRenderer() { }

    static MpsqPetPresenceClient.Snapshot snapshot() {
        String selected=MpsqPetSelectionStore.activeId();
        return positioned && isOtterId(selected)
                ? new MpsqPetPresenceClient.Snapshot(null,selected,"",x,y,z,yaw,System.currentTimeMillis()) : null;
    }

    static void initialize() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(MpsqOtterPetRenderer::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (MpsqReplayPlayback.active() || client.level == null || client.player == null || !TeamVisibilitySettings.visible()) return;
            var matrices = context.matrixStack();
            var consumers = context.consumers();
            if (matrices == null || consumers == null) return;
            net.minecraft.world.phys.Vec3 camera = context.camera().getPos();
            double[] frame=motion.frame(x,y,z,yaw,context.tickCounter().getGameTimeDeltaPartialTick(false));
            int light;
            long now = System.currentTimeMillis();
            if (despawningId != null) {
                double elapsed = (now - despawnStartedAt) / 1000.0;
                if (elapsed >= MpsqOtterModel.animationLength("despawn")) {
                    despawningId = null;
                } else {
                    light = net.minecraft.util.LightCoordsUtil.getLightCoords(client.level,
                            net.minecraft.core.BlockPos.containing(despawnX, despawnY, despawnZ));
                    MpsqOtterModel.render(matrices, consumers, light,
                            (float)(despawnX-camera.x), (float)(despawnY-camera.y), (float)(despawnZ-camera.z),
                            despawnYaw, MODEL_SCALE, "despawn", elapsed);
                    return;
                }
            }
            if (!isOtterSelected() || !positioned) return;
            if (camera.distanceToSqr(x, y, z) >MpsqPetGrounding.viewDistanceSquared()) return;
            String action = currentPetAnimation(now);
            String animation = water ? "swim" : action;
            double animationTime = animation.equals(petAnimation) && petAnimation != null
                    ? (now - petAnimationStartedAt) / 1000.0
                    : (now / 1000.0);
            light = net.minecraft.util.LightCoordsUtil.getLightCoords(client.level, net.minecraft.core.BlockPos.containing(x, y, z));
            MpsqOtterModel.render(matrices, consumers, light,
                    (float)(frame[0]-camera.x), (float)(frame[1]-camera.y), (float)(frame[2]-camera.z), (float)frame[3],
                    MODEL_SCALE, animation, animationTime);
        });
    }

    static boolean suppressOtherPets() { return despawningId != null; }

    private static void tick(Minecraft client) {
        motion.tick(x,y,z,yaw,positioned);
        moving = false;
        if (trackedWorld != client.level) {
            trackedWorld = client.level;
            positioned = false;
            previousSelectedId = null;
            despawningId = null;
            petAnimation = null;
            petAnimationUntil = 0;
            pauseUntil = 0;
            useKeyWasDown = false;
        }
        if (client.player == null || client.level == null || !TeamVisibilitySettings.visible()) {
            positioned = false;
            previousSelectedId = null;
            return;
        }
        long now = System.currentTimeMillis();
        String selected = MpsqPetSelectionStore.activeId();
        boolean otterSelected = isOtterId(selected);
        if (isOtterId(previousSelectedId) && !otterSelected && despawningId == null && positioned) {
            beginDespawn(now);
        }
        if (despawningId != null && now - despawnStartedAt >= DESPAWN_MILLIS) despawningId = null;
        previousSelectedId = selected;
        if (!otterSelected || despawningId != null) return;

        var player = client.player;
        if (!positioned) { if(!relocate(client))return; chooseWanderTarget(client,now); }
        if(MpsqPetGrounding.blocked(client,x,y,z,.28,.58)){if(!relocate(client)){positioned=false;}return;}
        double ownerDistance=horizontalDistance(x,z,player.getX(),player.getZ());
        if(ownerDistance>20){if(!relocate(client))return;chooseWanderTarget(client,now);return;}

        water = isWater(client, x, y, z);
        boolean useDown = client.options.keyUse.isDown();
        boolean useStarted = useDown && !useKeyWasDown;
        useKeyWasDown = useDown;
        if (!water && client.gui.screen() == null && useStarted && now - lastPetAt >= PET_COOLDOWN_MILLIS
                && rayHitsOtter(player)) {
            petAnimation = ThreadLocalRandom.current().nextBoolean() ? "pet1" : "pet2";
            petAnimationStartedAt = now;
            petAnimationUntil = now + Math.round(MpsqOtterModel.animationLength(petAnimation) * 1000);
            lastPetAt = now;
        }

        if (horizontalDistance(x,z,player.getX(),player.getZ()) >= MAX_PLAYER_DISTANCE && horizontalDistance(targetX,targetZ,player.getX(),player.getZ())>9) {
            chooseWanderTarget(client, now);
        }
        if (water) { petAnimation = null; petAnimationUntil = 0; }
        if (now < petAnimationUntil) return;
        double targetDistance = horizontalDistance(x, z, targetX, targetZ);
        if (now >= nextWanderAt) {
            chooseWanderTarget(client, now);
        } else if (targetDistance < 0.55) {
            if (pauseUntil == 0) pauseUntil = now + ThreadLocalRandom.current().nextLong(900, 4200);
            if (now < pauseUntil) return;
            chooseWanderTarget(client, now);
        }
        var waypoint=water?null:navigation.next(client,x,y,z,targetX,targetZ,now,ownerDistance>10,player.getX(),player.getZ());
        if(!water&&waypoint==null)return;
        double dx = (waypoint==null?targetX:waypoint.x()) - x, dz = (waypoint==null?targetZ:waypoint.z()) - z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.05) return;
        double step = water ? 0.04 : movingFast ? 0.105 : 0.055;
        double nx = x + dx / distance * Math.min(step, distance);
        double nz = z + dz / distance * Math.min(step, distance);
        double ny = stepY(client, nx, y, nz);
        if (!Double.isFinite(ny) || ny - y > 1.0001 || y - ny > 1.1
                || MpsqPetGrounding.blocked(client, nx, ny, nz, 0.28, 0.58)) {
            // Missing floor, a drop, or an obstacle too high for the otter: pick
            // another route. Walkable slabs and steps up to one block are allowed.
            navigation.obstructed(now);
            return;
        }
        moving = true;
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
        return isOtterId(MpsqPetSelectionStore.activeId());
    }

    private static boolean isOtterId(String id) { return "nocsy_otter".equals(id); }

    private static String currentPetAnimation(long now) {
        if (petAnimation != null && now < petAnimationUntil) return petAnimation;
        petAnimation = null;
        if (pauseUntil > now) return "idle";
        return moving ? "walk" : "idle";
    }

    private static boolean rayHitsOtter(net.minecraft.client.player.LocalPlayer player) {
        Vec3 start = player.getEyePosition(1.0f);
        Vec3 end = start.add(player.getViewVector(1.0f).scale(3.2));
        AABB bounds = new AABB(x - 0.62, y, z - 0.62, x + 0.62, y + 0.9, z + 0.62);
        return bounds.clip(start, end).isPresent();
    }

    private static void chooseWanderTarget(Minecraft client, long now) {
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

    /** Finds a supported, unobstructed height across the entire footprint, including step edges. */
    private static double stepY(Minecraft client, double px, double referenceY, double pz) {
        java.util.TreeSet<Double> candidates = new java.util.TreeSet<>();
        double center = groundY(client, px, referenceY, pz);
        if (Double.isFinite(center)) candidates.add(center);
        for (int bx = net.minecraft.util.Mth.floor(px - .28); bx <= net.minecraft.util.Mth.floor(px + .28); bx++)
            for (int bz = net.minecraft.util.Mth.floor(pz - .28); bz <= net.minecraft.util.Mth.floor(pz + .28); bz++)
                for (int by = net.minecraft.util.Mth.floor(referenceY - 1.1) - 1;
                     by <= net.minecraft.util.Mth.floor(referenceY + 1); by++) {
                    BlockPos pos = new BlockPos(bx, by, bz);
                    if (!client.level.hasChunk(bx >> 4, bz >> 4)) continue;
                    for (AABB box : client.level.getBlockState(pos).getCollisionShape(client.level, pos).toAabbs()) {
                        if (px + .28 <= bx + box.minX || px - .28 >= bx + box.maxX
                                || pz + .28 <= bz + box.minZ || pz - .28 >= bz + box.maxZ) continue;
                        candidates.add(by + box.maxY);
                    }
                }
        for (double candidate : candidates.descendingSet()) {
            if (candidate - referenceY > 1.0001 || referenceY - candidate > 1.1) continue;
            // Check clearance for lifting at the old position as well as the destination.
            if (candidate > referenceY && MpsqPetGrounding.blocked(client, x, referenceY, z,
                    .28, .58 + candidate - referenceY)) continue;
            if (!MpsqPetGrounding.blocked(client, px, candidate, pz, .28, .58)) return candidate;
        }
        return Double.NaN;
    }

    private static double groundY(Minecraft client, double px, double referenceY, double pz) {
        double surface = MpsqPetGrounding.groundY(client, px, referenceY, pz);
        // Float just below a nearby water surface; do not mistake water below a dry floor for swimming.
        for (int by = net.minecraft.util.Mth.floor(referenceY + 1);
             by >= net.minecraft.util.Mth.floor(referenceY - 1.1); by--) {
            BlockPos pos = BlockPos.containing(px, by, pz);
            var fluid = client.level.getFluidState(pos);
            if (!fluid.is(net.minecraft.tags.FluidTags.WATER)) continue;
            double waterTop = by + fluid.getHeight(client.level, pos);
            double swimY = waterTop - .18;
            if (swimY > referenceY + 1.0001 || swimY < referenceY - 1.1) continue;
            if ((!Double.isFinite(surface) || surface < swimY)
                    && !MpsqPetGrounding.blocked(client, px, swimY, pz, .28, .58)) return swimY;
        }
        return surface;
    }

    private static boolean isWater(Minecraft client, double px, double py, double pz) {
        BlockPos feet = BlockPos.containing(px, py + .1, pz);
        var fluid = client.level.getFluidState(feet);
        return fluid.is(net.minecraft.tags.FluidTags.WATER)
                && py + .1 < feet.getY() + fluid.getHeight(client.level, feet);
    }

    private static double horizontalDistance(double x1,double z1,double x2,double z2) {
        double dx=x2-x1,dz=z2-z1; return Math.sqrt(dx*dx+dz*dz);
    }
    private static float approachAngle(float current, float target, float amount) {
        float difference = net.minecraft.util.Mth.wrapDegrees(target-current);
        return current + Math.max(-amount,Math.min(amount,difference));
    }
}
