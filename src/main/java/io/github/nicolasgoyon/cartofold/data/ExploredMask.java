package io.github.nicolasgoyon.cartofold.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * The explored columns of a region for one player: one bit for each block column of the region. The map of the
 * player shows only the explored columns.
 */
public final class ExploredMask {
    /** The version of the file format. */
    static final int VERSION = 1;
    private static final int WORDS = MapRegion.SIZE * MapRegion.SIZE / Long.SIZE;

    private final RegionPos pos;
    private final long[] bits = new long[WORDS];
    private boolean dirty;

    public ExploredMask(RegionPos pos) {
        this.pos = pos;
    }

    public RegionPos pos() {
        return pos;
    }

    private int index(int blockX, int blockZ) {
        return (blockX - pos.minBlockX()) + (blockZ - pos.minBlockZ()) * MapRegion.SIZE;
    }

    /** True if the player explored a column, from block coordinates in the world. */
    public boolean isExplored(int blockX, int blockZ) {
        int index = index(blockX, blockZ);
        return (bits[index >>> 6] & (1L << (index & 63))) != 0;
    }

    /**
     * Marks all the columns of a chunk as explored.
     *
     * @param chunkX the X of the chunk, in the region
     * @param chunkZ the Z of the chunk, in the region
     * @return true if a column was not explored
     */
    public boolean markChunk(int chunkX, int chunkZ) {
        if (!RegionPos.ofChunk(chunkX, chunkZ).equals(pos)) {
            throw new IllegalArgumentException("The chunk " + chunkX + " " + chunkZ + " is not in " + pos);
        }
        boolean changed = false;
        for (int z = 0; z < 16; z++) {
            // The 16 columns of a row of a chunk are 16 bits of one word: the side of a region is a multiple of 64.
            int index = index(chunkX * 16, chunkZ * 16 + z);
            long row = 0xFFFFL << (index & 63);
            long word = bits[index >>> 6];
            if ((word & row) != row) {
                bits[index >>> 6] = word | row;
                changed = true;
            }
        }
        dirty |= changed;
        return changed;
    }

    /** True if all the columns of a chunk are explored. */
    public boolean isChunkExplored(int chunkX, int chunkZ) {
        for (int z = 0; z < 16; z++) {
            int index = index(chunkX * 16, chunkZ * 16 + z);
            long row = 0xFFFFL << (index & 63);
            if ((bits[index >>> 6] & row) != row) {
                return false;
            }
        }
        return true;
    }

    /** The number of explored columns. */
    public int count() {
        int count = 0;
        for (long word : bits) {
            count += Long.bitCount(word);
        }
        return count;
    }

    /** True if the mask changed since the last save. */
    public boolean isDirty() {
        return dirty;
    }

    /** Records that the mask is saved. */
    public void setSaved() {
        dirty = false;
    }

    /**
     * Writes the mask.
     *
     * @param out the stream
     * @throws IOException if the stream fails
     */
    public void write(OutputStream out) throws IOException {
        DataOutputStream data = new DataOutputStream(out);
        data.writeInt(VERSION);
        DeflaterOutputStream deflater = new DeflaterOutputStream(data);
        DataOutputStream body = new DataOutputStream(deflater);
        for (long word : bits) {
            body.writeLong(word);
        }
        body.flush();
        deflater.finish();
        data.flush();
    }

    /**
     * Reads a mask.
     *
     * @param pos the position of the region
     * @param in  the stream
     * @return the mask
     * @throws IOException if the stream fails or has another version
     */
    public static ExploredMask read(RegionPos pos, InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(in);
        int version = data.readInt();
        if (version != VERSION) {
            throw new IOException("Unknown version of a mask: " + version);
        }
        DataInputStream body = new DataInputStream(new InflaterInputStream(data));
        ExploredMask mask = new ExploredMask(pos);
        for (int i = 0; i < WORDS; i++) {
            mask.bits[i] = body.readLong();
        }
        return mask;
    }
}
