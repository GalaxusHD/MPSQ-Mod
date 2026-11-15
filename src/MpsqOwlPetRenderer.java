package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import de.galaxushd.mpsqcamera.compat.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import java.util.concurrent.ThreadLocalRandom;

/** Owls fly between perches; authored walk is flight, idle is exclusively grounded. */
final class MpsqOwlPetRenderer {
    private static final MpsqFlightNavigation flightNavigation=new MpsqFlightNavigation(.4,1.4);
    private static boolean relocate(Minecraft c){var p=MpsqPetGrounding.safeSpawn(c,c.player.getX(),c.player.getY(),c.player.getZ(),.3,.85);if(p==null)return false;x=p.x();y=p.y();z=p.z(); yaw=c.player.getYRot();positioned=true;motion.tick(x,y,z,yaw,false); return true;}

    private static boolean registered,positioned,flying;
    private static double x,y,z,targetX,targetY,targetZ;
    private static float yaw;
    private static long nextTarget,pauseUntil;
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static final MpsqPetMotion motion=new MpsqPetMotion();
    private static String petId(){String id=MpsqPetSelectionStore.activeId();return id!=null&&id.startsWith("nogs_owl_")?id:"";}
    static String animation(Minecraft c,double x,double y,double z){return MpsqBudgiePetRenderer.isAirborne(c,x,y,z)?"walk":"idle";}
    static MpsqPetPresenceClient.Snapshot snapshot(){return positioned&&!petId().isEmpty()?new MpsqPetPresenceClient.Snapshot(null,petId(),"",x,y,z,yaw,System.currentTimeMillis()):null;}
    static void initialize(){
        if(registered)return;registered=true;
        ClientTickEvents.END_CLIENT_TICK.register(MpsqOwlPetRenderer::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(ctx->{
            var c=Minecraft.getInstance();if(MpsqReplayPlayback.active()||c.level==null||c.player==null||world!=c.level||!positioned||petId().isEmpty()||!TeamVisibilitySettings.visible())return;
            var cam=ctx.camera().getPos();if(cam.distanceToSqr(x,y,z)>MpsqPetGrounding.viewDistanceSquared()||ctx.matrixStack()==null||ctx.consumers()==null)return;
            var frame=motion.frame(x,y,z,yaw,ctx.tickCounter().getGameTimeDeltaPartialTick(false));
            MpsqOwlModel.select(petId());
            MpsqOwlModel.render(ctx.matrixStack(),ctx.consumers(),net.minecraft.util.LightCoordsUtil.getLightCoords(c.level,net.minecraft.core.BlockPos.containing(x,y,z)),
                (float)(frame[0]-cam.x),(float)(frame[1]-cam.y),(float)(frame[2]-cam.z),(float)frame[3],1,animation(c,x,y,z),System.nanoTime()/1e9);
        });
    }
    private static void tick(Minecraft c){
        motion.tick(x,y,z,yaw,positioned);
        if(c.level==null||c.player==null||!TeamVisibilitySettings.visible()||petId().isEmpty()){positioned=false;world=null;return;}
        if(c.isPaused())return;
        long now=System.currentTimeMillis();
        if(world!=c.level){world=c.level;positioned=false;}
        if(!positioned){if(!relocate(c))return;choose(c,now);}
        if(MpsqPetGrounding.blocked(c,x,y,z,.3,.85)){if(!relocate(c)){positioned=false;}return;}
        double ownerDistance=Math.hypot(x-c.player.getX(),z-c.player.getZ());if(ownerDistance>20){if(!relocate(c))return;choose(c,now);return;}
        if((Math.hypot(x-c.player.getX(),z-c.player.getZ())>10&&Math.hypot(targetX-c.player.getX(),targetZ-c.player.getZ())>9)||now>=nextTarget)choose(c,now);
        if(now<pauseUntil)return;
        double dx=targetX-x,dy=targetY-y,dz=targetZ-z,dist=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(dist<.08){x=targetX;y=targetY;z=targetZ;pauseUntil=now+ThreadLocalRandom.current().nextLong(1200,3500);nextTarget=pauseUntil;return;}
        var waypoint=flightNavigation.next(c,new net.minecraft.world.phys.Vec3(x,y,z),new net.minecraft.world.phys.Vec3(targetX,targetY,targetZ),now);if(waypoint==null)return;dx=waypoint.x-x;dy=waypoint.y-y;dz=waypoint.z-z;dist=Math.sqrt(dx*dx+dy*dy+dz*dz);if(dist<.001)return;
        double step=Math.min(.1,dist),nx=x+dx/dist*step,ny=y+dy/dist*step,nz=z+dz/dist*step;
        double floor=MpsqPetGrounding.groundY(c,nx,ny,nz);
        if(Double.isFinite(floor))ny=Math.max(floor,ny);
        if(MpsqPetGrounding.blocked(c,nx,ny,nz,.3,.85)){choose(c,now);return;}
        x=nx;y=ny;z=nz;
        if(Math.hypot(dx,dz)>.02)yaw+=Math.clamp(net.minecraft.util.Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-dx,dz))-yaw),-8f,8f);
    }
    private static void choose(Minecraft c,long now){
        var random=ThreadLocalRandom.current();double angle=random.nextDouble(Math.PI*2),radius=random.nextDouble(2,6);
        targetX=c.player.getX()+Math.cos(angle)*radius;targetZ=c.player.getZ()+Math.sin(angle)*radius;
        double floor=MpsqPetGrounding.groundY(c,targetX,c.player.getY(),targetZ);
        flying=!Double.isFinite(floor)||random.nextInt(4)!=0;
        targetY=Double.isFinite(floor)?floor+(flying?random.nextDouble(1.4,3.5):0):c.player.getY()+2;
        // A perched owl takes off before horizontal travel instead of sliding along the ground.
        if(!MpsqBudgiePetRenderer.isAirborne(c,x,y,z)){targetX=x;targetZ=z;targetY=y+1.5;flying=true;}
        nextTarget=now+12000;pauseUntil=0;
    }
}
