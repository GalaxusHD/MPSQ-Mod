package de.galaxushd.mpsqcamera.mixin.client;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.illager.SpellcasterIllager;
@Mixin(SpellcasterIllager.class)
public interface CinematicSpellAccessor {
    @Accessor("DATA_SPELL_CASTING_ID")
    static EntityDataAccessor<Byte> mpsq$spellData(){throw new AssertionError();}
}
