package io.github.nicolasgoyon.cartofold.api;

import io.github.nicolasgoyon.cartofold.paper.PaperWaypoints;

/**
 * The entry point of the waypoints of blocks on the paper maps.
 *
 * <pre>{@code
 * PaperMapMarkers.register(new PaperMarkerSource(
 *         ResourceLocation.fromNamespaceAndPath("mymod", "village_heart"),
 *         state -> state.is(MyBlocks.VILLAGE_HEART.get()),
 *         MyMapDecorations.VILLAGE,
 *         (level, pos) -> villageAt(level, pos).map(village -> Component.literal(village.name()))));
 * }</pre>
 *
 * <p>Cartofold has a source for the lodestone, with the decoration type {@code cartofold:waypoint}. Other mods can use
 * this type for their own sources.
 */
public final class PaperMapMarkers {
    private PaperMapMarkers() {
    }

    /**
     * Adds a source of waypoints. Call this method once, for example in the common setup. A source with the identifier
     * of another source replaces it.
     */
    public static void register(PaperMarkerSource source) {
        PaperWaypoints.register(source);
    }
}
