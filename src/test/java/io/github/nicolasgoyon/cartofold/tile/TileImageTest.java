package io.github.nicolasgoyon.cartofold.tile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nicolasgoyon.cartofold.data.ChunkColumns;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import java.util.Arrays;
import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.Test;

class TileImageTest {
    /** A chunk of one color at one height. */
    private static ChunkColumns flat(MapColor color, int height, int depth) {
        ChunkColumns columns = ChunkColumns.empty();
        Arrays.fill(columns.colors(), (byte) color.id);
        Arrays.fill(columns.heights(), (short) height);
        Arrays.fill(columns.depths(), (byte) depth);
        return columns;
    }

    @Test
    void chunksWithNoDataAreTransparent() {
        MapRegion region = new MapRegion(new RegionPos(0, 0));
        region.put(0, 0, flat(MapColor.GRASS, 64, 0), 1);

        int[] pixels = TileImage.render(region, null, 0);

        assertEquals(512 * 512, pixels.length);
        assertEquals(MapColor.GRASS.calculateRGBColor(MapColor.Brightness.NORMAL), pixels[5 + 5 * 512]);
        assertEquals(0, pixels[16 + 5 * 512]);
    }

    @Test
    void higherLandIsLighter() {
        MapRegion region = new MapRegion(new RegionPos(0, 0));
        ChunkColumns columns = flat(MapColor.STONE, 64, 0);
        for (int x = 0; x < 16; x++) {
            columns.heights()[x + 8 * 16] = 70;
        }
        region.put(0, 0, columns, 1);

        int[] pixels = TileImage.render(region, null, 0);

        assertEquals(MapColor.STONE.calculateRGBColor(MapColor.Brightness.HIGH), pixels[4 + 8 * 512]);
        assertEquals(MapColor.STONE.calculateRGBColor(MapColor.Brightness.LOW), pixels[4 + 9 * 512]);
    }

    @Test
    void northRegionShadesTheFirstRow() {
        MapRegion region = new MapRegion(new RegionPos(0, 0));
        MapRegion north = new MapRegion(new RegionPos(0, -1));
        region.put(0, 0, flat(MapColor.STONE, 64, 0), 1);
        north.put(0, -1, flat(MapColor.STONE, 50, 0), 1);

        assertEquals(MapColor.STONE.calculateRGBColor(MapColor.Brightness.HIGH),
                TileImage.render(region, north, 0)[2]);
        assertEquals(MapColor.STONE.calculateRGBColor(MapColor.Brightness.NORMAL),
                TileImage.render(region, null, 0)[2]);
    }

    @Test
    void deepWaterIsDarker() {
        assertEquals(MapColor.Brightness.HIGH, TileImage.waterBrightness(1, 0));
        assertEquals(MapColor.Brightness.LOW, TileImage.waterBrightness(10, 0));
    }

    @Test
    void lowDetailHasFewerPixels() {
        MapRegion region = new MapRegion(new RegionPos(-1, 2));
        region.put(-32, 64, flat(MapColor.SAND, 64, 0), 1);

        int[] pixels = TileImage.render(region, null, 3);

        assertEquals(64 * 64, pixels.length);
        assertEquals(MapColor.SAND.calculateRGBColor(MapColor.Brightness.NORMAL), pixels[0]);
        assertEquals(0, pixels[2]);
        assertThrows(IllegalArgumentException.class, () -> TileImage.size(TileImage.LODS));
    }
}
