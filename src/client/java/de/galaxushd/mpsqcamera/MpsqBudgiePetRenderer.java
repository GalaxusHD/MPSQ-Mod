package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import de.galaxushd.mpsqcamera.compat.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Client-side flight, idle and click interaction for Nog's Budgie companion. */
final class MpsqBudgiePetRenderer {
    private static final MpsqFlightNavigation flightNavigation=new MpsqFlightNavigation(.4,1.4);
    private static boolean relocate(Minecraft c){var p=MpsqPetGrounding.safeSpawn(c,c.player.getX(),c.player.getY(),c.player.getZ(),.16,.5);if(p==null)return false;x=p.x();y=p.y();z=p.z(); yaw=c.player.getYRot();positioned=true;motion.tick(x,y,z,yaw,false); return true;}

    private static final String PET_ID = "nogs_budgie";
    private static final Set<String> VARIANTS = Set.of("green", "blue_spangle", "cobalt", "gray", "green_spangle", "light_green", "olive", "sky_blue", "white", "yellow");
    private static final double ROAM_RADIUS = 8.0, LEASH_RADIUS = 10.0;
    private static final long ACTION_COOLDOWN = 2500;
    private static boolean registered, positioned, moving, targetFlying, useWasDown, dancePending;
    private static double x, y, z, targetX, targetY, targetZ;
    private static float yaw;
    private static long nextTargetAt, pauseUntil, actionStartedAt, actionUntil, actionCooldownUntil, nextIdleActionAt;
    private static String action;
    private static java.util.UUID threatId;
    private static net.minecraft.client.multiplayer.ClientLevel trackedWorld;

    private static final MpsqPetMotion motion=new MpsqPetMotion();

    private MpsqBudgiePetRenderer() { }
    static boolean isVariant(String variant) { return variant != null && VARIANTS.contains(variant); }
    static MpsqPetPresenceClient.Snapshot snapshot() {
        return positioned && PET_ID.equals(MpsqPetSelectionStore.activeId())
                ? new MpsqPetPresenceClient.Snapshot(null,PET_ID,MpsqPetSelectionStore.budgieVariant(),x,y,z,yaw,System.currentTimeMillis()) : null;
    }
    static boolean isAirborne(Minecraft client, double px, double py, double pz) {
        if (client.level == null || client.player == null) return true;
        double floor = MpsqPetGrounding.groundY(client, px, py, pz);
        return !Double.isFinite(floor) || Math.abs(py - floor) > 0.12;
    }

