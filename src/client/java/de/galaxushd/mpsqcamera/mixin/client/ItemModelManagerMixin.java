package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.TeamVisibilitySettings;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Selects the MPSQ clock model without replacing or registering the vanilla item. */
@Mixin(ItemModelManager.class)
public abstract class ItemModelManagerMixin {
    private static final Identifier MPSQ_CLOCK_MODEL = Identifier.of("mpsqcamera", "mpsq_clock");

    @ModifyVariable(method = "update", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ItemStack mpsqteam$useMobileScreenModel(ItemStack stack) {
        if (!TeamVisibilitySettings.visible() || !stack.isOf(Items.CLOCK)) return stack;
        ItemStack rendered = stack.copy();
        rendered.set(DataComponentTypes.ITEM_MODEL, MPSQ_CLOCK_MODEL);
        return rendered;
    }
}
