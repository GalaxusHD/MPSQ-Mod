package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import de.galaxushd.mpsqcamera.compat.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.ThreadLocalRandom;

/** Local roaming Mini You pets using the supplied Blockbench animation states. */
final class MpsqMiniYouPetRenderer {
    private static final double ROAM_RADIUS = 9.5;
    private static final double RETURN_DISTANCE = 10.0;
    private static final float MODEL_SCALE = 0.48f;
    private static final long INTERACTION_COOLDOWN = 3000;
    private static boolean registered, positioned, moving, alternateWalk, useWasDown, attackWasDown;
    private static double ownerX, ownerZ;

    private static double x, y, z, targetX, targetZ;
    private static float yaw;
    private static long nextWanderAt, pauseUntil, waveStartedAt, waveUntil, nextAllowedWaveAt;
    private static String activeId, trackedId;
    private static String interaction="wave"; private static boolean teleported, departureShown, arrivalShown;
    private static net.minecraft.client.multiplayer.ClientLevel trackedWorld;

    private static final MpsqMiniNavigation navigation=new MpsqMiniNavigation();
    private static long nextTeleportAttempt;
    private static final MpsqPetMotion motion=new MpsqPetMotion();

    private MpsqMiniYouPetRenderer() { }

    static boolean isMiniYouId(String id) { MpsqPetCatalog.Pet pet=MpsqPetCatalog.byId(id);return pet!=null&&pet.miniModel()!=null; }

    static MpsqPetPresenceClient.Snapshot snapshot() {
        return positioned && isMiniYouId(MpsqPetSelectionStore.activeId())
                ? new MpsqPetPresenceClient.Snapshot(null, MpsqPetSelectionStore.activeId(), "",
                x, y, z, yaw, System.currentTimeMillis()) : null;
    }

