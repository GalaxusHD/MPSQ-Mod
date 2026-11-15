package de.galaxushd.mpsqcamera;

import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.PlayerSkin;

/** Playback-only actors. Real server entities and inventories are never touched. */
final class MpsqCinematicActors {
    private static LivingEntity active;
    private static MpsqCinematicScene.Npc model,settings;
    record Pose(double x,double y,double z,float yaw,float headYaw,float pitch,String clip,float clipTime,MpsqMotionCapture.Frame recorded){
        Pose(double x,double y,double z,float yaw,float headYaw,float pitch,String clip,float clipTime){this(x,y,z,yaw,headYaw,pitch,clip,clipTime,null);}
    }
    private static Pose pose;
    private static String sceneId;
    static Pose scenePose(String id){return java.util.Objects.equals(id,sceneId)?pose:null;}
    static void editor(MpsqCinematicScene scene){if(!java.util.Objects.equals(sceneId,scene.id)||settings==null)start(scene);advance(0);}
    private static double previousTime=-1,steps;
    private static long animationTick=-1;
    static Pose linkedPose(String id){return settings!=null&&settings.linkedNpc!=null&&!settings.linkedNpc.isBlank()&&settings.linkedNpc.equals(id)?pose:null;}
    static java.util.List<String> clips(MpsqCinematicScene.Npc n){
        if(n.category.equals("npc_model")){var clips=new java.util.ArrayList<>(MpsqAccessoryRenderer.cinematicClips(MpsqAccessoryRenderer.assetPreviewUrl(n.type,n.url)));if(MpsqSeagullDrops.seagull(n))clips.add(MpsqSeagullDrops.ACTION);return clips;}
        if(n.category.startsWith("npc_skin")){var result=new java.util.ArrayList<String>(java.util.List.of("idle","lie","swim","jump","swing"));for(var clip:MpsqScenesClips.all())result.add(clip.id());return result;}
        if(n.type.contains("villager")||n.type.contains("wandering_trader"))return java.util.List.of("idle","shake_head","jump");
        if(n.type.contains("evoker")||n.type.contains("illusioner"))return java.util.List.of("idle","cast","jump");
        return java.util.List.of("idle","jump");
    }

