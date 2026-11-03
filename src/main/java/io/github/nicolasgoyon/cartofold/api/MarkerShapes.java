package io.github.nicolasgoyon.cartofold.api;

import io.github.nicolasgoyon.cartofold.Cartofold;
import net.minecraft.resources.ResourceLocation;

/**
 * The icons of Cartofold for the markers: white shapes with a dark outline. Give one of them to
 * {@link MapMarker#tinted}: the views draw the white pixels in the color of the marker.
 */
public final class MarkerShapes {
    /** A full square. */
    public static final ResourceLocation SQUARE = texture("square");
    /** A ring. */
    public static final ResourceLocation RING = texture("ring");
    /** A cross. */
    public static final ResourceLocation CROSS = texture("cross");
    /** A diamond. */
    public static final ResourceLocation DIAMOND = texture("diamond");
    /** A small disc. */
    public static final ResourceLocation DOT = texture("dot");

    private MarkerShapes() {
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "textures/marker/" + name + ".png");
    }
}
