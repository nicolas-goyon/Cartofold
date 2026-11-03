package io.github.nicolasgoyon.cartofold.paper;

/**
 * The geometry of a vanilla map (a paper map): 128 x 128 pixels around a center, with {@code 2^scale} blocks for each
 * pixel. The rules are the rules of the game.
 *
 * @param centerX the X of the center of the map
 * @param centerZ the Z of the center of the map
 * @param scale   the scale, from 0 to 4
 */
public record PaperMapGeometry(int centerX, int centerZ, int scale) {
    /** The side of a map, in pixels. */
    public static final int SIZE = 128;

    /** The number of blocks on each side of a pixel. */
    public int factor() {
        return 1 << scale;
    }

    /** The X of the first block of a column of pixels, as in the game. */
    public int blockX(int px) {
        return (centerX / factor() + px - SIZE / 2) * factor();
    }

    /** The Z of the first block of a row of pixels, as in the game. */
    public int blockZ(int pz) {
        return (centerZ / factor() + pz - SIZE / 2) * factor();
    }

    /** The column of pixels of a block X. It can be out of the map (less than 0 or {@link #SIZE} or more). */
    public int pixelX(int blockX) {
        return Math.floorDiv(blockX - blockX(0), factor());
    }

    /** The row of pixels of a block Z. It can be out of the map. */
    public int pixelZ(int blockZ) {
        return Math.floorDiv(blockZ - blockZ(0), factor());
    }
}
