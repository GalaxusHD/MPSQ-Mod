package de.galaxushd.mpsqcamera.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes the local player's own nametag visible in third-person/F5 views. */
@Mixin(net.minecraft.client.render.entity.LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
    private void mpsq$showOwnThirdPersonLabel(
            LivingEntity entity,
            double squaredDistanceToCamera,
            CallbackInfoReturnable<Boolean> callback
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == entity && !client.options.getPerspective().isFirstPerson()) {
            callback.setReturnValue(true);
        }
    }
}
