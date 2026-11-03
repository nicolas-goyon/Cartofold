package io.github.nicolasgoyon.cartofold.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.nicolasgoyon.cartofold.server.BannerService;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A use of a vanilla map on a banner also marks the banner on the minimap and on the map of the world. */
@Mixin(MapItem.class)
// Mixin calls the injected methods.
@SuppressWarnings("UnusedMethod")
abstract class MapItemMixin {
    @ModifyReturnValue(method = "useOn", at = @At("RETURN"))
    private InteractionResult cartofold$markBanner(InteractionResult original, UseOnContext context) {
        BannerService.onVanillaMapUse(context, original);
        return original;
    }
}
