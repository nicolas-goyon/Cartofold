package io.github.nicolasgoyon.cartofold.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The first slot of the cartography table (the map) accepts a survey map. */
@Mixin(targets = "net.minecraft.world.inventory.CartographyTableMenu$3")
// Mixin calls the injected methods.
@SuppressWarnings("UnusedMethod")
abstract class CartographyTableMapSlotMixin {
    @ModifyReturnValue(method = "mayPlace", at = @At("RETURN"))
    private boolean cartofold$surveyMap(boolean original, ItemStack stack) {
        return original || stack.getItem() instanceof SurveyMapItem;
    }
}
