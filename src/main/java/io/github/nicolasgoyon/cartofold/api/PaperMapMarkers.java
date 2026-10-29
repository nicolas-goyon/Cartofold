package io.github.nicolasgoyon.cartofold.api;

import io.github.nicolasgoyon.cartofold.paper.PaperWaypoints;

/**
 * The entry point of the waypoints of blocks on the survey maps (the paper maps of Cartofold).
 *
 * <pre>{@code
 * PaperMapMarkers.register(PaperMarkerSource.of(
 *         ResourceLocation.fromNamespaceAndPath("mymod", "village_heart"),
 *         state -> state.is(MyBlocks.VILLAGE_HEART.get()),
 *         MyMapDecorations.VILLAGE,
 *         (level, pos) -> villageAt(level, pos).map(village -> Component.literal(village.name()))));
 * }</pre>
 *
 * <p>Cartofold has a source for the banners (the icons and the names of the vanilla banners) and for the lodestone
 * (the decoration type {@code cartofold:waypoint}). Other mods can use this type for their own sources.
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
