package io.github.nicolasgoyon.cartofold.data;

/**
 * The map data of the 16 x 16 columns of a chunk, at the index {@code x + z * 16} (x and z in the chunk). The arrays
 * are mutable: a sampler fills them.
 */
public final class ChunkColumns {
    /** The number of columns of a chunk. */
    public static final int COLUMNS = 16 * 16;

    private final byte[] colors;
    private final short[] heights;
    private final byte[] depths;

    /**
     * Makes columns from arrays.
     *
     * @param colors  the id of the map color of the top block of each column (0: no color)
     * @param heights the Y of the top block of each column
     * @param depths  the depth of the water above the floor, for a column of water (0 for the other columns)
     */
    public ChunkColumns(byte[] colors, short[] heights, byte[] depths) {
        if (colors.length != COLUMNS || heights.length != COLUMNS || depths.length != COLUMNS) {
            throw new IllegalArgumentException("A chunk has " + COLUMNS + " columns");
        }
        this.colors = colors;
        this.heights = heights;
        this.depths = depths;
    }

    /** Empty columns, to fill. */
    public static ChunkColumns empty() {
        return new ChunkColumns(new byte[COLUMNS], new short[COLUMNS], new byte[COLUMNS]);
    }

    /** The id of the map color of the top block of each column (0: no color). */
    public byte[] colors() {
        return colors;
    }

    /** The Y of the top block of each column. */
    public short[] heights() {
        return heights;
    }

    /** The depth of the water above the floor, for a column of water (0 for the other columns). */
    public byte[] depths() {
        return depths;
    }
}
