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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Suppresses vanilla jukebox and note-block audio while MPSQ audio is active. */
@Mixin(SoundSystem.class)
public abstract class SoundSystemMixin {
    @Inject(
            method = "play(Lnet/minecraft/client/sound/SoundInstance;)Lnet/minecraft/client/sound/SoundSystem$PlayResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void mpsq$cancelVanillaMusicBlocksDuringMpsqAudio(
            SoundInstance sound,
            CallbackInfoReturnable<SoundSystem.PlayResult> cir
    ) {
        if (sound != null && isMinecraftMusicBlock(sound) && isMpsqAudioActive()) {
            cir.setReturnValue(SoundSystem.PlayResult.NOT_STARTED);
        }
    }

    private static boolean isMpsqAudioActive() {
        return CinemaAudioManager.isMusicAudioActive()
                || MpsqMediaAudioManager.playing()
                || MpsqAudioManager.playing();
    }

    private static boolean isMinecraftMusicBlock(SoundInstance sound) {
        return sound.getCategory() == SoundCategory.RECORDS
                || ("minecraft".equals(sound.getId().getNamespace())
                && sound.getId().getPath().startsWith("block.note_block."));
    }
}
