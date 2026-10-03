package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.CinemaAudioManager;
import de.galaxushd.mpsqcamera.MpsqAudioManager;
import de.galaxushd.mpsqcamera.MpsqMediaAudioManager;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Suppresses vanilla jukebox and note-block audio while MPSQ audio is active. */
@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {
    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void mpsq$cancelVanillaMusicBlocksDuringMpsqAudio(SoundInstance sound, CallbackInfo ci) {
        if (sound != null && isMinecraftMusicBlock(sound) && isMpsqAudioActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F", at = @At("HEAD"), cancellable = true)
    private void mpsq$silenceVanillaMusicBlocksDuringMpsqAudio(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (sound != null && isMinecraftMusicBlock(sound) && isMpsqAudioActive()) {
            cir.setReturnValue(0.0f);
        }
    }

    private static boolean isMpsqAudioActive() {
        return CinemaAudioManager.isMusicAudioActive()
                || MpsqMediaAudioManager.playing()
                || MpsqAudioManager.playing();
    }

    private static boolean isMinecraftMusicBlock(SoundInstance sound) {
        if (sound.getCategory() == SoundCategory.RECORDS) {
            return true;
        }
        return "minecraft".equals(sound.getId().getNamespace())
                && sound.getId().getPath().startsWith("block.note_block.");
    }
}
