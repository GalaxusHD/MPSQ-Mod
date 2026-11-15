package de.galaxushd.mpsqcamera.mixin.client;
import net.minecraft.world.entity.Entity;
import de.galaxushd.mpsqcamera.MpsqNpcGlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Entity.class)
abstract class MpsqNpcColorMixin {
 @Inject(method="getTeamColor",at=@At("HEAD"),cancellable=true)
 private void mpsq$customColor(CallbackInfoReturnable<Integer> result){Integer color=MpsqNpcGlow.entityColor((Entity)(Object)this);if(color!=null)result.setReturnValue(color);}
}
