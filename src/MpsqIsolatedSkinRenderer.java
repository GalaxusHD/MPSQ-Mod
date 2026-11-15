package de.galaxushd.mpsqcamera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import de.galaxushd.mpsqcamera.compat.MultiBufferSource;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerModelType;

/** Dedicated geometry: never uses the shared, resource-pack-replaced player renderer. */
public final class MpsqIsolatedSkinRenderer {
    private record Geometry(ModelPart root,PlayerModel model){}
    private static final Geometry[] NPC=new Geometry[2],BODIES=new Geometry[2];
    private static Geometry geometry(boolean slim,boolean body){
        Geometry[] cache=body?BODIES:NPC;int i=slim?1:0;
        if(cache[i]==null){
            ModelPart root=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot();
            cache[i]=new Geometry(root,body?null:new PlayerModel(root,slim));
        }
        return cache[i];
    }
    public static boolean isBody(AvatarRenderState state){
        String key=de.galaxushd.mpsqcamera.compat.AvatarIdentity.name(state);
        return key!=null&&key.contains("#mpsq-kick-");
    }
    public static void render(AvatarRenderState state,PoseStack pose,MultiBufferSource buffers,boolean body){
        if(state.skin==null)return;
        Geometry geo=geometry(state.skin.model()==PlayerModelType.SLIM,body);
        geo.root.getAllParts().forEach(p->{p.resetPose();p.visible=true;p.skipDraw=false;});
        if(!body)geo.model.setupAnim(state);
        else {
            String key=de.galaxushd.mpsqcamera.compat.AvatarIdentity.name(state);
            for(String[] names:new String[][]{{"head","head"},{"right_arm","rightArm"},{"left_arm","leftArm"},{"right_leg","rightLeg"},{"left_leg","leftLeg"}}){
                ModelPart part=geo.root.getChild(names[0]);float[] rot=MpsqKickAnimationManager.rotation(key,names[1]);
                if(rot!=null){part.xRot=rot[0];part.yRot=rot[1];part.zRot=rot[2];}
                float[] move=MpsqKickAnimationManager.position(key,names[1]);
                if(move!=null){part.x+=move[0];part.y+=move[1];part.z+=move[2];}
            }
        }
        geo.root.getChild("head").getChild("hat").visible=state.showHat;
        geo.root.getChild("body").getChild("jacket").visible=state.showJacket;
        geo.root.getChild("left_arm").getChild("left_sleeve").visible=state.showLeftSleeve;
        geo.root.getChild("right_arm").getChild("right_sleeve").visible=state.showRightSleeve;
        geo.root.getChild("left_leg").getChild("left_pants").visible=state.showLeftPants;
        geo.root.getChild("right_leg").getChild("right_pants").visible=state.showRightPants;
        pose.pushPose();pose.rotate(Axis.YP.rotationDegrees(180-state.bodyRot));
        if(body){
            float fall=MpsqKickAnimationManager.rootPitchDegrees(de.galaxushd.mpsqcamera.compat.AvatarIdentity.name(state));
            pose.translate(0,22.56/16,0);pose.rotate(Axis.XP.rotationDegrees(fall));pose.translate(0,-22.56/16,0);
        }
        pose.scale(-.9375f,-.9375f,.9375f);pose.translate(0,-1.501,0);
        var type=RenderTypes.entityCutout(state.skin.body().texturePath());
        geo.root.render(pose,buffers.getBuffer(type),state.lightCoords,OverlayTexture.NO_OVERLAY,-1);
        if(state.appearsGlowing()){
            var outline=buffers.outline(state.outlineColor);geo.root.render(pose,outline.getBuffer(type),state.lightCoords,OverlayTexture.NO_OVERLAY,-1);outline.endBatch();
        }
        pose.popPose();
    }
}
