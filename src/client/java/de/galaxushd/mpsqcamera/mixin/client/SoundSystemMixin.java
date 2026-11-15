package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.CinemaAudioManager;
import de.galaxushd.mpsqcamera.MpsqAudioManager;
import de.galaxushd.mpsqcamera.MpsqMediaAudioManager;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Suppresses vanilla jukebox and note-block audio while MPSQ audio is active. */
@Mixin(SoundEngine.class)
public abstract class SoundSystemMixin {
    @Inject(
            method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mpsq$cancelVanillaMusicBlocksDuringMpsqAudio(
            SoundInstance sound,
            CallbackInfoReturnable<SoundEngine.PlayResult> cir
    ) {
        if (sound != null && isMinecraftMusicBlock(sound) && isMpsqAudioActive()) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }

    private static boolean isMpsqAudioActive() {
        return CinemaAudioManager.isMusicAudioActive()
                || MpsqMediaAudioManager.playing()
                || MpsqAudioManager.playing();
    }

    private static boolean isMinecraftMusicBlock(SoundInstance sound) {
        return sound.getSource() == SoundSource.RECORDS
                || ("minecraft".equals(sound.getIdentifier().getNamespace())
                && sound.getIdentifier().getPath().startsWith("block.note_block."));
    }
}
