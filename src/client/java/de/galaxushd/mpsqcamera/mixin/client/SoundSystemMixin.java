package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.CinemaAudioManager;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Suppresses jukebox and note-block audio while MPSQ browser audio is audible. */
@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {
    @Inject(method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F", at = @At("HEAD"), cancellable = true)
    private void mpsq$suppressRecordsDuringMpsqAudio(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (sound.getCategory() == SoundCategory.RECORDS && CinemaAudioManager.isMusicAudioActive()) {
            cir.setReturnValue(0.0f);
        }
    }
}
