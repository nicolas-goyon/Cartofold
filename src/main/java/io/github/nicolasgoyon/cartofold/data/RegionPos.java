package io.github.nicolasgoyon.cartofold.data;

/**
 * The position of a region of the exploration data: a square of {@link MapRegion#SIZE} x {@link MapRegion#SIZE}
 * blocks.
 *
 * @param x the X of the region: the block X divided by {@link MapRegion#SIZE}, rounded down
 * @param z the Z of the region: the block Z divided by {@link MapRegion#SIZE}, rounded down
 */
public record RegionPos(int x, int z) {
    /** The region of a block. */
    public static RegionPos ofBlock(int blockX, int blockZ) {
        return new RegionPos(blockX >> MapRegion.SHIFT, blockZ >> MapRegion.SHIFT);
    }

    /** The region of a chunk. */
    public static RegionPos ofChunk(int chunkX, int chunkZ) {
        return new RegionPos(chunkX >> MapRegion.CHUNK_SHIFT, chunkZ >> MapRegion.CHUNK_SHIFT);
    }

    /** The X of the first block of the region. */
    public int minBlockX() {
        return x << MapRegion.SHIFT;
    }

    /** The Z of the first block of the region. */
    public int minBlockZ() {
        return z << MapRegion.SHIFT;
    }

    /** The name of the file of the region, for example {@code r.-1.2.dat}. */
    public String fileName() {
        return "r." + x + "." + z + ".dat";
    }
}
