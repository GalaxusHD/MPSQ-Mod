package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Client-side flight, idle and click interaction for Nog's Budgie companion. */
final class MpsqBudgiePetRenderer {
    private static final String PET_ID = "nogs_budgie";
    private static final Set<String> VARIANTS = Set.of("green", "blue_spangle", "cobalt", "gray", "green_spangle", "light_green", "olive", "sky_blue", "white", "yellow");
    private static final double ROAM_RADIUS = 8.0, LEASH_RADIUS = 10.0;
    private static final long ACTION_COOLDOWN = 2500;
    private static boolean registered, positioned, moving, targetFlying;
    private static double x, y, z, targetX, targetY, targetZ;
    private static float yaw;
    private static long nextTargetAt, pauseUntil, actionStartedAt, actionUntil, actionCooldownUntil, nextIdleActionAt;
    private static String action;
    private static java.util.UUID threatId;
    private static net.minecraft.client.world.ClientWorld trackedWorld;

    private MpsqBudgiePetRenderer() { }
    static boolean isVariant(String variant) { return variant != null && VARIANTS.contains(variant); }
    static MpsqPetPresenceClient.Snapshot snapshot() {
        return positioned && PET_ID.equals(MpsqPetSelectionStore.selectedId())
                ? new MpsqPetPresenceClient.Snapshot(null,PET_ID,MpsqPetSelectionStore.budgieVariant(),x,y,z,yaw,System.currentTimeMillis()) : null;
    }
    static boolean isAirborne(MinecraftClient client, double px, double py, double pz) {
        if (client.world == null || client.player == null) return true;
        double floor = MpsqPetGrounding.groundY(client, px, py, pz);
        return !Double.isFinite(floor) || py > floor + 0.12;
    }

