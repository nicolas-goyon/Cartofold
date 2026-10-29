package io.github.nicolasgoyon.cartofold.tile;

import io.github.nicolasgoyon.cartofold.data.MapRegion;
import javax.annotation.Nullable;
import net.minecraft.world.level.material.MapColor;

/**
 * Makes the pixels of a region, with the shades of the vanilla maps.
 *
 * <p>A level of detail {@code lod} gives an image of {@code 512 >> lod} pixels on each side: one pixel for each cell of
 * {@code 2^lod} x {@code 2^lod} blocks. A pixel shows the column at the center of its cell.
 *
 * <ul>
 *   <li>A land pixel is lighter when its column is higher than the column of the cell to the north, and darker when it
 *       is lower.</li>
 *   <li>A water pixel is darker when the water is deeper.</li>
 *   <li>A pixel of a chunk with no data, or of a column with no color, is transparent.</li>
 * </ul>
 *
 * <p>The pixels are in the ABGR format of {@code NativeImage}, at the index {@code x + z * size}.
 */
public final class TileImage {
    /** The number of levels of detail: images of 512, 256, 128 and 64 pixels. */
    public static final int LODS = 4;

    private TileImage() {
    }

    /** The size of the image of a region at a level of detail, in pixels. */
    public static int size(int lod) {
        if (lod < 0 || lod >= LODS) {
            throw new IllegalArgumentException("No level of detail " + lod);
        }
        return MapRegion.SIZE >> lod;
    }

    /**
     * Makes the pixels of a region.
     *
     * @param region the region
     * @param north  the region to the north, for the shade of the first row, or null
     * @param lod    the level of detail
     * @return the pixels in ABGR
     */
    public static int[] render(MapRegion region, @Nullable MapRegion north, int lod) {
        int size = size(lod);
        int cell = 1 << lod;
        int half = cell / 2;
        int minX = region.pos().minBlockX();
        int minZ = region.pos().minBlockZ();
        int[] pixels = new int[size * size];
        for (int z = 0; z < size; z++) {
            int blockZ = minZ + z * cell + half;
            for (int x = 0; x < size; x++) {
                int blockX = minX + x * cell + half;
                if (!known(region, blockX, blockZ)) {
                    continue;
                }
                int id = region.color(blockX, blockZ);
                if (id == 0) {
                    continue;
                }
                int checker = ((blockX >> lod) + (blockZ >> lod)) & 1;
                MapColor.Brightness brightness;
                if (id == MapColor.WATER.id) {
                    brightness = waterBrightness(region.depth(blockX, blockZ), checker);
                } else {
                    int height = region.height(blockX, blockZ);
                    int northZ = blockZ - cell;
                    MapRegion source = z > 0 ? region : north;
                    int northHeight = source != null && known(source, blockX, northZ)
                            ? source.height(blockX, northZ) : height;
                    brightness = landBrightness(height - northHeight, cell, checker);
                }
                pixels[x + z * size] = MapColor.byId(id).calculateRGBColor(brightness);
            }
        }
        return pixels;
    }

    private static boolean known(MapRegion region, int blockX, int blockZ) {
        return region.sampled(blockX >> 4, blockZ >> 4) != MapRegion.NEVER;
    }

    /** The shade of water, as on the vanilla maps. */
    public static MapColor.Brightness waterBrightness(int depth, int checker) {
        double value = depth * 0.1 + checker * 0.2;
        if (value < 0.5) {
            return MapColor.Brightness.HIGH;
        }
        return value > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
    }

    /** The shade of land from the difference of height with the north, as on the vanilla maps. */
    public static MapColor.Brightness landBrightness(int difference, int cell, int checker) {
        double value = difference * 4.0 / (cell + 4) + (checker - 0.5) * 0.4;
        if (value > 0.6) {
            return MapColor.Brightness.HIGH;
        }
        return value < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
    }
}
