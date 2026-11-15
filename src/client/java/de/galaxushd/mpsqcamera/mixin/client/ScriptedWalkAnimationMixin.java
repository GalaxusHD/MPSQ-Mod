package de.galaxushd.mpsqcamera.mixin.client;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** The route controller advances walking once per tick; vanilla must not advance it a second time. */
@Mixin(LivingEntity.class)
public class ScriptedWalkAnimationMixin {
 @Inject(method="calculateEntityAnimation",at=@At("HEAD"),cancellable=true)
 private void mpsq$routeWalking(boolean flying,CallbackInfo ci){if(((LivingEntity)(Object)this).entityTags().contains("mpsq_cinematic_actor"))ci.cancel();}
}
