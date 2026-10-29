package io.github.nicolasgoyon.cartofold.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.nicolasgoyon.cartofold.client.PaperMapOverlays;
import net.minecraft.client.gui.MapRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The vanilla map renderer: the layers of Cartofold go on the map (see {@link PaperMapOverlays}). */
@Mixin(MapRenderer.class)
// Mixin calls the injected methods, and gives them the parameters of the target method.
@SuppressWarnings({"UnusedMethod", "UnusedVariable"})
abstract class MapRendererMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void cartofold$layers(PoseStack poseStack, MultiBufferSource buffer, MapId mapId,
            MapItemSavedData mapData, boolean active, int packedLight, CallbackInfo info) {
        PaperMapOverlays.render(poseStack, buffer, mapId, mapData, packedLight);
    }
}
