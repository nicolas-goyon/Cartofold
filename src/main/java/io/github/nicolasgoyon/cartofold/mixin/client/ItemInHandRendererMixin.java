package io.github.nicolasgoyon.cartofold.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.nicolasgoyon.cartofold.client.SurveyMapHands;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapItem;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The items in the hands of the player: a survey map is held as a vanilla map, and Cartofold draws it (see
 * {@link SurveyMapHands}). The vanilla maps do not change.
 */
@Mixin(ItemInHandRenderer.class)
// Mixin calls the injected methods, and gives them the parameters of the target method.
@SuppressWarnings({"UnusedMethod", "UnusedVariable"})
abstract class ItemInHandRendererMixin {
    /** A survey map takes the poses of a map in the hands. */
    @ModifyExpressionValue(method = "renderArmWithItem", at = @At(value = "CONSTANT",
            args = "classValue=net/minecraft/world/item/MapItem"))
    private boolean cartofold$holdSurveyMap(boolean map, @Local(argsOnly = true) ItemStack stack) {
        return map || stack.getItem() instanceof SurveyMapItem;
    }

    /** Draws a survey map in place of a vanilla map. */
    @Inject(method = "renderMap", at = @At("HEAD"), cancellable = true)
    private void cartofold$drawSurveyMap(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
            ItemStack stack, CallbackInfo info) {
        if (stack.getItem() instanceof SurveyMapItem) {
            SurveyMapHands.render(poseStack, buffer, packedLight, stack);
            info.cancel();
        }
    }
}
