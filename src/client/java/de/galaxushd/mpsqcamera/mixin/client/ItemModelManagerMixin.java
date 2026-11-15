package de.galaxushd.mpsqcamera.mixin.client;

import de.galaxushd.mpsqcamera.TeamVisibilitySettings;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Selects the MPSQ clock model without replacing or registering the vanilla item. */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelManagerMixin {
    private static final Identifier MPSQ_CLOCK_MODEL = Identifier.fromNamespaceAndPath("mpsqcamera", "mpsq_clock");

    @ModifyVariable(method = "appendItemLayers", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private ItemStack mpsqteam$useMobileScreenModel(ItemStack stack) {
        if (!TeamVisibilitySettings.visible() || !stack.is(Items.CLOCK)) return stack;
        ItemStack rendered = stack.copy();
        rendered.set(DataComponents.ITEM_MODEL, MPSQ_CLOCK_MODEL);
        return rendered;
    }
}
