package io.github.nicolasgoyon.cartofold.data;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * The map data of a region of the world: for each block column, the map color of the top block, its height and the
 * depth of the water. All the players share this data. The explored columns of each player are in an
 * {@link ExploredMask}.
 *
 * <ul>
 *   <li>A region is {@link #SIZE} x {@link #SIZE} blocks: {@link #CHUNKS} x {@link #CHUNKS} chunks.</li>
 *   <li>Each chunk has the game time of its last sample. A new sample of a chunk replaces its columns.</li>
 *   <li>The file format: the version, then the compressed arrays (see {@link #write} and {@link #read}).</li>
 * </ul>
 */
public final class MapRegion {
    /** The side of a region, in blocks. */
    public static final int SIZE = 512;
    /** The shift from a block coordinate to a region coordinate. */
    public static final int SHIFT = 9;
    /** The side of a region, in chunks. */
    public static final int CHUNKS = SIZE / 16;
    /** The shift from a chunk coordinate to a region coordinate. */
    public static final int CHUNK_SHIFT = 5;
    /** The version of the file format. */
    static final int VERSION = 1;
    /** No sample: the game time of a chunk with no sample. */
    public static final long NEVER = Long.MIN_VALUE;

    private final RegionPos pos;
    private final byte[] colors = new byte[SIZE * SIZE];
    private final short[] heights = new short[SIZE * SIZE];
    private final byte[] depths = new byte[SIZE * SIZE];
    private final long[] sampled = new long[CHUNKS * CHUNKS];
    private boolean dirty;

    public MapRegion(RegionPos pos) {
        this.pos = pos;
        Arrays.fill(sampled, NEVER);
    }

    public RegionPos pos() {
        return pos;
    }

    /** The index of a column, from block coordinates in the world. */
    private int index(int blockX, int blockZ) {
        return (blockX - pos.minBlockX()) + (blockZ - pos.minBlockZ()) * SIZE;
    }

    /** The id of the map color of a column, from block coordinates in the world (0: no color). */
    public int color(int blockX, int blockZ) {
        return colors[index(blockX, blockZ)] & 0xFF;
    }

    /** The Y of the top block of a column, from block coordinates in the world. */
    public int height(int blockX, int blockZ) {
        return heights[index(blockX, blockZ)];
    }

    /** The depth of the water of a column, from block coordinates in the world. */
    public int depth(int blockX, int blockZ) {
        return depths[index(blockX, blockZ)] & 0xFF;
    }

    /** The game time of the last sample of a chunk, or {@link #NEVER}. */
    public long sampled(int chunkX, int chunkZ) {
        return sampled[chunkIndex(chunkX, chunkZ)];
    }

    private int chunkIndex(int chunkX, int chunkZ) {
        return (chunkX - (pos.x() << CHUNK_SHIFT)) + (chunkZ - (pos.z() << CHUNK_SHIFT)) * CHUNKS;
    }

    /**
     * Puts the sample of a chunk in the region.
     *
     * @param chunkX  the X of the chunk, in the region
     * @param chunkZ  the Z of the chunk, in the region
     * @param columns the columns of the chunk
     * @param time    the game time of the sample
     * @return true if a column changed
     */
    public boolean put(int chunkX, int chunkZ, ChunkColumns columns, long time) {
        if (!RegionPos.ofChunk(chunkX, chunkZ).equals(pos)) {
            throw new IllegalArgumentException("The chunk " + chunkX + " " + chunkZ + " is not in " + pos);
        }
        boolean changed = false;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int column = x + z * 16;
                int index = index(chunkX * 16 + x, chunkZ * 16 + z);
                if (colors[index] != columns.colors()[column] || heights[index] != columns.heights()[column]
                        || depths[index] != columns.depths()[column]) {
                    colors[index] = columns.colors()[column];
                    heights[index] = columns.heights()[column];
                    depths[index] = columns.depths()[column];
                    changed = true;
                }
            }
        }
        sampled[chunkIndex(chunkX, chunkZ)] = time;
        dirty = true;
        return changed;
    }

    /** The columns of a chunk of the region. */
    public ChunkColumns columns(int chunkX, int chunkZ) {
        ChunkColumns columns = ChunkColumns.empty();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int column = x + z * 16;
                int index = index(chunkX * 16 + x, chunkZ * 16 + z);
                columns.colors()[column] = colors[index];
                columns.heights()[column] = heights[index];
                columns.depths()[column] = depths[index];
            }
        }
        return columns;
    }

    /** True if the region changed since the last save. */
    public boolean isDirty() {
        return dirty;
    }

    /** Records that the region is saved. */
    public void setSaved() {
        dirty = false;
    }

    /**
     * Writes the region.
     *
     * @param out the stream
     * @throws IOException if the stream fails
     */
    public void write(OutputStream out) throws IOException {
        DataOutputStream data = new DataOutputStream(out);
        data.writeInt(VERSION);
        DeflaterOutputStream deflater = new DeflaterOutputStream(data);
        DataOutputStream body = new DataOutputStream(deflater);
        body.write(colors);
        for (short height : heights) {
            body.writeShort(height);
        }
        body.write(depths);
        for (long time : sampled) {
            body.writeLong(time);
        }
        body.flush();
        deflater.finish();
        data.flush();
    }

    /**
     * Reads a region.
     *
     * @param pos the position of the region
     * @param in  the stream
     * @return the region
     * @throws IOException if the stream fails or has another version
     */
    public static MapRegion read(RegionPos pos, InputStream in) throws IOException {
        DataInputStream data = new DataInputStream(in);
        int version = data.readInt();
        if (version != VERSION) {
            throw new IOException("Unknown version of a region: " + version);
        }
        DataInputStream body = new DataInputStream(new InflaterInputStream(data));
        MapRegion region = new MapRegion(pos);
        body.readFully(region.colors);
        for (int i = 0; i < region.heights.length; i++) {
            region.heights[i] = body.readShort();
        }
        body.readFully(region.depths);
        for (int i = 0; i < region.sampled.length; i++) {
            region.sampled[i] = body.readLong();
        }
        return region;
    }
}