    static void initialize() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(MpsqBudgiePetRenderer::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null || !TeamVisibilitySettings.visible() || !positioned || trackedWorld != client.world || !PET_ID.equals(MpsqPetSelectionStore.selectedId())) return;
            var matrices = context.matrixStack(); var consumers = context.consumers();
            if (matrices == null || consumers == null) return;
            Vec3d camera = context.camera().getPos();
            if (camera.squaredDistanceTo(x,y,z) > 4096) return;
            long now = System.currentTimeMillis();
            boolean airborne = isAirborne(client, x, y, z);
            boolean actionPose = !airborne && action != null && now < actionUntil;
            String pose = airborne ? "fly" : actionPose ? action : moving ? "walk" : "idle";
            double time = actionPose ? (now-actionStartedAt)/1000.0 : now/1000.0;
            int light = WorldRenderer.getLightmapCoordinates(client.world, BlockPos.ofFloored(x,y,z));
            MpsqBudgieModel.render(matrices, consumers, light, (float)(x-camera.x), (float)(y-camera.y), (float)(z-camera.z), yaw, 0.58f, MpsqPetSelectionStore.budgieVariant(), pose, time);
        });
    }

    private static void tick(MinecraftClient client) {
        var owner = client.player;
        if (owner == null || client.world == null || !TeamVisibilitySettings.visible() || !PET_ID.equals(MpsqPetSelectionStore.selectedId())) {
            positioned=false; moving=false; action=null; trackedWorld=null; return;
        }
        long now=System.currentTimeMillis();
        if (trackedWorld != client.world) { trackedWorld=client.world; positioned=false; action=null; }
        if (!positioned) {
            double a=Math.toRadians(owner.getYaw()); x=owner.getX()+Math.sin(a)*1.8; z=owner.getZ()-Math.cos(a)*1.8;
            double floor = groundY(client, x, owner.getY(), z);
            y = Double.isFinite(floor) ? floor : owner.getY() + 1.6;
            yaw=owner.getYaw(); positioned=true; chooseTarget(client, owner, now);
        }
        if (action != null && now >= actionUntil) action=null;
        boolean grounded = !isAirborne(client, x, y, z);
        if (grounded && client.options.useKey.wasPressed() && now>=actionCooldownUntil && rayHits(owner)) startBite(client, now);
        if (action != null && now < actionUntil) { moving=false; return; }
        if (grounded && !moving && now>=nextIdleActionAt) {
            action=ThreadLocalRandom.current().nextBoolean()?"preen":"dance";
            actionStartedAt=now;actionUntil=now+Math.round(MpsqBudgieModel.animationLength(MpsqPetSelectionStore.budgieVariant(),action)*1000);
            nextIdleActionAt=now+ThreadLocalRandom.current().nextLong(9000,18000);moving=false;return;
        }

        // Back away from nearby non-owner players, then turn back toward the owner.
        var threat=client.world.getPlayers().stream().filter(p->p!=owner && p.isAlive() && p.squaredDistanceTo(x,y,z)<16).findFirst().orElse(null);
        if (threat != null && (!threat.getUuid().equals(threatId) || now>=nextTargetAt)) {
            threatId=threat.getUuid();
            double dx=x-threat.getX(), dz=z-threat.getZ(), len=Math.max(0.01,Math.sqrt(dx*dx+dz*dz));
            double fleeX=x+dx/len*4.5, fleeZ=z+dz/len*4.5;
            double toOwnerX=owner.getX()-fleeX, toOwnerZ=owner.getZ()-fleeZ; double ownerLen=Math.max(0.01,Math.sqrt(toOwnerX*toOwnerX+toOwnerZ*toOwnerZ));
            targetX=fleeX+toOwnerX/ownerLen*2.5; targetZ=fleeZ+toOwnerZ/ownerLen*2.5;
            double floor = groundY(client, fleeX, owner.getY(), fleeZ);
            targetY = (Double.isFinite(floor) ? floor : owner.getY()) + 1.5;
            targetFlying = true; nextTargetAt=now+1800; pauseUntil=0;
        }
        if(threat==null)threatId=null;
        double ownerDistance=horizontal(x,z,owner.getX(),owner.getZ());
        if (threat==null && (ownerDistance>LEASH_RADIUS || now>=nextTargetAt)) chooseTarget(client, owner,now);
        double dx=targetX-x,dy=targetY-y,dz=targetZ-z,dist=Math.sqrt(dx*dx+dy*dy+dz*dz);
        boolean reached = targetFlying ? dist < 0.25
                : horizontal(x, z, targetX, targetZ) < 0.25 && Math.abs(y - targetY) < 0.06;
        if (reached) {
            moving=false;
            if (pauseUntil==0) pauseUntil=now+ThreadLocalRandom.current().nextLong(900,3200);
            if (now>=pauseUntil) chooseTarget(client, owner,now);
            return;
        }
        if (now<pauseUntil) { moving=false; return; }
        moving=true;
        double step=threat==null?0.075:0.12, ratio=Math.min(step/dist,1.0);
        double nx=x+dx*ratio, nz=z+dz*ratio, nextY=y+dy*ratio;
        double floor = groundY(client, nx, owner.getY(), nz);
        if (!targetFlying) {
            if (!Double.isFinite(floor) || floor-y > 1.0001) {
                moving=false; chooseTarget(client, owner, now+1000); return;
            }
            // Follow slabs and terrain contours; the model's feet rest on the
            // collision surface instead of clipping below it.
            nextY = grounded ? floor : Math.max(floor, nextY);
        } else if (Double.isFinite(floor)) {
            nextY = Math.max(floor + 0.45, nextY);
        }
        x=nx; y=nextY; z=nz;
        yaw=approach(yaw,(float)Math.toDegrees(Math.atan2(-dx,dz)),8.0f);
    }

    private static void chooseTarget(MinecraftClient client, net.minecraft.client.network.ClientPlayerEntity owner,long now) {
        double angle=ThreadLocalRandom.current().nextDouble(Math.PI*2), radius=ThreadLocalRandom.current().nextDouble(2.0,ROAM_RADIUS);
        targetX=owner.getX()+Math.cos(angle)*radius; targetZ=owner.getZ()+Math.sin(angle)*radius;
        double floor = groundY(client, targetX, owner.getY(), targetZ);
        targetFlying = !Double.isFinite(floor) || ThreadLocalRandom.current().nextInt(4) != 0;
        targetY = Double.isFinite(floor) ? floor + (targetFlying ? ThreadLocalRandom.current().nextDouble(0.8, 2.8) : 0.0)
                : owner.getY() + 1.5;
        nextTargetAt=now+ThreadLocalRandom.current().nextLong(4000,10000); pauseUntil=0;
    }
    private static double groundY(MinecraftClient client, double px, double referenceY, double pz) {
        return MpsqPetGrounding.groundY(client, px, referenceY, pz);
    }
    private static void startBite(MinecraftClient client,long now) {
        action="bite"; actionStartedAt=now; actionUntil=now+550; actionCooldownUntil=now+ACTION_COOLDOWN;
        // Vanilla hurt status triggers only the brief client-side red tint and tilt; it does not alter health.
        client.player.handleStatus((byte)2);
        client.world.playSound(client.player, client.player.getBlockPos(), SoundEvents.ENTITY_PARROT_HURT, SoundCategory.PLAYERS, 0.45f, 1.2f);
    }
    private static boolean rayHits(net.minecraft.client.network.ClientPlayerEntity player) {
        Vec3d start=player.getCameraPosVec(1.0f), end=start.add(player.getRotationVec(1.0f).multiply(4.0));
        return new Box(x-0.5,y-0.4,z-0.5,x+0.5,y+0.5,z+0.5).raycast(start,end).isPresent();
    }
    private static double horizontal(double x1,double z1,double x2,double z2) { return Math.hypot(x2-x1,z2-z1); }
    private static float approach(float current,float target,float amount) {
        float difference=MathHelper.wrapDegrees(target-current); return current+Math.max(-amount,Math.min(amount,difference));
    }
}