    static void initialize() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(MpsqBudgiePetRenderer::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (MpsqReplayPlayback.active() || client.level == null || client.player == null || !TeamVisibilitySettings.visible() || !positioned || trackedWorld != client.level || !PET_ID.equals(MpsqPetSelectionStore.activeId())) return;
            var matrices = context.matrixStack(); var consumers = context.consumers();
            if (matrices == null || consumers == null) return;
            net.minecraft.world.phys.Vec3 camera = context.camera().getPos();
            double[] frame=motion.frame(x,y,z,yaw,context.tickCounter().getGameTimeDeltaPartialTick(false));
            if (camera.distanceToSqr(x,y,z) >MpsqPetGrounding.viewDistanceSquared()) return;
            long now = System.currentTimeMillis();
            boolean airborne = isAirborne(client, x, y, z);
            boolean actionPose = !airborne && action != null && now < actionUntil;
            String pose = airborne ? "fly" : actionPose ? action : moving ? "walk" : "idle";
            double time = actionPose ? (now-actionStartedAt)/1000.0 : now/1000.0;
            if (actionPose && "dance".equals(action)) time %= Math.max(0.001, MpsqBudgieModel.animationLength(MpsqPetSelectionStore.budgieVariant(), action));
            int light = net.minecraft.util.LightCoordsUtil.getLightCoords(client.level, net.minecraft.core.BlockPos.containing(x,y,z));
            MpsqBudgieModel.render(matrices, consumers, light, (float)(frame[0]-camera.x), (float)(frame[1]-camera.y), (float)(frame[2]-camera.z), (float)frame[3], 0.58f, MpsqPetSelectionStore.budgieVariant(), pose, time);
        });
    }

    private static void tick(Minecraft client) {
        motion.tick(x,y,z,yaw,positioned);
        var owner = client.player;
        if (owner == null || client.level == null || !TeamVisibilitySettings.visible() || !PET_ID.equals(MpsqPetSelectionStore.activeId())) {
            positioned=false; moving=false; action=null; trackedWorld=null; useWasDown=false; dancePending=false; return;
        }
        long now=System.currentTimeMillis();
        if (trackedWorld != client.level) { trackedWorld=client.level; positioned=false; action=null; dancePending=false; nextIdleActionAt=now+12000; pauseUntil=0; }
        if (!positioned) { if(!relocate(client))return;chooseTarget(client,owner,now); }
        if(MpsqPetGrounding.blocked(client,x,y,z,.16,.5)){if(!relocate(client)){positioned=false;}return;}
        double leashDistance=horizontal(x,z,owner.getX(),owner.getZ());
        if(leashDistance>20){if(!relocate(client))return;moving=false;chooseTarget(client,owner,now);return;}
        else if(leashDistance>10&&horizontal(targetX,targetZ,owner.getX(),owner.getZ())>9)chooseTarget(client,owner,now);
        if (action != null && now >= actionUntil) action=null;
        boolean grounded = !isAirborne(client, x, y, z);
        boolean useDown=client.options.keyUse.isDown();
        if (client.gui.screen() == null && useDown && !useWasDown && now>=actionCooldownUntil && rayHits(owner)) {
            actionCooldownUntil=now+ACTION_COOLDOWN;
            if (grounded) startDance(now);
            else {
                double landing=groundY(client,x,owner.getY(),z);
                if (Double.isFinite(landing)) {
                    dancePending=true;action=null;targetX=x;targetZ=z;targetY=landing;
                    targetFlying=false;pauseUntil=0;nextTargetAt=now+10000;
                }
            }
        }
        useWasDown=useDown;
        if (dancePending && grounded) { dancePending=false;startDance(now); }
        if (!grounded) action=null;
        if (action != null && now < actionUntil) { moving=false; return; }
        if (grounded && !moving && now<pauseUntil && now>=nextIdleActionAt) {
            action="preen";
            actionStartedAt=now;actionUntil=now+Math.round(MpsqBudgieModel.animationLength(MpsqPetSelectionStore.budgieVariant(),action)*1000);
            nextIdleActionAt=now+ThreadLocalRandom.current().nextLong(9000,18000);moving=false;return;
        }

        // Back away from nearby non-owner players, then turn back toward the owner.
        var threat=client.level.players().stream().filter(p->p!=owner && p.isAlive() && p.distanceToSqr(x,y,z)<16).findFirst().orElse(null);
        if (!dancePending && threat != null && (!threat.getUUID().equals(threatId) || now>=nextTargetAt)) {
            threatId=threat.getUUID();
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
        if (!dancePending && threat==null && (ownerDistance>LEASH_RADIUS && horizontal(targetX,targetZ,owner.getX(),owner.getZ())>9 || now>=nextTargetAt)) chooseTarget(client, owner,now);
        double dx=targetX-x,dy=targetY-y,dz=targetZ-z,dist=Math.sqrt(dx*dx+dy*dy+dz*dz);
        boolean reached = targetFlying ? dist < 0.25
                : horizontal(x, z, targetX, targetZ) < 0.25 && Math.abs(y - targetY) < 0.06;
        if (reached) {
            moving=false;
            if (pauseUntil==0) {
                pauseUntil=now+ThreadLocalRandom.current().nextLong(1500,4200);
                nextTargetAt=Math.max(nextTargetAt,pauseUntil+1);
            }
            if (now>=pauseUntil) chooseTarget(client, owner,now);
            return;
        }
        if (now<pauseUntil) { moving=false; return; }
        if(targetFlying){var waypoint=flightNavigation.next(client,new Vec3(x,y,z),new Vec3(targetX,targetY,targetZ),now);if(waypoint==null){moving=false;return;}dx=waypoint.x-x;dy=waypoint.y-y;dz=waypoint.z-z;dist=Math.sqrt(dx*dx+dy*dy+dz*dz);if(dist<.001)return;}
        moving=true;
        double step=threat==null&&leashDistance<=10?0.075:0.12, ratio=Math.min(step/dist,1.0);
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
        if (MpsqPetGrounding.blocked(client,nx,nextY,nz,0.16,0.5)) {
            moving=false; dancePending=false; chooseTarget(client,owner,now); return;
        }
        x=nx; y=nextY; z=nz;
        yaw=approach(yaw,(float)Math.toDegrees(Math.atan2(-dx,dz)),8.0f);
    }

    private static void chooseTarget(Minecraft client, net.minecraft.client.player.LocalPlayer owner,long now) {
        double angle=ThreadLocalRandom.current().nextDouble(Math.PI*2),distance=horizontal(x,z,owner.getX(),owner.getZ());boolean returning=distance>10;double radius=returning?ThreadLocalRandom.current().nextDouble(2.0,5.0):ThreadLocalRandom.current().nextDouble(2.0,ROAM_RADIUS);
        targetX=owner.getX()+Math.cos(angle)*radius; targetZ=owner.getZ()+Math.sin(angle)*radius;
        double floor = groundY(client, targetX, owner.getY(), targetZ);
        targetFlying = returning || !Double.isFinite(floor) || ThreadLocalRandom.current().nextInt(4) != 0;
        targetY = Double.isFinite(floor) ? floor + (targetFlying ? ThreadLocalRandom.current().nextDouble(0.8, 2.8) : 0.0)
                : owner.getY() + 1.5;
        nextTargetAt=now+ThreadLocalRandom.current().nextLong(4000,10000); pauseUntil=0;
    }
    private static double groundY(Minecraft client, double px, double referenceY, double pz) {
        return MpsqPetGrounding.groundY(client, px, referenceY, pz);
    }
    private static void startDance(long now) {
        action="dance"; actionStartedAt=now;
        actionUntil=now+Math.round(MpsqBudgieModel.animationLength(MpsqPetSelectionStore.budgieVariant(),action)*3000);
        moving=false; pauseUntil=actionUntil; nextTargetAt=actionUntil+1500;
        nextIdleActionAt=actionUntil+12000;
    }
    private static boolean rayHits(net.minecraft.client.player.LocalPlayer player) {
        Vec3 start=player.getEyePosition(1.0f), end=start.add(player.getViewVector(1.0f).scale(4.0));
        return new AABB(x-0.3,y,z-0.3,x+0.3,y+0.65,z+0.3).clip(start,end)
                .filter(hit -> {
                    var worldHit=player.level().clip(new net.minecraft.world.level.ClipContext(start,hit,
                            net.minecraft.world.level.ClipContext.Block.COLLIDER,
                            net.minecraft.world.level.ClipContext.Fluid.NONE,player));
                    return worldHit.getType()==net.minecraft.world.phys.HitResult.Type.MISS;
                }).isPresent();
    }
    private static double horizontal(double x1,double z1,double x2,double z2) { return Math.hypot(x2-x1,z2-z1); }
    private static float approach(float current,float target,float amount) {
        float difference=Mth.wrapDegrees(target-current); return current+Math.max(-amount,Math.min(amount,difference));
    }
}
