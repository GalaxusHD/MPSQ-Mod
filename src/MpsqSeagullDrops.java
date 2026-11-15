package de.galaxushd.mpsqcamera;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.block.Block;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import de.galaxushd.mpsqcamera.compat.WorldRenderEvents;

/** Local visual projectiles. The supplied model's hitbox is 16 x 2 x 16 pixels. */
final class MpsqSeagullDrops {
    static final String ACTION="seagull_drop", URL="builtin://mpsq/nm_seagull_poop";
    static final double IMPACT_SECONDS=.15, LANDED_SECONDS=10;
    private static final List<Drop> drops=new ArrayList<>();
    private static final Map<String,Double> clocks=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    static final class Drop {
        Vec3 position,previous;
        final float scale,yaw;
        double speed,age,stateAge;
        boolean impacted,entityHit;
        Drop(Vec3 position,float scale,float yaw){this.position=this.previous=position;this.scale=scale;this.yaw=yaw;}
        String clip(){return !impacted?"idle":stateAge<IMPACT_SECONDS?"plop":"idle_landed";}
        boolean expired(){return impacted&&stateAge>=IMPACT_SECONDS+(entityHit?0:LANDED_SECONDS)||age>60;}
    }
    static boolean seagull(MpsqCinematicScene.Npc n){return n.category.equals("npc_model")&&n.type.equals("nm_seagull");}
    static List<MpsqReplayData.DropState> replaySnapshot(){List<MpsqReplayData.DropState> out=new ArrayList<>(drops.size());for(Drop d:drops)out.add(new MpsqReplayData.DropState(d.clip(),d.position.x,d.position.y,d.position.z,d.yaw,d.scale,(float)(d.impacted?d.stateAge:d.age)));return List.copyOf(out);}
    static void reset(){drops.clear();clocks.clear();world=Minecraft.getInstance().level;}
    static void forget(String key){clocks.remove(key);}
    static void drop(MpsqCinematicScene.Npc n,MpsqCinematicActors.Pose pose,float scale){
        var c=Minecraft.getInstance();if(c.level==null||!TeamVisibilitySettings.inArea()||!seagull(n))return;
        if(world!=c.level)reset();
        if(drops.size()>=256)return;
        drops.add(new Drop(new Vec3(pose.x(),pose.y(),pose.z()),scale,pose.yaw()));
        MpsqAccessoryRenderer.cinematicClips(URL);
    }
    /** Crossing detection avoids frame-rate-dependent spawns and permits replay after rewind. */
    static void timeline(String key,MpsqCinematicScene.Npc n,MpsqCinematicActors.Pose pose,float scale,double time){
        if(!seagull(n)||!TeamVisibilitySettings.inArea())return;
        if(world!=Minecraft.getInstance().level)reset();
        Double previous=clocks.put(key,time);
        double from=previous==null?(key.startsWith("play:")?-1e-6:time-.05):time<previous?time-1e-6:previous;
        for(var action:n.actions)if(ACTION.equals(action.clip)&&action.time>from&&action.time<=time)drop(n,pose,scale);
    }
    static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(world!=c.level||!TeamVisibilitySettings.inArea()){reset();return;}
            if(c.level==null||c.isPaused())return;
            for(var d:drops)step(d);
            drops.removeIf(Drop::expired);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(ctx->{
            if(!TeamVisibilitySettings.inArea()||world!=Minecraft.getInstance().level)return;
            float partial=Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            for(var d:drops){
                var pos=d.previous.lerp(d.position,partial);
                float elapsed=(float)(d.impacted?Math.max(0,d.stateAge-.05+partial*.05):d.age);
                String clip=!d.impacted?"idle":elapsed<IMPACT_SECONDS?"plop":"idle_landed";
                MpsqAccessoryRenderer.drawCinematicModel(ctx,URL,pos.x,pos.y,pos.z,d.yaw,clip,elapsed,0,0,d.scale);
            }
        });
    }
    static void step(Drop d){
        d.previous=d.position;d.age+=.05;
        if(d.impacted){d.stateAge+=.05;return;}
        d.speed=Math.min(30,d.speed+9.81*.05);
        Vec3 end=d.position.add(0,-d.speed*.05,0);
        var c=Minecraft.getInstance();double radius=.5*d.scale,height=.125*d.scale;
        var sweep=new AABB(d.position,end).inflate(radius,height,radius);
        Vec3 nearest=null;double distance=Double.POSITIVE_INFINITY;boolean entity=false;
        for(var block:BlockPos.betweenClosed(BlockPos.containing(sweep.minX,sweep.minY,sweep.minZ),BlockPos.containing(sweep.maxX,sweep.maxY,sweep.maxZ))){
            var state=c.level.getBlockState(block);
            if(!Block.isShapeFullBlock(state.getCollisionShape(c.level,block)))continue;
            Vec3 hit=hit(new AABB(block),d.position,end,radius,height);
            if(hit!=null&&d.position.distanceToSqr(hit)<distance){nearest=hit;distance=d.position.distanceToSqr(hit);entity=false;}
        }
        for(var target:c.level.getEntities((net.minecraft.world.entity.Entity)null,sweep,e->e.isAlive()&&!e.isSpectator()&&e.isPickable()&&e.getId()!=-1900000001)){
            Vec3 hit=hit(target.getBoundingBox(),d.position,end,radius,height);
            if(hit!=null&&d.position.distanceToSqr(hit)<distance){nearest=hit;distance=d.position.distanceToSqr(hit);entity=true;}
        }
        if(nearest!=null){d.position=nearest;d.previous=nearest;d.impacted=true;d.entityHit=entity;d.stateAge=0;}
        else d.position=end;
    }
    /** Sweep the complete model hitbox, including high-speed drops across thin entity bounds. */
    static Vec3 hit(AABB target,Vec3 start,Vec3 end,double radius,double height){
        var expanded=new AABB(target.minX-radius,target.minY-height,target.minZ-radius,target.maxX+radius,target.maxY,target.maxZ+radius);
        return expanded.contains(start)?start:expanded.clip(start,end).orElse(null);
    }
}
