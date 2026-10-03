package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.CinemaAudioManager;
import de.galaxushd.mpsqcamera.MpsqMediaAudioManager;
import de.galaxushd.mpsqcamera.MpsqAudioManager;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Suppresses jukebox and note-block audio while MPSQ music is active. */
@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {
    @Inject(method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F", at = @At("HEAD"), cancellable = true)
    private void mpsq$suppressRecordsDuringMpsqAudio(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        if (isMinecraftMusicBlock(sound)
                && (CinemaAudioManager.isMusicAudioActive()
                || MpsqMediaAudioManager.playing()
                || MpsqAudioManager.playing())) {
            cir.setReturnValue(0.0f);
        }
    }

    private static boolean isMinecraftMusicBlock(SoundInstance sound) {
        if (sound.getCategory() == SoundCategory.RECORDS) return true;
        String path = sound.getId().getPath();
        return path.startsWith("block.note_block.");
    }
}
