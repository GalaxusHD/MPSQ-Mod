package de.galaxushd.mpsqcamera;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import de.galaxushd.mpsqcamera.compat.WorldRenderEvents;

/** Local mob actors and editor paths; no server mobs are spawned or modified. */
final class MpsqSceneWorldNpcs {
    private record Actor(String fingerprint,LivingEntity entity){}
    private static final Map<String,Actor> mobs=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private static int nextId=-1900010000,ticks;
    private static final Map<String,MpsqScenes.Profile> ambient=new LinkedHashMap<>();
    private static final Map<String,MpsqScenes.Profile> cinematicPaths=new LinkedHashMap<>();
    private static final Map<String,MpsqScenes.Profile> visibleAmbient=new LinkedHashMap<>();
    static boolean hasGlowingModel(){return visibleAmbient.entrySet().stream().anyMatch(e->MpsqNpcGlow.enabled("cinematic:"+e.getKey(),e.getValue().npc));}
    static void invalidate(){ticks=0;}

    static void initialize(){
        ClientTickEvents.END_CLIENT_TICK.register(c->{
            if(c.level!=world||!TeamVisibilitySettings.inArea()) {mobs.values().forEach(a->a.entity.discard());mobs.clear();ambient.clear();cinematicPaths.clear();visibleAmbient.clear();ticks=0;world=c.level;}
            if(c.level==null||!TeamVisibilitySettings.inArea())return;
            if(ticks++%20==0){
                try{var refreshed=new LinkedHashMap<String,MpsqScenes.Profile>();cinematicPaths.clear();
                    for(var scene:MpsqCinematicStore.load())if(scene.npc.placed&&scene.npc.linkedNpc.isBlank()&&scene.dimension.equals(c.level.dimension().identifier().toString())){
                        var current=MpsqScenes.fromScene(scene).profile();cinematicPaths.put(scene.id,current);if(!"ALL".equals(scene.npc.visibility))continue;var old=ambient.get(scene.id);refreshed.put(scene.id,old!=null&&MpsqScenes.JSON.toJson(old).equals(MpsqScenes.JSON.toJson(current))?old:current);
                    }ambient.clear();ambient.putAll(refreshed);
                }catch(Exception ignored){ambient.clear();}
            }
            var candidates=new LinkedHashMap<String,MpsqScenes.Profile>();visibleAmbient.clear();
            ambient.forEach((id,p)->{if(MpsqCinematicActors.scenePose(id)==null){candidates.put("cinematic:"+id,p);visibleAmbient.put(id,p);}});
            var seen=new HashSet<String>();
            for(var element:MpsqAccessoryRenderer.npcsSnapshot()){
                var row=MpsqScenes.apply(element.getAsJsonObject());String id=MpsqScenes.str(row,"id","");
                var p=MpsqScenes.stored(id);
                if(p!=null&&!p.deleted&&!(row.has("scenes_hidden")&&row.get("scenes_hidden").getAsBoolean())&&MpsqCinematicActors.linkedPose(id)==null)MpsqSeagullDrops.timeline("npc:"+id,p.npc,MpsqScenes.pose(p),p.scale,MpsqScenes.time());
                if(p==null||(!p.npc.category.equals("mob")&&!p.npc.category.startsWith("npc_skin"))||p.deleted||row.has("scenes_hidden")&&row.get("scenes_hidden").getAsBoolean())continue;
                candidates.put(id,p);
            }
            for(var entry:candidates.entrySet()){
                String id=entry.getKey();var p=entry.getValue();MpsqSeagullDrops.timeline(id,p.npc,MpsqScenes.pose(p),p.scale,MpsqScenes.time());if(p.npc.category.equals("npc_model"))continue;
                seen.add(id);String fingerprint=p.npc.category+p.npc.type+p.npc.url+Arrays.toString(p.npc.equipment)+p.npc.name;
                Actor actor=mobs.get(id);
                if(actor==null||!actor.fingerprint.equals(fingerprint)){
                    if(actor!=null)actor.entity.discard();var entity=MpsqCinematicActors.create(p.npc);if(entity==null)continue;
                    entity.setId(nextId--);var first=MpsqScenes.pose(p);MpsqNpcMotion.place(entity,first,false);c.level.addEntity(entity);actor=new Actor(fingerprint,entity);mobs.put(id,actor);
                }
                var pose=MpsqCinematicActors.linkedPose(id);if(pose==null)pose=MpsqScenes.pose(p);
                var entity=actor.entity;MpsqNpcGlow.apply(entity,id,p.npc);
                entity.setInvisible(MpsqScenesClips.get(pose.clip())!=null);if(MpsqScenesClips.get(pose.clip())!=null){entity.setGlowingTag(false);entity.removeTag("mpsq_npc_glow_on");}
                entity.setPose(pose.clip().equals("crouch")?net.minecraft.world.entity.Pose.CROUCHING:pose.clip().equals("lie")?net.minecraft.world.entity.Pose.SLEEPING:pose.clip().equals("swim")?net.minecraft.world.entity.Pose.SWIMMING:net.minecraft.world.entity.Pose.STANDING);
                if(entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE)!=null)entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE).setBaseValue(p.scale);
                MpsqNpcMotion.place(entity,pose,entity.tickCount>0);MpsqMotionCapture.apply(entity,pose);
                var pos=MpsqScenes.position(p,MpsqScenes.time());var prev=MpsqScenes.position(p,Math.max(0,MpsqScenes.time()-.05));
                if(pose.recorded()==null&&pose.clip().equals("swing")&&!entity.isSwinging())entity.swing(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,true);
                entity.walkAnimation.update((float)Math.min(1,Math.hypot(pos.x-prev.x,pos.z-prev.z)*12),.25f,1);
                if(entity instanceof net.minecraft.world.entity.npc.villager.AbstractVillager v)v.setUnhappyCounter(pose.clip().equals("shake_head")?20:0);
                if(entity instanceof net.minecraft.world.entity.monster.illager.SpellcasterIllager v)v.getEntityData().set(de.galaxushd.mpsqcamera.mixin.client.CinematicSpellAccessor.mpsq$spellData(),(byte)(pose.clip().equals("cast")?1:0));
            }
            mobs.entrySet().removeIf(e->{if(seen.contains(e.getKey()))return false;e.getValue().entity.discard();return true;});
        });
        WorldRenderEvents.AFTER_ENTITIES.register(ctx->{
            if(TeamVisibilitySettings.inArea()){for(var element:MpsqAccessoryRenderer.npcsSnapshot()){var p=MpsqScenes.stored(MpsqScenes.str(element.getAsJsonObject(),"id",""));if(p!=null&&!p.deleted&&!MpsqScenes.apply(element.getAsJsonObject()).get("scenes_hidden").getAsBoolean()){var linked=MpsqCinematicActors.linkedPose(MpsqScenes.str(element.getAsJsonObject(),"id",""));MpsqNpcLabels.draw(ctx,p.npc,linked!=null?linked:MpsqScenes.pose(p),p.scale);}}}
            if(TeamVisibilitySettings.inArea())for(var entry:visibleAmbient.entrySet()){
                var p=entry.getValue();var pose=MpsqScenes.pose(p);MpsqNpcLabels.draw(ctx,p.npc,pose,p.scale);
                if(p.npc.category.equals("npc_model"))MpsqAccessoryRenderer.drawCinematicModel(ctx,MpsqAccessoryRenderer.assetPreviewUrl(p.npc.type,p.npc.url),pose.x(),pose.y(),pose.z(),pose.yaw(),pose.clip(),pose.clipTime(),pose.headYaw(),pose.pitch(),p.scale,MpsqNpcGlow.enabled("cinematic:"+entry.getKey(),p.npc)?MpsqNpcGlow.color(p.npc):0);
                else if(p.npc.category.startsWith("npc_skin")&&MpsqScenesClips.get(pose.clip())!=null){var actor=mobs.get("cinematic:"+entry.getKey());if(actor!=null&&actor.entity instanceof net.minecraft.client.player.RemotePlayer player)MpsqAccessoryRenderer.drawScenesActor(ctx,new MpsqNpcSkinRenderer.Skin(player.getSkin().body().texturePath(),player.getSkin().model()==net.minecraft.world.entity.player.PlayerModelType.SLIM),pose,p.scale,MpsqNpcGlow.enabled("cinematic:"+entry.getKey(),p.npc)?MpsqNpcGlow.color(p.npc):0,p.npc);}
            }
            if(!MpsqScenes.editorVisible()||!TeamVisibilitySettings.inArea()||ctx.matrixStack()==null||ctx.consumers()==null)return;
            var lines=ctx.consumers().getBuffer(net.minecraft.client.renderer.rendertype.RenderTypes.lines());var m=ctx.matrixStack();var cam=ctx.camera().getPos();
            var paths=new ArrayList<MpsqScenes.Profile>(cinematicPaths.values());for(var element:MpsqAccessoryRenderer.npcsSnapshot()){var p=MpsqScenes.stored(MpsqScenes.str(element.getAsJsonObject(),"id",""));if(p!=null&&!p.deleted)paths.add(p);}
            for(var p:paths){
                double end=p.npc.duration();MpsqPathSeconds.draw(ctx,p.npc);
                var prev=p.npc.position(p.npc.movementStart,false,"FORWARD");
                int count=Math.max(1,Math.min(512,(int)((end-p.npc.movementStart)*24)));
                for(int i=1;i<=count;i++){var next=p.npc.position(p.npc.movementStart+(end-p.npc.movementStart)*i/count,false,"FORWARD");MpsqCinematicMarkers.segment(lines,m,cam,prev.x,prev.y+.08,prev.z,next.x,next.y+.08,next.z,0xFF33FFCC);prev=next;}
                for(var point:p.npc.route)if(point!=null){MpsqCinematicMarkers.segment(lines,m,cam,point.x-.3,point.y+.08,point.z,point.x+.3,point.y+.08,point.z,0xFF33FFCC);MpsqCinematicMarkers.segment(lines,m,cam,point.x,point.y+.08,point.z-.3,point.x,point.y+.08,point.z+.3,0xFF33FFCC);}
            }
        });
    }
}