    static void initialize() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(MpsqMiniYouPetRenderer::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            String id = MpsqPetSelectionStore.activeId();
            if (MpsqReplayPlayback.active() || client.level == null || client.player == null || !TeamVisibilitySettings.visible()
                    || !positioned || trackedWorld != client.level || !isMiniYouId(id)) return;
            var matrices = context.matrixStack();
            var consumers = context.consumers();
            if (matrices == null || consumers == null) return;
            net.minecraft.world.phys.Vec3 camera = context.camera().getPos();
            double[] frame=motion.frame(x,y,z,yaw,context.tickCounter().getGameTimeDeltaPartialTick(false));
            if (camera.distanceToSqr(x, y, z) >MpsqPetGrounding.viewDistanceSquared()) return;
            long now = System.currentTimeMillis();
            String animation = now < waveUntil ? interaction : moving
                    ? (alternateWalk ? "alternate_walk" : "walk") : "idle";
            double animTime = now < waveUntil ? (now - waveStartedAt) / 1000.0 : now / 1000.0;
            int light = net.minecraft.util.LightCoordsUtil.getLightCoords(client.level, net.minecraft.core.BlockPos.containing(x, y, z));
            MpsqPetCatalog.Pet pet=MpsqPetCatalog.byId(id);MpsqNpcSkinRenderer.Skin skin=pet==null?null:MpsqPetRenderer.skinFor(pet);
            MpsqMiniYouModel.render(matrices, consumers, light, id,skin==null?null:skin.texture(),skin!=null&&skin.slim(),
                    (float)(frame[0]-camera.x), (float)(frame[1]-camera.y), (float)(frame[2]-camera.z),
                    (float)frame[3]+(now<waveUntil&&interaction.equals("wave")?MpsqMiniYouModel.waveRootYaw(id,skin!=null&&skin.slim(),animTime):0), MODEL_SCALE, animation, animTime);
        });
    }

    private static void tick(Minecraft client) {
        motion.tick(x,y,z,yaw,positioned);
        boolean useDown=client.options.keyUse.isDown(), attackDown=client.options.keyAttack.isDown();
        boolean useStarted=useDown&&!useWasDown, attackStarted=attackDown&&!attackWasDown;
        useWasDown=useDown; attackWasDown=attackDown;
        moving=false;
        var owner = client.player;
        String selected = MpsqPetSelectionStore.activeId();
        if (owner == null || client.level == null || !TeamVisibilitySettings.visible() || !isMiniYouId(selected)) {
            navigation.reset();
            positioned = false;
            trackedWorld = null;
            trackedId = null;
            activeId = null;
            moving = false;
            return;
        }
        long now = System.currentTimeMillis();
        if (!positioned || trackedWorld != client.level || !selected.equals(trackedId)) {
            trackedWorld = client.level;
            trackedId = selected;
            activeId = selected;
            var spawn=MpsqMiniNavigation.safeReturn(client,owner.getX(),owner.getY(),owner.getZ());
            if(spawn==null){positioned=false;return;}
            x=spawn.x();y=spawn.y();z=spawn.z();motion.tick(x,y,z,yaw,false);
            yaw = owner.getYRot();
            positioned = true;
            navigation.reset();
            waveUntil = 0;
            ownerX=owner.getX(); ownerZ=owner.getZ();
            chooseTarget(owner, now);
        }
        double ownerSpeed=horizontalDistance(ownerX,ownerZ,owner.getX(),owner.getZ());
        if(MpsqPetGrounding.blocked(client,x,y,z,.18,.64)){positioned=false;navigation.reset();return;}
        ownerX=owner.getX(); ownerZ=owner.getZ();

        if (client.gui.screen()==null && useStarted && rayHits(owner)) {
            if (now >= nextAllowedWaveAt) startWave(client, now);
        } else if (client.gui.screen()==null && attackStarted && rayHits(owner)) {
            playSound(client, "entity.axolotl.hurt", 1.0f, 1.4f);
        }
        if (now < waveUntil) {
            if(interaction.equals("teleport")){
                long elapsed=now-waveStartedAt;
                // Match the authored scale keys: shrink at .20s, grow at 1.15s.
                if(!departureShown&&elapsed>=200){departureShown=true;MpsqCustomPetRenderer.teleportDeparture(client,x,y,z,.62f);}
                if(!teleported&&elapsed>=750){teleported=true;teleport(client,now);}
                if(!arrivalShown&&elapsed>=1150){arrivalShown=true;MpsqCustomPetRenderer.teleportParticles(client,x,y,z,.62f);}
            }
            moving = false;
            double dx=owner.getX()-x,dz=owner.getZ()-z;
            if(Math.hypot(dx,dz)>.05)yaw=approachAngle(yaw,(float)Math.toDegrees(Math.atan2(-dx,dz)),25f);
            return;
        }

        double ownerDistance = horizontalDistance(x, z, owner.getX(), owner.getZ());
        if((ownerDistance>20||Math.abs(owner.getY()-y)>20)&&now>=nextTeleportAttempt){
            nextTeleportAttempt=now+1000;
            var safe=MpsqMiniNavigation.safeReturn(client,owner.getX(),owner.getY(),owner.getZ());
            if(safe!=null){x=safe.x();y=safe.y();z=safe.z();motion.tick(x,y,z,yaw,false);navigation.reset();chooseTarget(owner,now);return;}
        }
        boolean returning = ownerDistance > RETURN_DISTANCE;
        if (returning || now >= nextWanderAt || horizontalDistance(targetX,targetZ,owner.getX(),owner.getZ())>RETURN_DISTANCE) {
            if (returning) {
                double away=Math.max(.001,ownerDistance);
                targetX = owner.getX()+(x-owner.getX())/away*8.0;
                targetZ = owner.getZ()+(z-owner.getZ())/away*8.0;
                pauseUntil = 0;
                nextWanderAt = 0;
            } else chooseTarget(owner, now);
        }
        double targetDistance=horizontalDistance(x,z,targetX,targetZ);
        if(targetDistance<.8){
            if(pauseUntil==0)pauseUntil=now+ThreadLocalRandom.current().nextLong(900,3500);
            if(now>=pauseUntil)chooseTarget(owner,now);
            return;
        }
        if(now<pauseUntil)return;
        var waypoint=navigation.next(client,x,y-.01,z,targetX,targetZ,now,returning,owner.getX(),owner.getZ());
        if(waypoint==null){if(!returning&&now>=nextWanderAt)chooseTarget(owner,now);return;}
        double dx=waypoint.x()-x,dz=waypoint.z()-z,distance=Math.hypot(dx,dz);
        if(distance<.001)return;
        double speed=followSpeed(returning,ownerDistance,ownerSpeed);
        alternateWalk=returning;
        double step=Math.min(speed,distance),nx=x+dx/distance*step,nz=z+dz/distance*step;
        double ny=MpsqPetGrounding.stepY(client,x,y-.01,z,nx,nz,.18,.64);
        if(!Double.isFinite(ny)){navigation.obstructed(now);return;}
        if(!returning&&horizontalDistance(nx,nz,owner.getX(),owner.getZ())>RETURN_DISTANCE){navigation.obstructed(now);nextWanderAt=0;return;}
        moving=true;
        ny+=.01;
        x = nx;
        z = nz;
        y = ny;
        yaw = approachAngle(yaw, (float) Math.toDegrees(Math.atan2(-dx, dz)), 8.0f);
    }

    static double followSpeed(boolean following,double distance,double ownerSpeed) {
        return following ? .15 : .075;
    }

    static String interactionFor(String id,boolean teleportChoice){
        return "endermansdl".equals(id)&&teleportChoice?"teleport":"wave";
    }

    private static void startWave(Minecraft client, long now) {
        interaction=interactionFor(activeId,ThreadLocalRandom.current().nextBoolean());teleported=false;departureShown=false;arrivalShown=false;
        waveStartedAt = now;
        waveUntil = now + Math.round(MpsqMiniYouModel.animationLength(activeId, interaction) * 1000);
        nextAllowedWaveAt = now + INTERACTION_COOLDOWN;
        moving = false;
        if(interaction.equals("wave"))playSound(client, "entity.axolotl.ambient", 0.7f, 1.5f);
    }

    private static void teleport(Minecraft client,long now){
        var safe=MpsqPetGrounding.safeSpawn(client,client.player.getX(),client.player.getY(),client.player.getZ(),.18,.64,true);
        if(safe==null)return;
        x=safe.x();y=safe.y();z=safe.z();
        motion.tick(x,y,z,yaw,false);navigation.reset();chooseTarget(client.player,now);

    }

    private static void playSound(Minecraft client, String soundId, float volume, float pitch) {
        var sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(soundId));
        client.level.playSound(null, BlockPos.containing(x, y, z), sound,
                SoundSource.NEUTRAL, volume, pitch);
    }

    private static boolean rayHits(net.minecraft.client.player.LocalPlayer player) {
        Vec3 start = player.getEyePosition(1.0f);
        Vec3 end = start.add(player.getViewVector(1.0f).scale(4.0));
        var hit=new AABB(x-.20,y,z-.20,x+.20,y+.65,z+.20).clip(start,end);
        if(hit.isEmpty())return false;
        var block=player.level().clip(new net.minecraft.world.level.ClipContext(start,end,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        return block.getType()==net.minecraft.world.phys.HitResult.Type.MISS
                || start.distanceToSqr(hit.get())<start.distanceToSqr(block.getLocation());
    }

    private static void chooseTarget(net.minecraft.client.player.LocalPlayer owner, long now) {
        navigation.reset();
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double radius = ThreadLocalRandom.current().nextDouble(1.0, ROAM_RADIUS);
        targetX = owner.getX() + Math.cos(angle) * radius;
        targetZ = owner.getZ() + Math.sin(angle) * radius;
        nextWanderAt = now + ThreadLocalRandom.current().nextLong(3500, 8500);
        pauseUntil = 0;
    }

    private static double groundedY(Minecraft client, double px, double referenceY, double pz) {
        BlockPos probe = BlockPos.containing(px, referenceY, pz);
        if (client.level != null && !client.level.getFluidState(probe).isEmpty()) return probe.getY() + 0.08;
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
        float difference = net.minecraft.util.Mth.wrapDegrees(target - current);
        return current + Math.max(-amount, Math.min(amount, difference));
    }
}
