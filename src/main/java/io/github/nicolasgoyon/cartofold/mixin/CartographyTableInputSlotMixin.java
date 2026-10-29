package io.github.nicolasgoyon.cartofold.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.nicolasgoyon.cartofold.paper.CartofoldItems;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The second slot of the cartography table accepts an empty survey map, for a copy of a survey map. */
@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$4")
// Mixin calls the injected methods.
@SuppressWarnings("UnusedMethod")
abstract class CartographyTableInputSlotMixin {
    @ModifyReturnValue(method = "mayPlace", at = @At("RETURN"))
    private boolean cartofold$emptySurveyMap(boolean original, ItemStack stack) {
        return original || stack.is(CartofoldItems.EMPTY_SURVEY_MAP.get());
    }
}
