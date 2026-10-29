package io.github.nicolasgoyon.cartofold.api.client;

import io.github.nicolasgoyon.cartofold.api.LayerInfo;

/**
 * A layer that a mod of the client draws itself. Use it for data that the client has (for example data of the client
 * of the mod). For data of the server, use {@link io.github.nicolasgoyon.cartofold.api.CartofoldLayers}: Cartofold
 * then draws the layer.
 */
public interface MapLayerRenderer {
    /** The description of the layer: its order, its views and its name in the settings. */
    LayerInfo info();

    /** Draws the layer in a view. The views call this method on the render thread. */
    void render(LayerRenderContext context);
}
