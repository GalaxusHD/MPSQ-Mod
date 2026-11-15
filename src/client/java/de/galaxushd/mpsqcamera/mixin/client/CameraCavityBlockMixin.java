package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.MpsqCameraCavity;
import net.minecraft.client.renderer.block.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public abstract class CameraCavityBlockMixin {
    @Inject(method="tesselateBlock",at=@At("HEAD"),cancellable=true)
    private void mpsq$hideCameraCell(BlockQuadOutput output,float x,float y,float z,BlockAndTintGetter level,BlockPos pos,BlockState state,BlockStateModel model,long seed,CallbackInfo ci){
        if(MpsqCameraCavity.hides(pos))ci.cancel();
    }
}
