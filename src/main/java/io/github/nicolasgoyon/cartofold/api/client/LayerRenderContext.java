package io.github.nicolasgoyon.cartofold.api.client;

import io.github.nicolasgoyon.cartofold.api.MapViewKind;
import io.github.nicolasgoyon.cartofold.view.MapProjection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The context of the draw of a layer in a map view. The view cuts the draws at its rectangle.
 *
 * @param graphics   the graphics of the screen
 * @param projection the projection of the view: {@link MapProjection#toScreen} gives the point of a block
 * @param dimension  the dimension of the view
 * @param kind       the kind of the view
 */
public record LayerRenderContext(GuiGraphics graphics, MapProjection projection, ResourceKey<Level> dimension,
        MapViewKind kind) {
}
