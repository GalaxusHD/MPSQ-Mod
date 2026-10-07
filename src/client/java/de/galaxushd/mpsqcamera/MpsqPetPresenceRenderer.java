package de.galaxushd.mpsqcamera;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.math.BlockPos;
import java.util.UUID;

/** Draws pets advertised by other authenticated MPSQ clients in the same server world. */
final class MpsqPetPresenceRenderer {
    private static boolean registered;
    private MpsqPetPresenceRenderer() { }
    static void initialize(){
        if(registered)return;registered=true;
        WorldRenderEvents.AFTER_ENTITIES.register(context->{
            MinecraftClient client=MinecraftClient.getInstance();var matrices=context.matrixStack();var consumers=context.consumers();
            if(client.world==null||client.player==null||matrices==null||consumers==null||!TeamVisibilitySettings.visible())return;
            var camera=context.camera().getPos();long now=System.currentTimeMillis();
            for(MpsqPetPresenceClient.Snapshot pet:MpsqPetPresenceClient.remote().values()){
                if(now-pet.seenAt()>10000||camera.squaredDistanceTo(pet.x(),pet.y(),pet.z())>4096)continue;
                int light=WorldRenderer.getLightmapCoordinates(client.world,BlockPos.ofFloored(pet.x(),pet.y(),pet.z()));
                float px=(float)(pet.x()-camera.x),py=(float)(pet.y()-camera.y),pz=(float)(pet.z()-camera.z);
                if(MpsqMiniYouPetRenderer.isMiniYouId(pet.petId())){renderMiniYou(client,pet,px,py,pz,light,matrices,consumers,now);continue;}
                switch(pet.petId()){
                    case "nogs_budgie" -> {
                        double floor = MpsqPetGrounding.groundY(client, pet.x(), pet.y(), pet.z());
                        String pose = !Double.isFinite(floor) || pet.y() > floor + 0.12 ? "fly" : "walk";
                        MpsqBudgieModel.render(matrices,consumers,light,px,py,pz,pet.yaw(),0.58f,pet.variant(),pose,now/1000.0);
                    }
                    case "nogs_hedgehog" -> MpsqHedgehogModel.render(matrices,consumers,light,px,py,pz,pet.yaw(),1.0f,"walk",now/1000.0);
                    case "nocsy_otter" -> MpsqOtterModel.render(matrices,consumers,light,px,py,pz,pet.yaw(),0.62f,"walk",now/1000.0);
                    default -> renderMini(client,pet,px,py,pz,light,matrices,consumers,now);
                }
            }
        });
    }
    private static void renderMiniYou(MinecraftClient client,MpsqPetPresenceClient.Snapshot presence,float x,float y,float z,int light,
                                      net.minecraft.client.util.math.MatrixStack matrices,
                                      net.minecraft.client.render.VertexConsumerProvider consumers,long now){
        MpsqPetCatalog.Pet pet=MpsqPetCatalog.byId(presence.petId());if(pet==null)return;
        MpsqNpcSkinRenderer.Skin skin=MpsqPetRenderer.skinFor(pet);
        if("__player__".equals(pet.texture())){
            var handler=client.getNetworkHandler();var entry=handler==null?null:handler.getPlayerListEntry(presence.playerId());
            if(entry==null)return;SkinTextures textures=entry.getSkinTextures();skin=new MpsqNpcSkinRenderer.Skin(textures.texture(),textures.model()==SkinTextures.Model.SLIM);
        }
        MpsqMiniYouModel.render(matrices,consumers,light,pet.id(),skin==null?null:skin.texture(),skin!=null&&skin.slim(),x,y,z,presence.yaw(),0.48f,"walk",now/1000.0);
    }
    private static void renderMini(MinecraftClient client,MpsqPetPresenceClient.Snapshot pet,float x,float y,float z,int light,
                                   net.minecraft.client.util.math.MatrixStack matrices,
                                   net.minecraft.client.render.VertexConsumerProvider consumers,long now){
        MpsqPetCatalog.Pet catalog=MpsqPetCatalog.byId(pet.petId());if(catalog==null||catalog.group()==MpsqPetCatalog.Group.ANIMAL)return;
        net.minecraft.util.Identifier texture=catalog.textureId();boolean slim=false;
        if("__player__".equals(catalog.texture())){
            var handler=client.getNetworkHandler();var entry=handler==null?null:handler.getPlayerListEntry(pet.playerId());
            if(entry==null)return;SkinTextures skin=entry.getSkinTextures();texture=skin.texture();slim=skin.model()==SkinTextures.Model.SLIM;
        }
        MpsqNpcSkinRenderer.Skin skin=new MpsqNpcSkinRenderer.Skin(texture,slim);
        var state=MpsqNpcSkinRenderer.createState(skin,pet.yaw(),0,(now%1_000_000L)/50.0f,false);
        state.limbSwingAnimationProgress=(now%1_000_000L)/50.0f*0.08f;state.limbSwingAmplitude=0.12f;
        MpsqPetModelContext.render(()->MpsqNpcSkinRenderer.render(state,x,y,z,0.42f,matrices,consumers,light,0xFFFFFFFF));
    }
}
