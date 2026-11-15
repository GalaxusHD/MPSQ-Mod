package de.galaxushd.mpsqcamera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** One continuous preloaded effect, with impact aligned to the fall. */
final class MpsqKickSounds {
    static void shot(){play("kick.continuous");}
    private static void play(String event){
        if(TeamVisibilitySettings.visible())Minecraft.getInstance().getSoundManager().play(new Effect(event));
    }
    private static final class Effect extends SimpleSoundInstance {
        Effect(String event){super(Identifier.fromNamespaceAndPath("mpsqcamera",event),SoundSource.PLAYERS,.28f,1f,RandomSource.create(),false,0,SoundInstance.Attenuation.NONE,0,0,0,true);}
    }
}
