package de.galaxushd.mpsqcamera;

import de.galaxushd.mpsqcamera.compat.WorldRenderEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.concurrent.ThreadLocalRandom;

/** Roaming state and model rendering for bundled custom animal pets. */
final class MpsqCustomPetRenderer {
    private static final MpsqFlightNavigation flightNavigation=new MpsqFlightNavigation(.4,1.4);
    private static final MpsqMiniNavigation normalNavigation=new MpsqMiniNavigation(.4,1.4),tigerNavigation=new MpsqMiniNavigation(.85,1.4);
    private static MpsqMiniNavigation navigation(){return "mpsq_tiger".equals(trackedId)?tigerNavigation:normalNavigation;}
    private static double radius(){return "mpsq_tiger".equals(trackedId)?.85:.4;}
    private static boolean relocate(Minecraft c){return relocate(c,false);}
    private static boolean relocate(Minecraft c,boolean random){var p=MpsqPetGrounding.safeSpawn(c,c.player.getX(),c.player.getY(),c.player.getZ(),radius(),1.4,random);if(p==null)return false;x=p.x();y=p.y();z=p.z();if("mpsq_baby_happy_ghast".equals(trackedId))y+=.1; yaw=c.player.getYRot();positioned=true;motion.tick(x,y,z,yaw,false);navigation().reset(); return true;}

