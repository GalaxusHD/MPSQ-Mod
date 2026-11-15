package de.galaxushd.mpsqcamera;

import de.galaxushd.mpsqcamera.compat.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.PlayerSkin;
import java.util.UUID;

/** Draws pets advertised by other authenticated MPSQ clients in the same server world. */
final class MpsqPetPresenceRenderer {
    private static boolean registered;
    private MpsqPetPresenceRenderer() { }
    static void initialize(){
        if(registered)return;registered=true;
        WorldRenderEvents.AFTER_ENTITIES.register(context->{
            net.minecraft.client.Minecraft client=net.minecraft.client.Minecraft.getInstance();var matrices=context.matrixStack();var consumers=context.consumers();
            if(MpsqReplayPlayback.active()||client.level==null||client.player==null||matrices==null||consumers==null||!TeamVisibilitySettings.visible())return;
            var camera=context.camera().getPos();long now=System.currentTimeMillis();
            for(MpsqPetPresenceClient.Snapshot pet:MpsqPetPresenceClient.remote().values()){
                var owner=client.level.getPlayerByUUID(pet.playerId());
                if(owner!=null&&(owner.isSpectator()||owner.isInvisible()))continue;
                if(now-pet.seenAt()>10000||camera.distanceToSqr(pet.x(),pet.y(),pet.z())>MpsqPetGrounding.viewDistanceSquared())continue;
                int light=net.minecraft.util.LightCoordsUtil.getLightCoords(client.level,net.minecraft.core.BlockPos.containing(pet.x(),pet.y(),pet.z()));
                float px=(float)(pet.x()-camera.x),py=(float)(pet.y()-camera.y),pz=(float)(pet.z()-camera.z);
                if(MpsqMiniYouPetRenderer.isMiniYouId(pet.petId())){renderMiniYou(client,pet,px,py,pz,light,matrices,consumers,now);continue;}
                switch(pet.petId()){
                    case "nogs_budgie" -> {
                        double floor = MpsqPetGrounding.groundY(client, pet.x(), pet.y(), pet.z());
                        String pose = !Double.isFinite(floor) || pet.y() > floor + 0.12 ? "fly" : "walk";
                        MpsqBudgieModel.render(matrices,consumers,light,px,py,pz,pet.yaw(),0.58f,pet.variant(),pose,now/1000.0);
                    }
                    case "nogs_owl_barn","nogs_owl_horned","nogs_owl_long_eared","nogs_owl_snowy" -> {MpsqOwlModel.select(pet.petId());MpsqOwlModel.render(matrices,consumers,light,px,py,pz,pet.yaw(),1.0f,MpsqOwlPetRenderer.animation(client,pet.x(),pet.y(),pet.z()),now/1000.0);}
            case "mpsq_tiger","mpsq_tiger_baby","mpsq_crab","mpsq_penguin","mpsq_baby_happy_ghast","mpsq_colin_the_creeper","mpsq_exo_the_enderman","mpsq_slak_the_skeleton","mpsq_zekk_the_zombie" -> MpsqAccessoryRenderer.drawCinematicModel(context,MpsqCustomPetRenderer.url(pet.petId()),pet.x(),pet.y(),pet.z(),pet.yaw(),"walk",(now%100000L)/1000f,0,0,0.72f);
                    case "nogs_hedgehog" -> MpsqHedgehogModel.render(matrices,consumers,light,px,py,pz,pet.yaw(),1.0f,"walk",now/1000.0);
                    case "nocsy_otter" -> MpsqOtterModel.render(matrices,consumers,light,px,py,pz,pet.yaw(),0.62f,"walk",now/1000.0);
                    default -> renderMini(client,pet,px,py,pz,light,matrices,consumers,now);
                }
            }
        });
    }
    private static void renderMiniYou(Minecraft client,MpsqPetPresenceClient.Snapshot presence,float x,float y,float z,int light,
                                      com.mojang.blaze3d.vertex.PoseStack matrices,
                                      de.galaxushd.mpsqcamera.compat.MultiBufferSource consumers,long now){
        MpsqPetCatalog.Pet pet=MpsqPetCatalog.byId(presence.petId());if(pet==null)return;
        MpsqNpcSkinRenderer.Skin skin=MpsqPetRenderer.skinFor(pet);
        if("__player__".equals(pet.texture())){
            var handler=client.getConnection();var entry=handler==null?null:handler.getPlayerInfo(presence.playerId());
            if(entry==null)return;PlayerSkin textures=entry.getSkin();skin=new MpsqNpcSkinRenderer.Skin(textures.body().texturePath(),textures.model()==net.minecraft.world.entity.player.PlayerModelType.SLIM);
        }
        MpsqMiniYouModel.render(matrices,consumers,light,pet.id(),skin==null?null:skin.texture(),skin!=null&&skin.slim(),x,y,z,presence.yaw(),0.48f,"walk",now/1000.0);
    }
    private static void renderMini(Minecraft client,MpsqPetPresenceClient.Snapshot pet,float x,float y,float z,int light,
                                   com.mojang.blaze3d.vertex.PoseStack matrices,
                                   de.galaxushd.mpsqcamera.compat.MultiBufferSource consumers,long now){
        MpsqPetCatalog.Pet catalog=MpsqPetCatalog.byId(pet.petId());if(catalog==null||catalog.group()==MpsqPetCatalog.Group.ANIMAL)return;
        net.minecraft.resources.Identifier texture=catalog.textureId();boolean slim=false;
        if("__player__".equals(catalog.texture())){
            var handler=client.getConnection();var entry=handler==null?null:handler.getPlayerInfo(pet.playerId());
            if(entry==null)return;PlayerSkin skin=entry.getSkin();texture=skin.body().texturePath();slim=skin.model()==net.minecraft.world.entity.player.PlayerModelType.SLIM;
        }
        MpsqNpcSkinRenderer.Skin skin=new MpsqNpcSkinRenderer.Skin(texture,slim);
        var state=MpsqNpcSkinRenderer.createState(skin,pet.yaw(),0,(now%1_000_000L)/50.0f,false);
        state.walkAnimationPos=(now%1_000_000L)/50.0f*0.08f;state.walkAnimationSpeed=0.12f;
        MpsqPetModelContext.render(()->MpsqNpcSkinRenderer.render(state,x,y,z,0.42f,matrices,consumers,light,0xFFFFFFFF));
    }
}

