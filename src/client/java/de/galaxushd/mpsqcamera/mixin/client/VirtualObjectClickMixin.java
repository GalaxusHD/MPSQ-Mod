package de.galaxushd.mpsqcamera.mixin.client;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(Minecraft.class)
public class VirtualObjectClickMixin {
 @Inject(method="startAttack",at=@At("HEAD"),cancellable=true)
 private void mpsq$attack(CallbackInfoReturnable<Boolean> ci){if(de.galaxushd.mpsqcamera.MpsqClickShield.blocks())ci.setReturnValue(false);}
 @Inject(method="continueAttack",at=@At("HEAD"),cancellable=true)
 private void mpsq$hold(boolean down,CallbackInfo ci){if(down&&de.galaxushd.mpsqcamera.MpsqClickShield.blocks())ci.cancel();}
 @Inject(method="startUseItem",at=@At("HEAD"),cancellable=true)
 private void mpsq$use(CallbackInfo ci){if(de.galaxushd.mpsqcamera.MpsqClickShield.blocks())ci.cancel();}
}
