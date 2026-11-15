package de.galaxushd.mpsqcamera;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import de.galaxushd.mpsqcamera.compat.*;

/** A viewer-only empty cell. No block state, collision, light or save data is changed. */
public final class MpsqCameraCavity {
    private static volatile BlockPos hidden;
    private static Object world;
    public static boolean hides(BlockPos pos){return pos.equals(hidden);}
    public static void initialize(){WorldRenderEvents.AFTER_ENTITIES.register(MpsqCameraCavity::render);}
    public static void update(){
        var c=Minecraft.getInstance();BlockPos next=null;
        if(c.level!=null&&(ScreenCreationManager.isCameraViewActive()||MpsqCinematicPreview.active())&&c.getCameraEntity()!=null){
            var eye=c.getCameraEntity().getEyePosition();var candidate=BlockPos.containing(eye);
            if(c.level.hasChunkAt(candidate)&&c.level.getBlockState(candidate).getShape(c.level,candidate).toAabbs().stream().anyMatch(box->box.move(candidate).contains(eye)))next=candidate.immutable();
        }
        if(world!=c.level){hidden=null;world=c.level;}
        if(!Objects.equals(hidden,next)){BlockPos old=hidden;hidden=next;dirty(old);dirty(next);}
    }
    private static void dirty(BlockPos p){if(p!=null)Minecraft.getInstance().levelExtractor.setBlocksDirty(p.getX()-1,p.getY()-1,p.getZ()-1,p.getX()+1,p.getY()+1,p.getZ()+1);}
    private static BlockStateModelPart inward(BlockStateModelPart source,Direction face){return new BlockStateModelPart(){
        public java.util.List<net.minecraft.client.resources.model.geometry.BakedQuad> getQuads(Direction direction){return direction==null||direction==face?source.getQuads(direction):List.of();}
        public boolean useAmbientOcclusion(){return source.useAmbientOcclusion();}
        public net.minecraft.client.resources.model.sprite.Material.Baked particleMaterial(){return source.particleMaterial();}
        public int materialFlags(){return source.materialFlags();}
    };}
    private static void render(WorldRenderContext ctx){
        BlockPos cell=hidden;var c=Minecraft.getInstance();if(cell==null||c.level==null)return;
        // Chunk meshes culled these faces against the occupied camera cell. Submit
        // the actual neighboring models again, including their inward faces.
        var camera=ctx.camera().getPos();var matrices=ctx.matrixStack();
        for(Direction direction:Direction.values()){
            BlockPos pos=cell.relative(direction);var state=c.level.getBlockState(pos);if(state.isAir())continue;
            var parts=new ArrayList<BlockStateModelPart>();c.getModelManager().getBlockStateModelSet().get(state).collectParts(net.minecraft.util.RandomSource.create(state.getSeed(pos)),parts);
            if(parts.isEmpty())continue;
            int maxTint=-1;for(var part:parts)for(Direction side:Direction.values())for(var quad:part.getQuads(side))maxTint=Math.max(maxTint,quad.materialInfo().tintIndex());
            for(var part:parts)for(var quad:part.getQuads(null))maxTint=Math.max(maxTint,quad.materialInfo().tintIndex());
            int[] colors=new int[Math.max(0,maxTint+1)];for(int i=0;i<colors.length;i++)colors[i]=0xFF000000|c.getBlockColors().getTintSource(state,i).colorInWorld(state,c.level,pos);
            int light=net.minecraft.util.LightCoordsUtil.getLightCoords(c.level,pos);
            matrices.pushPose();matrices.translate(pos.getX()-camera.x-direction.getStepX()*.001,pos.getY()-camera.y-direction.getStepY()*.001,pos.getZ()-camera.z-direction.getStepZ()*.001);
            for(var part:parts)ctx.consumers().collector().submitBlockModel(matrices,(part.materialFlags()&1)!=0?RenderTypes.translucentMovingBlock():RenderTypes.cutoutMovingBlock(),List.of(inward(part,direction.getOpposite())),colors,light,OverlayTexture.NO_OVERLAY,0);
            matrices.popPose();
        }
    }
}
