package de.galaxushd.mpsqcamera.mixin.client;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only our local actors: use their moving bounds, not tiny-mob distance scaling. */
@Mixin(EntityRenderer.class)
public abstract class MpsqNpcCullingMixin {
 @Inject(method="shouldRender",at=@At("HEAD"),cancellable=true)
 private void mpsq$movingBounds(Entity entity,Frustum frustum,double x,double y,double z,float partialTick,CallbackInfoReturnable<Boolean> result){
  if(!entity.entityTags().contains("mpsq_cinematic_actor")||entity.entityTags().contains("mpsq_cinematic_preview"))return;
  var position=entity.getPosition(partialTick);
  var bounds=entity.getBoundingBox().move(position.subtract(entity.position())).inflate(.5);
  result.setReturnValue(position.distanceToSqr(x,y,z)<=de.galaxushd.mpsqcamera.MpsqRenderRange.squared()&&frustum.isVisible(bounds));
 }
 @Inject(method="extractRenderState",at=@At("RETURN"))
 private void mpsq$singleLabel(Entity entity,net.minecraft.client.renderer.entity.state.EntityRenderState state,float partialTick,org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci){if(de.galaxushd.mpsqcamera.MpsqNpcGlow.isNpc(entity))state.nameTag=null;}
}
