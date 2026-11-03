package io.github.nicolasgoyon.cartofold.api.client;

import io.github.nicolasgoyon.cartofold.client.ClientLayers;

/** The entry point of the layers on the client. */
public final class CartofoldClientLayers {
    private CartofoldClientLayers() {
    }

    /**
     * Adds a layer that the mod draws itself. Call this method once, for example in the setup of the client. A
     * renderer with the identifier of another renderer replaces it.
     */
    public static void register(MapLayerRenderer renderer) {
        ClientLayers.get().register(renderer);
    }
}