    private static boolean registered,positioned,moving,useKeyWasDown;
    private static String trackedId="";
    private static double x,y,z,targetX,targetY,targetZ;
    private static float yaw,crabWalkBlend; private static boolean actionTeleported;
    private static long pauseUntil,nextTarget,animationStarted,spawnUntil,actionStarted,actionUntil,actionCooldownUntil;
    private static String actionClip="";
    private static net.minecraft.client.multiplayer.ClientLevel trackedWorld;
    private static final MpsqPetMotion motion=new MpsqPetMotion();
    private MpsqCustomPetRenderer(){}
    static boolean isCustomPet(String id){return id!=null&&(id.equals("mpsq_tiger")||id.equals("mpsq_tiger_baby")||id.equals("mpsq_crab")||id.equals("mpsq_penguin")||id.equals("mpsq_baby_happy_ghast")||id.equals("mpsq_colin_the_creeper")||id.equals("mpsq_exo_the_enderman")||id.equals("mpsq_slak_the_skeleton")||id.equals("mpsq_zekk_the_zombie"));}
    static String url(String id){return switch(id){case "mpsq_tiger"->"builtin://mpsq/nm_tiger";case "mpsq_tiger_baby"->"builtin://mpsq/nm_tiger_baby";case "mpsq_crab"->"builtin://mpsq/bs_vm_crab";case "mpsq_penguin"->"builtin://mpsq/bs_vm_penguin";case "mpsq_baby_happy_ghast"->"builtin://mpsq/baby_happy_ghast_mount";case "mpsq_colin_the_creeper"->"builtin://mpsq/nm_colin_the_creeper";case "mpsq_exo_the_enderman"->"builtin://mpsq/nm_exo_the_enderman";case "mpsq_slak_the_skeleton"->"builtin://mpsq/nm_slak_the_skeleton";case "mpsq_zekk_the_zombie"->"builtin://mpsq/nm_zekk_the_zombie";default->"";};}
    static boolean isModelUrl(String url){return url!=null&&(url.equals("builtin://mpsq/nm_tiger")||url.equals("builtin://mpsq/nm_tiger_baby")||url.equals("builtin://mpsq/bs_vm_crab")||url.equals("builtin://mpsq/bs_vm_penguin")||url.equals("builtin://mpsq/baby_happy_ghast_mount")||url.equals("builtin://mpsq/nm_colin_the_creeper")||url.equals("builtin://mpsq/nm_exo_the_enderman")||url.equals("builtin://mpsq/nm_slak_the_skeleton")||url.equals("builtin://mpsq/nm_zekk_the_zombie"));}
    private static String actionFor(String id){return switch(id){case "mpsq_tiger","mpsq_tiger_baby"->"roll";case "mpsq_colin_the_creeper"->"fuse";case "mpsq_exo_the_enderman"->"teleport";case "mpsq_slak_the_skeleton"->"head_spin";case "mpsq_zekk_the_zombie"->"trip";default->"";};}
    private static double clipSeconds(String id,String clip){double length=MpsqAccessoryRenderer.clipLength(url(id),clip);if(length>0)return length;return switch(id+":"+clip){case "mpsq_colin_the_creeper:spawn"->.35;case "mpsq_colin_the_creeper:fuse"->.95;case "mpsq_exo_the_enderman:spawn"->.35;case "mpsq_exo_the_enderman:teleport"->1.5;case "mpsq_slak_the_skeleton:spawn"->1.7;case "mpsq_slak_the_skeleton:head_spin"->1.1;case "mpsq_zekk_the_zombie:spawn"->1.7;case "mpsq_zekk_the_zombie:trip"->2.6;default->1.25;};}
    static MpsqPetPresenceClient.Snapshot snapshot(){return positioned&&isCustomPet(trackedId)?new MpsqPetPresenceClient.Snapshot(null,trackedId,"",x,y,z,yaw,System.currentTimeMillis()):null;}
    static void initialize(){
        if(registered)return;registered=true;
        ClientTickEvents.END_CLIENT_TICK.register(MpsqCustomPetRenderer::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(ctx->{
            var c=Minecraft.getInstance();if(MpsqPetVisibility.suppressed()||MpsqReplayPlayback.active()||c.level==null||c.player==null||!TeamVisibilitySettings.visible()||!positioned||!isCustomPet(trackedId))return;
            var cam=ctx.camera().getPos();if(cam.distanceToSqr(x,y,z)>MpsqPetGrounding.viewDistanceSquared())return;
            var f=motion.frame(x,y,z,yaw,ctx.tickCounter().getGameTimeDeltaPartialTick(false));
            long now=System.currentTimeMillis();String pose;float elapsed;
            if(!actionClip.isEmpty()&&now<actionUntil){pose=actionClip;elapsed=(now-actionStarted)/1000f;}
            else if(now<spawnUntil){pose="spawn";elapsed=(now-animationStarted)/1000f;}
            else {pose=moving?"walk":"idle";elapsed=(now-animationStarted)/1000f;}
            MpsqAccessoryRenderer.drawBlendedPet(ctx,url(trackedId),f[0],f[1],f[2],(float)f[3],pose,elapsed,0.72f,crabWalkBlend);
        });
    }
    private static void tick(Minecraft c){
        crabWalkBlend+=(Math.clamp((moving?1f:0f)-crabWalkBlend,-.125f,.125f));motion.tick(x,y,z,yaw,positioned);String selected=MpsqPetSelectionStore.activeId();
        boolean useDown=c.options.keyUse.isDown(),useStarted=useDown&&!useKeyWasDown;useKeyWasDown=useDown;
        if(c.level==null||c.player==null||!TeamVisibilitySettings.visible()||!isCustomPet(selected)){positioned=false;trackedId="";trackedWorld=null;actionClip="";useKeyWasDown=false;return;}
        if(c.isPaused())return;long now=System.currentTimeMillis();
        if(trackedWorld!=c.level||!selected.equals(trackedId)){trackedWorld=c.level;trackedId=selected;positioned=false;actionClip="";}
        boolean ghast=selected.equals("mpsq_baby_happy_ghast");
        if(!positioned){if(!relocate(c))return;choose(c,now,ghast);animationStarted=now;spawnUntil=now+(long)(Math.max(0,MpsqAccessoryRenderer.clipLength(url(selected),"spawn"))*1000);}
        if(MpsqPetGrounding.blocked(c,x,y,z,radius(),1.4)){if(!relocate(c)){positioned=false;}return;}
        if(useStarted&&c.gui.screen()==null&&now>=actionCooldownUntil&&!actionFor(selected).isEmpty()&&rayHitsPet(c)){actionClip=actionFor(selected);actionTeleported=false;actionStarted=now;actionUntil=now+(long)(clipSeconds(selected,actionClip)*1000);actionCooldownUntil=actionUntil+500;}
        if(!actionClip.isEmpty()&&now>=actionUntil)actionClip="";
        double playerDistance=Math.hypot(x-c.player.getX(),z-c.player.getZ());
        if(playerDistance>20){if(!relocate(c))return;moving=false;choose(c,now,ghast);animationStarted=now;return;}
        boolean returning=playerDistance>10;
        double targetRadius=Math.hypot(targetX-c.player.getX(),targetZ-c.player.getZ());
        moving=false;if(!actionClip.isEmpty()&&now<actionUntil){if(selected.equals("mpsq_exo_the_enderman")&&!actionTeleported&&now-actionStarted>=750){actionTeleported=true;teleportWithEffects(c,now,ghast);}return;}if((returning&&targetRadius>9)||now>=nextTarget)choose(c,now,ghast);if(now<pauseUntil)return;
        double dx=targetX-x,dy=targetY-y,dz=targetZ-z,d=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(d<.08){x=targetX;y=targetY;z=targetZ;pauseUntil=now+ThreadLocalRandom.current().nextLong(900,2300);nextTarget=pauseUntil;return;}
        if(!ghast){var point=navigation().next(c,x,y,z,targetX,targetZ,now,returning,c.player.getX(),c.player.getZ());if(point==null){if(Math.hypot(dx,dz)<.8){nextTarget=now+1200;pauseUntil=nextTarget;}return;}dx=point.x()-x;dz=point.z()-z;dy=0;d=Math.hypot(dx,dz);if(d<.001)return;}
        if(ghast){var point=flightNavigation.next(c,new Vec3(x,y,z),new Vec3(targetX,targetY,targetZ),now);if(point==null)return;dx=point.x-x;dy=point.y-y;dz=point.z-z;d=Math.sqrt(dx*dx+dy*dy+dz*dz);if(d<.001)return;}
        double step=Math.min(returning?.12:(ghast?.045:.055),d),nx=x+dx/d*step,ny=y+dy/d*step,nz=z+dz/d*step;
        if(!ghast){double floor=MpsqPetGrounding.stepY(c,x,y,z,nx,nz,radius(),1.4);if(!Double.isFinite(floor)){navigation().obstructed(now);return;}ny=floor;}
        else if(MpsqPetGrounding.blocked(c,nx,ny,nz,.32,.8)){choose(c,now+500,true);return;}
        x=nx;y=ny;z=nz;moving=true;float wanted=(float)Math.toDegrees(Math.atan2(-dx,dz));yaw+=Math.clamp(net.minecraft.util.Mth.wrapDegrees(wanted-yaw),-7f,7f);
    }
    private static boolean rayHitsPet(Minecraft client){var player=client.player;if(player==null)return false;Vec3 start=player.getEyePosition(1.0f),end=start.add(player.getViewVector(1.0f).scale(4.0));double height=client.level!=null&&client.level.getBlockState(net.minecraft.core.BlockPos.containing(x,y,z)).isAir()?1.85:1.2;AABB bounds=new AABB(x-.48,y,z-.48,x+.48,y+height,z+.48);return bounds.clip(start,end).isPresent();}
    private static void teleportWithEffects(Minecraft c,long now,boolean ghast){
        double oldX=x,oldY=y,oldZ=z;
        if(!relocate(c,true))return;
        teleportEffects(c,oldX,oldY,oldZ,x,y,z,1f);
        choose(c,now,ghast);
    }
    static void teleportEffects(Minecraft c,double ox,double oy,double oz,double nx,double ny,double nz,float size){
        teleportDeparture(c,ox,oy,oz,size);teleportParticles(c,nx,ny,nz,size);
    }
    static void teleportDeparture(Minecraft c,double ox,double oy,double oz,float size){
        teleportParticles(c,ox,oy,oz,size);
        c.level.playLocalSound(ox,oy+.45*size,oz,net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,net.minecraft.sounds.SoundSource.NEUTRAL,.65f,1f,false);
    }
    static void teleportParticles(Minecraft c,double px,double py,double pz,float size){
        var random=ThreadLocalRandom.current();
        for(int i=0;i<28;i++)c.level.addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL,
                px+random.nextDouble(-.22,.22)*size,py+random.nextDouble(.05,1.05)*size,pz+random.nextDouble(-.22,.22)*size,
                random.nextDouble(-.045,.045),random.nextDouble(-.025,.025),random.nextDouble(-.045,.045));
    }
    private static void choose(Minecraft c,long now,boolean ghast){
        var r=ThreadLocalRandom.current();double a=r.nextDouble(Math.PI*2),distance=Math.hypot(x-c.player.getX(),z-c.player.getZ());double radius=distance>10?r.nextDouble(4,8):r.nextDouble(1.5,4.2);
        targetX=c.player.getX()+Math.cos(a)*radius;targetZ=c.player.getZ()+Math.sin(a)*radius;
        if(ghast)targetY=c.player.getY()+r.nextDouble(1.4,2.8);else{double floor=MpsqPetGrounding.groundY(c,targetX,c.player.getY(),targetZ);if(!Double.isFinite(floor)){targetX=x;targetZ=z;floor=y;}targetY=floor;}
        nextTarget=now+8000;pauseUntil=0;
    }
}
