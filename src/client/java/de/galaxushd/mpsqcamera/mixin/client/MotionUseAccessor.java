package de.galaxushd.mpsqcamera.mixin.client;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.world.entity.LivingEntity;
@Mixin(LivingEntity.class)
public interface MotionUseAccessor {
 @Accessor("useItemRemaining") void mpsq$remaining(int value);
}
