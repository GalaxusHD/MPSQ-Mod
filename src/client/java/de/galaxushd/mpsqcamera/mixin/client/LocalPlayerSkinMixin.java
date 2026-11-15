package de.galaxushd.mpsqcamera.mixin.client;
import de.galaxushd.mpsqcamera.MpsqLocalSkinRecovery;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractClientPlayer.class)
public abstract class LocalPlayerSkinMixin {
 @Inject(method="getSkin",at=@At("RETURN"),cancellable=true)
 private void mpsq$recoverLocalSkin(CallbackInfoReturnable<PlayerSkin> cir) {
  var player=(AbstractClientPlayer)(Object)this;
  cir.setReturnValue(MpsqLocalSkinRecovery.recover(player.getGameProfile(),cir.getReturnValue()));
 }
}
