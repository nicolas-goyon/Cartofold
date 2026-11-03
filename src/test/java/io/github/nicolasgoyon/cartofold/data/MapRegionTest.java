package io.github.nicolasgoyon.cartofold.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class MapRegionTest {
    /** The region (-1, 2) has the chunks -32 to -1 on X and 64 to 95 on Z. */
    private static final RegionPos POS = new RegionPos(-1, 2);

    private static ChunkColumns columns(int color, int height) {
        ChunkColumns columns = ChunkColumns.empty();
        for (int i = 0; i < ChunkColumns.COLUMNS; i++) {
            columns.colors()[i] = (byte) color;
            columns.heights()[i] = (short) (height + i % 16);
            columns.depths()[i] = (byte) (i == 0 ? 3 : 0);
        }
        return columns;
    }

    @Test
    void positionsOfNegativeBlocks() {
        assertEquals(new RegionPos(-1, 0), RegionPos.ofBlock(-1, 511));
        assertEquals(new RegionPos(0, 1), RegionPos.ofBlock(511, 512));
        assertEquals(new RegionPos(-1, 2), RegionPos.ofChunk(-1, 64));
        assertEquals(-512, POS.minBlockX());
        assertEquals("r.-1.2.dat", POS.fileName());
    }

    /** A sample of a chunk gives the columns of its blocks, and its game time. A second same sample changes nothing. */
    @Test
    void sampleOfAChunk() {
        MapRegion region = new MapRegion(POS);
        assertEquals(MapRegion.NEVER, region.sampled(-3, 70));
        assertTrue(region.put(-3, 70, columns(12, 64), 100L));
        assertEquals(12, region.color(-48, 1120));
        assertEquals(64, region.height(-48, 1120));
        assertEquals(64 + 15, region.height(-33, 1120));
        assertEquals(3, region.depth(-48, 1120));
        assertEquals(0, region.color(-49, 1120), "The next chunk has no data");
        assertEquals(100L, region.sampled(-3, 70));
        assertFalse(region.put(-3, 70, columns(12, 64), 200L));
        assertEquals(200L, region.sampled(-3, 70));
        assertTrue(region.isDirty());
    }

    @Test
    void chunkOutOfTheRegion() {
        MapRegion region = new MapRegion(POS);
        assertThrows(IllegalArgumentException.class, () -> region.put(0, 70, columns(1, 1), 0L));
    }

    /** A region keeps its columns and the times of its samples in its file. */
    @Test
    void fileKeepsTheRegion() throws IOException {
        MapRegion region = new MapRegion(POS);
        region.put(-3, 70, columns(200, -40), 7L);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        region.write(out);
        MapRegion read = MapRegion.read(POS, new ByteArrayInputStream(out.toByteArray()));
        assertEquals(200, read.color(-48, 1120));
        assertEquals(-40, read.height(-48, 1120));
        assertEquals(3, read.depth(-48, 1120));
        assertEquals(7L, read.sampled(-3, 70));
        assertEquals(MapRegion.NEVER, read.sampled(-4, 70));
        assertFalse(read.isDirty());
        assertTrue(out.size() < 64 * 1024, "The file is compressed: " + out.size());
    }
}
