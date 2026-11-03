package io.github.nicolasgoyon.cartofold.api;

import java.util.EnumSet;
import java.util.Set;

/** The kinds of map views. A layer selects the kinds of views that show it. */
public enum MapViewKind {
    /** The minimap on the screen of the game. */
    MINIMAP(1),
    /** The map of the world (key M). */
    WORLD_MAP(1 << 1),
    /** The paper maps in the hands and in the item frames. */
    PAPER(1 << 2),
    /** The map frames in the screens of the other mods. */
    FRAME(1 << 3);

    private final int bit;

    MapViewKind(int bit) {
        this.bit = bit;
    }

    /** All the kinds of views. */
    public static Set<MapViewKind> all() {
        return EnumSet.allOf(MapViewKind.class);
    }

    /** The kinds of a bit mask: each kind has one bit. */
    public static Set<MapViewKind> fromMask(int mask) {
        Set<MapViewKind> kinds = EnumSet.noneOf(MapViewKind.class);
        for (MapViewKind kind : values()) {
            if ((mask & kind.bit) != 0) {
                kinds.add(kind);
            }
        }
        return kinds;
    }

    /** The bit mask of kinds (see {@link #fromMask}). */
    public static int toMask(Set<MapViewKind> kinds) {
        int mask = 0;
        for (MapViewKind kind : kinds) {
            mask |= kind.bit;
        }
        return mask;
    }
}
