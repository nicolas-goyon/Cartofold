package io.github.nicolasgoyon.cartofold.mixin;

import io.github.nicolasgoyon.cartofold.paper.MapMoves;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The vanilla map item: the move of a map from the cartography table (see {@link MapMoves}). */
@Mixin(MapItem.class)
// Mixin calls the injected methods, and gives them the parameters of the target method.
@SuppressWarnings({"UnusedMethod", "UnusedVariable"})
public abstract class MapItemMixin {
    /** A map from the cartography table with a direction moves (see {@link MapMoves}). */
    @Inject(method = "onCraftedPostProcess", at = @At("HEAD"))
    private void cartofold$move(ItemStack stack, Level level, CallbackInfo info) {
        MapMoves.postProcess(stack, level);
    }
}
