package io.github.nicolasgoyon.cartofold.paper;

/**
 * The move of a survey map in the cartography table. The game puts the center of a map on a grid: a zoomed-out map
 * is not always around the player. The move gives a new map, one step from the old map in a direction.
 *
 * <ul>
 *   <li>A step is half of the side of the map: the new map has half of the area of the old map.</li>
 *   <li>The new map has the same scale. It keeps the explored pixels of the old map in their new positions, and no
 *       marker.</li>
 *   <li>A locked map does not move.</li>
 * </ul>
 */
public final class MapMoves {
    /** The directions: the ids of the buttons of the screen. */
    public static final int NORTH = 0;
    public static final int SOUTH = 1;
    public static final int WEST = 2;
    public static final int EAST = 3;
    /** The update of the map at a distance (see {@link MapUpdates}): the id of its button. */
    public static final int UPDATE = 4;
    /** No selection: the zoom of the game. */
    public static final int NONE = -1;
    /** The side of a map, in pixels. */
    public static final int SIZE = 128;
    /** A step, in pixels: half of the side of a map. */
    public static final int STEP = SIZE / 2;

    private MapMoves() {
    }

    /** The move of a direction on the X axis, in steps. */
    public static int dx(int direction) {
        return direction == EAST ? 1 : direction == WEST ? -1 : 0;
    }

    /** The move of a direction on the Z axis, in steps. */
    public static int dz(int direction) {
        return direction == SOUTH ? 1 : direction == NORTH ? -1 : 0;
    }

    /**
     * The data of a map, one step in a direction.
     *
     * @param data      the data of the map
     * @param direction the direction
     * @return the new data, with the explored pixels of the map in their new positions
     */
    public static SurveyMapData moved(SurveyMapData data, int direction) {
        int blocks = STEP << data.scale();
        SurveyMapData result = data.moved(data.centerX() + dx(direction) * blocks,
                data.centerZ() + dz(direction) * blocks, data.scale());
        byte[] shifted = shifted(data.colors(), dx(direction) * STEP, dz(direction) * STEP);
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                result.setColor(x, z, shifted[x + z * SIZE]);
            }
        }
        return result;
    }

    /**
     * The pixels of a map after a move: the pixel (x, z) of the new map is the pixel (x + dx, z + dz) of the old map, or
     * 0 (not explored) out of the old map.
     *
     * @param colors the pixels of the old map, at the index {@code x + z * SIZE}
     * @param dx     the move on the X axis, in pixels
     * @param dz     the move on the Z axis, in pixels
     * @return the pixels of the new map
     */
    static byte[] shifted(byte[] colors, int dx, int dz) {
        byte[] result = new byte[SIZE * SIZE];
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int oldX = x + dx;
                int oldZ = z + dz;
                if (oldX >= 0 && oldX < SIZE && oldZ >= 0 && oldZ < SIZE) {
                    result[x + z * SIZE] = colors[oldX + oldZ * SIZE];
                }
            }
        }
        return result;
    }
}