    private static net.minecraft.client.multiplayer.ClientLevel world;
    static LivingEntity create(MpsqCinematicScene.Npc n){
        var c=Minecraft.getInstance();if(c.level==null)return null;
        LivingEntity result=null;
        if(n.category.startsWith("npc_skin")){
            result=new RemotePlayer(c.level,new GameProfile(UUID.randomUUID(),n.name.isBlank()?"NPC":n.name)){
                @Override public net.minecraft.network.chat.Component getName(){return n.name.equals("NPC")&&c.player!=null?(n.rankTag.isBlank()?MpsqNametags.forPlayer(c.player.getGameProfile().name()):net.minecraft.network.chat.Component.literal(c.player.getGameProfile().name())):net.minecraft.network.chat.Component.literal(n.name);}
                @Override public net.minecraft.network.chat.Component getDisplayName(){return getName();}
                @Override public boolean isModelPartShown(net.minecraft.world.entity.player.PlayerModelPart part){return true;}
                @Override public PlayerSkin getSkin(){
                    if(n.type.equals("self")&&c.player!=null){
                        var original=c.player.getSkin();
                        return new PlayerSkin(original.body(),original.cape(),original.elytra(),
                            original.model(),false);
                    }
                    MpsqAccessoryRenderer.prepareCinematicSkin(n.url,n.category.endsWith("slim"));
                    var skin=MpsqNpcSkinRenderer.get(n.url,n.category.endsWith("slim"));
                    return skin==null?super.getSkin():new PlayerSkin(new net.minecraft.core.ClientAsset.ResourceTexture(skin.texture(),skin.texture()),null,null,
                        skin.slim()?net.minecraft.world.entity.player.PlayerModelType.SLIM:net.minecraft.world.entity.player.PlayerModelType.WIDE,false);
                }
            };
        }else if(n.category.equals("mob")){
            var id=Identifier.tryParse(n.type);
            if(id!=null&&BuiltInRegistries.ENTITY_TYPE.containsKey(id)){
                var entity=BuiltInRegistries.ENTITY_TYPE.getValue(id).create(c.level,EntitySpawnReason.LOAD);
                if(entity instanceof LivingEntity living)result=living;
            }
        }
        if(result==null)return null;
        result.addTag("mpsq_cinematic_actor");
        result.setNoGravity(true);result.setSilent(true);
        MpsqNpcMotion.place(result,new Pose(n.x,n.y,n.z,n.yaw,n.headYaw,n.pitch,"idle",0),false);
        if(result instanceof Mob mob)mob.setNoAi(true);
        EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND};
        for(int i=0;i<6;i++)result.setItemSlot(slots[i],MpsqCinematicNpcScreen.stack(n.equipment[i]));
        result.setCustomName(n.category.startsWith("npc_skin")?result.getName():net.minecraft.network.chat.Component.literal(n.name));
        result.setCustomNameVisible(false);MpsqNpcGlow.apply(result,"",n);
        return result;
    }
    static void start(MpsqCinematicScene scene){
        stop();var n=scene.npc==null?null:new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(scene.npc),MpsqCinematicScene.Npc.class);var c=Minecraft.getInstance();
        if(n==null||!n.placed||c.level==null)return;n.validate();world=c.level;settings=n;sceneId=scene.id;
        if(n.linkedNpc!=null&&!n.linkedNpc.isBlank()){
            boolean found=false;for(var e:MpsqAccessoryRenderer.npcsSnapshot()){var row=MpsqScenes.apply(e.getAsJsonObject());if(row.has("id")&&n.linkedNpc.equals(row.get("id").getAsString())){found=true;n.x=coordinate(row,"x");n.y=coordinate(row,"y");n.z=coordinate(row,"z");}}
            if(!found){stop();return;}model=n;return;
        }
        if(n.category.equals("npc_model")){n.url=MpsqAccessoryRenderer.assetPreviewUrl(n.type,n.url);model=n;return;}
        active=create(n);if(active==null)return;if(active.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE)!=null)active.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SCALE).setBaseValue(n.scenesScale);
        active.setId(-1900000002);active.snapTo(n.x,n.y,n.z,n.yaw,0);active.setYHeadRot(n.yaw);active.setYBodyRot(n.yaw);c.level.addEntity(active);
    }
    private static double coordinate(com.google.gson.JsonObject o,String axis){return o.has("world_"+axis)?o.get("world_"+axis).getAsDouble():o.get(axis).getAsDouble()+(axis.equals("y")?0:.5);}
    static void stop(){if(sceneId!=null)MpsqSeagullDrops.forget("play:"+sceneId);if(active!=null)active.discard();active=null;model=null;settings=null;pose=null;sceneId=null;world=null;previousTime=-1;animationTick=-1;steps=0;}
    static void advance(double time){
        if(settings==null)return;var c=Minecraft.getInstance();if(c.level!=world||!TeamVisibilitySettings.inArea()){stop();return;}
        var n=settings;var point=n.position(time);var before=n.position(Math.max(0,time-.05));
        double dx=point.x-before.x,dz=point.z-before.z;boolean moving=Math.abs(dx)+Math.abs(dz)>1e-6;
        var action=n.action(time);if(action!=null&&MpsqSeagullDrops.ACTION.equals(action.clip))action=null;String clip=action==null?(moving&&clips(n).contains("walk")?"walk":n.scenesClip):action.clip;
        double age=action==null?time:time-action.time;String look=action==null||action.look==null||action.look.equals("DEFAULT")?n.look:action.look;
        float yaw=moving?(float)Math.toDegrees(Math.atan2(-dx,dz)):n.yaw,head=n.headYaw,pitch=n.pitch;
        if(!look.equals("FIXED")){
            var target=look.startsWith("CAMERA")?c.getCameraEntity():c.player;
            if(target!=null&&c.player!=null&&MpsqNpcManager.withinLookRange(c.player.position(),new net.minecraft.world.phys.Vec3(point.x,point.y,point.z))){double tx=target.getX()-point.x,tz=target.getZ()-point.z,ty=target.getEyeY()-(point.y+1.5);float targetYaw=(float)Math.toDegrees(Math.atan2(-tx,tz));pitch=(float)-Math.toDegrees(Math.atan2(ty,Math.sqrt(tx*tx+tz*tz)));
                if(look.endsWith("HEAD")){head=net.minecraft.util.Mth.wrapDegrees(targetYaw-yaw);head=Math.max(-75,Math.min(75,head));}else{yaw=targetYaw;head=0;}}
        }
        if(pose!=null&&previousTime>=0&&time>=previousTime){
            float blend=(float)(1-Math.exp(-Math.min(.1,time-previousTime)/.16));
            yaw=pose.yaw+(float)MpsqCinematicScene.angleDelta(pose.yaw,yaw)*blend;
            head=pose.headYaw+(float)MpsqCinematicScene.angleDelta(pose.headYaw,head)*blend;
            pitch=pose.pitch+(pitch-pose.pitch)*blend;
        }
        double jump=clip.equals("jump")?Math.max(0,Math.sin(Math.min(1,age/(action==null?1:action.duration))*Math.PI))*.8:0;
        pose=new Pose(point.x,point.y+jump,point.z,yaw,head,pitch,clip,action==null?loopTime(n,clip,age):(float)age);
        var captured=MpsqMotionCapture.sample(n.recording,time-n.movementStart,n.scenesMotionLoop,n.scenesDirection);if(captured!=null){pose=captured;clip=pose.clip();}
        if(MpsqCinematicPreview.active())MpsqSeagullDrops.timeline("play:"+sceneId,n,pose,n.scenesScale,time);
        if(active!=null){
            MpsqNpcGlow.apply(active,"cinematic:"+sceneId,n);
            active.setInvisible(MpsqScenesClips.get(clip)!=null);if(MpsqScenesClips.get(clip)!=null){active.setGlowingTag(false);active.removeTag("mpsq_npc_glow_on");}
            active.setPose(clip.equals("crouch")?net.minecraft.world.entity.Pose.CROUCHING:clip.equals("lie")?net.minecraft.world.entity.Pose.SLEEPING:clip.equals("swim")?net.minecraft.world.entity.Pose.SWIMMING:net.minecraft.world.entity.Pose.STANDING);
            MpsqNpcMotion.place(active,pose,false);MpsqMotionCapture.apply(active,pose);
            long tick=(long)(time*20);if(tick!=animationTick){animationTick=tick;active.walkAnimation.update(moving?(float)Math.min(1,Math.hypot(dx,dz)*12):0,.25f,1);
                if(pose.recorded()==null&&clip.equals("swing")&&!active.isSwinging())active.swing(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.component.SwingAnimation.DEFAULT,true);
                if(active instanceof net.minecraft.world.entity.npc.villager.AbstractVillager v)v.setUnhappyCounter(clip.equals("shake_head")?20:0);
                if(active instanceof net.minecraft.world.entity.monster.illager.SpellcasterIllager v)v.getEntityData().set(de.galaxushd.mpsqcamera.mixin.client.CinematicSpellAccessor.mpsq$spellData(),(byte)(clip.equals("cast")?1:0));
            }
        }
        if(previousTime>=0&&time>previousTime&&time-previousTime<.5){var last=n.position(previousTime);steps+=Math.sqrt(Math.pow(point.x-last.x,2)+Math.pow(point.z-last.z,2));
            if(moving&&steps>=.8){steps%=.8;var block=c.level.getBlockState(net.minecraft.core.BlockPos.containing(point.x,point.y-.1,point.z));var sound=block.getSoundType();c.level.playLocalSound(point.x,point.y,point.z,sound.getStepSound(),net.minecraft.sounds.SoundSource.NEUTRAL,sound.getVolume()*.15f,sound.getPitch(),false);
                if(!block.isAir())c.level.addParticle(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,block),point.x,point.y+.05,point.z,0,.03,0);
            }
        }previousTime=time;
    }
    static float loopTime(MpsqCinematicScene.Npc n,String clip,double time){var source=MpsqScenesClips.get(clip);double length=source!=null?source.length():MpsqAccessoryRenderer.clipLength(MpsqAccessoryRenderer.assetPreviewUrl(n.type,n.url),clip);return (float)(length>0?time%length:time);}
    static boolean hasGlowingModel(){return model!=null&&MpsqNpcGlow.enabled("cinematic:"+sceneId,model);}
    static void initialize(){
        de.galaxushd.mpsqcamera.compat.WorldRenderEvents.AFTER_ENTITIES.register(ctx->{
            if(world!=null&&(world!=Minecraft.getInstance().level||!TeamVisibilitySettings.inArea())){stop();return;}
            if(settings!=null&&pose!=null&&settings.linkedNpc.isBlank())MpsqNpcLabels.draw(ctx,settings,pose,settings.scenesScale);
            if(active instanceof RemotePlayer player&&pose!=null&&MpsqScenesClips.get(pose.clip())!=null&&(MpsqCinematicPreview.active()||MpsqCinematicMarkers.visible()))
                MpsqAccessoryRenderer.drawScenesActor(ctx,new MpsqNpcSkinRenderer.Skin(player.getSkin().body().texturePath(),player.getSkin().model()==net.minecraft.world.entity.player.PlayerModelType.SLIM),pose,settings.scenesScale,MpsqNpcGlow.enabled("cinematic:"+sceneId,settings)?MpsqNpcGlow.color(settings):0,settings);
            if(model!=null&&pose!=null&&(model.linkedNpc==null||model.linkedNpc.isBlank())&&(MpsqCinematicPreview.active()||MpsqCinematicMarkers.visible()))MpsqAccessoryRenderer.drawCinematicModel(ctx,model.url,pose.x,pose.y,pose.z,pose.yaw,pose.clip,pose.clipTime,pose.headYaw,pose.pitch,model.scenesScale,MpsqNpcGlow.enabled("cinematic:"+sceneId,model)?MpsqNpcGlow.color(model):0);
        });
    }
}

