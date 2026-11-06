package io.github.nicolasgoyon.cartofold.view;

/**
 * The sizes of the markers, of their names and of the arrow of the player on the minimap, the map of the world and the
 * map frames, in units of the screen.
 *
 * @param scale the scale of the setting of the player: 1 gives the default sizes
 */
public record MarkerSizes(double scale) {
    /** The smallest scale of the setting. */
    public static final double MIN_SCALE = 0.5;
    /** The largest scale of the setting. */
    public static final double MAX_SCALE = 3;

    private static final double ICON = 5;
    private static final double NAME = 0.5;
    private static final double ARROW = 1 / 3.0;
    private static final double MIN_HIT = 4;

    public MarkerSizes {
        scale = Math.clamp(scale, MIN_SCALE, MAX_SCALE);
    }

    /** The side of an icon of a marker. */
    public double icon() {
        return ICON * scale;
    }

    /** The scale of the text of a name: 1 is the size of the font of the game. */
    public double name() {
        return NAME * scale;
    }

    /** The scale of the arrow of the player: 1 is the size of the shape of the arrow. */
    public double arrow() {
        return ARROW * scale;
    }

    /** The scale of the diamond of a waypoint and of a marker with no icon: 1 is the size of the shape. */
    public double diamond() {
        return ICON / 10 * scale;
    }

    /** The distance from the name to the point of its marker. */
    public double nameOffset() {
        return icon() / 2 + 1;
    }

    /** The largest distance between the mouse and a marker for a click or a tooltip. */
    public double hit() {
        return Math.max(MIN_HIT, icon() / 2 + 1.5);
    }

    /** The half of the free space in the mark of a selected marker. */
    public double selection() {
        return icon() / 2 + 1.5;
    }
}
