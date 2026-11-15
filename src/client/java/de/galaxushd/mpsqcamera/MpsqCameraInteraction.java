package de.galaxushd.mpsqcamera;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Picking uses the camera head, never the invisible armor stand beneath it. */
public final class MpsqCameraInteraction {
    private static boolean playerHead(Minecraft c, BlockPos pos) {
        var state=c.level.getBlockState(pos);
        return state.is(Blocks.PLAYER_HEAD)||state.is(Blocks.PLAYER_WALL_HEAD);
    }
    public static Vec3 lookedAtHead(Minecraft c) {
        if(c.level==null||!(c.hitResult instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK||!playerHead(c,hit.getBlockPos())) return null;
        var shape=c.level.getBlockState(hit.getBlockPos()).getShape(c.level,hit.getBlockPos());
        return shape.isEmpty()?Vec3.atCenterOf(hit.getBlockPos()):shape.bounds().move(hit.getBlockPos()).getCenter();
    }
    static float headYaw(Minecraft c,Vec3 center) {
        var state=c.level.getBlockState(BlockPos.containing(center));
        if(state.hasProperty(net.minecraft.world.level.block.WallSkullBlock.FACING)){
            var facing=state.getValue(net.minecraft.world.level.block.WallSkullBlock.FACING);
            return (float)Math.toDegrees(Math.atan2(-facing.getStepX(),facing.getStepZ()));
        }
        if(!state.hasProperty(net.minecraft.world.level.block.SkullBlock.ROTATION))return c.player.getYRot();
        return net.minecraft.util.Mth.wrapDegrees(state.getValue(net.minecraft.world.level.block.SkullBlock.ROTATION)*22.5f+180f);
    }
    public static boolean usesWorldHead(Vec3 position) {
        var c=Minecraft.getInstance(); if(c.level==null)return false;
        var pos=BlockPos.containing(position);
        return playerHead(c,pos)&&c.level.getBlockState(pos).getShape(c.level,pos).toAabbs().stream().anyMatch(b->b.move(pos).inflate(.001).contains(position));
    }
    public static AABB bounds(Vec3 position) {return new AABB(position.x-.5,position.y-.5,position.z-.5,position.x+.5,position.y+.5,position.z+.5);}
    public static LocalCameraStore.CameraData target() {
        var c=Minecraft.getInstance();if(c.level==null||c.player==null||c.gui.screen()!=null||!TeamVisibilitySettings.inArea()||ScreenCreationManager.isCameraViewActive())return null;
        Vec3 eye=c.player.getEyePosition(),end=eye.add(c.player.getViewVector(1).scale(6));
        double nearest=c.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK?eye.distanceToSqr(hit.getLocation())+.001:36;
        LocalCameraStore.CameraData chosen=null;
        for(var camera:LocalCameraStore.getAll()) {
            if(camera.position()==null||!camera.dimension().equals(c.level.dimension().identifier().toString()))continue;
            var box=bounds(camera.position());var hit=box.contains(eye)?java.util.Optional.of(eye):box.clip(eye,end);
            if(hit.isPresent()&&eye.distanceToSqr(hit.get())<=nearest){nearest=eye.distanceToSqr(hit.get());chosen=camera;}
        }
        return chosen;
    }
    public static boolean handleRightClick() {
        var camera=target();if(camera==null)return false;var c=Minecraft.getInstance();
        if(c.options.keyShift.isDown())c.gui.setScreen(new CameraDetailScreen(null,camera.id()));
        return true;
    }
}
