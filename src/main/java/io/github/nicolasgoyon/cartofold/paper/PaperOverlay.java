package io.github.nicolasgoyon.cartofold.paper;

import io.github.nicolasgoyon.cartofold.api.ChunkArea;
import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.api.MapLine;
import io.github.nicolasgoyon.cartofold.api.MapMarker;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Makes the pixels of the layers on a vanilla map (a paper map). The layers show only on the explored pixels of the
 * map (a color that is not 0), whatever the setting "only explored" of the layer: the paper map shows only what its
 * holders explored.
 *
 * <ul>
 *   <li>An area fills its pixels, and draws its border on the pixels next to a pixel out of the area.</li>
 *   <li>A line draws one pixel of width.</li>
 *   <li>A marker draws a small cross of its color, and a dark middle.</li>
 * </ul>
 *
 * <p>The pixels are in the ABGR format of {@code NativeImage}, at the index {@code x + z * 128}. A pixel with no layer is
 * transparent.
 */
public final class PaperOverlay {
    private static final int SIZE = PaperMapGeometry.SIZE;
    private static final int MARKER_MIDDLE = 0xFF202020;

    private PaperOverlay() {
    }

    /**
     * Makes the pixels of the layers.
     *
     * @param geometry  the geometry of the map
     * @param dimension the dimension of the map
     * @param colors    the colors of the map: 0 for a pixel that is not explored
     * @param layers    the layers, in their order
     * @return the pixels in ABGR
     */
    public static int[] render(PaperMapGeometry geometry, ResourceKey<Level> dimension, byte[] colors,
            List<LayerContent> layers) {
        int[] argb = new int[SIZE * SIZE];
        int half = geometry.factor() / 2;
        long[] chunks = new long[SIZE * SIZE];
        for (int pz = 0; pz < SIZE; pz++) {
            for (int px = 0; px < SIZE; px++) {
                int blockX = geometry.blockX(px) + half;
                int blockZ = geometry.blockZ(pz) + half;
                chunks[px + pz * SIZE] = ChunkPos.asLong(blockX >> 4, blockZ >> 4);
            }
        }
        for (LayerContent layer : layers) {
            for (ChunkArea area : layer.areas()) {
                if (area.dimension().equals(dimension)) {
                    drawArea(argb, area, chunks, colors);
                }
            }
            for (MapLine line : layer.lines()) {
                if (line.dimension().equals(dimension)) {
                    drawLine(argb, line, geometry, colors);
                }
            }
            for (MapMarker marker : layer.markers()) {
                if (marker.dimension().equals(dimension)) {
                    drawMarker(argb, marker, geometry, colors);
                }
            }
        }
        int[] abgr = new int[SIZE * SIZE];
        for (int i = 0; i < abgr.length; i++) {
            int color = argb[i];
            abgr[i] = (color & 0xFF00FF00) | ((color >> 16) & 0xFF) | ((color & 0xFF) << 16);
        }
        return abgr;
    }

    private static void drawArea(int[] argb, ChunkArea area, long[] chunks, byte[] colors) {
        for (int pz = 0; pz < SIZE; pz++) {
            for (int px = 0; px < SIZE; px++) {
                int index = px + pz * SIZE;
                if (colors[index] == 0 || !area.chunks().contains(chunks[index])) {
                    continue;
                }
                boolean border = !inside(area, chunks, px - 1, pz) || !inside(area, chunks, px + 1, pz)
                        || !inside(area, chunks, px, pz - 1) || !inside(area, chunks, px, pz + 1);
                int color = border && (area.borderColor() >>> 24) != 0 ? area.borderColor() : area.fillColor();
                argb[index] = blend(argb[index], color);
            }
        }
    }

    /** True if a pixel is in the area. A pixel out of the map counts as in the area: the side of the map is no border. */
    private static boolean inside(ChunkArea area, long[] chunks, int px, int pz) {
        if (px < 0 || pz < 0 || px >= SIZE || pz >= SIZE) {
            return true;
        }
        return area.chunks().contains(chunks[px + pz * SIZE]);
    }

    private static void drawLine(int[] argb, MapLine line, PaperMapGeometry geometry, byte[] colors) {
        for (int i = 1; i < line.points().size(); i++) {
            BlockPos from = line.points().get(i - 1);
            BlockPos to = line.points().get(i);
            int x0 = geometry.pixelX(from.getX());
            int z0 = geometry.pixelZ(from.getZ());
            int x1 = geometry.pixelX(to.getX());
            int z1 = geometry.pixelZ(to.getZ());
            if (Math.max(x0, x1) < 0 || Math.min(x0, x1) >= SIZE || Math.max(z0, z1) < 0
                    || Math.min(z0, z1) >= SIZE) {
                continue;
            }
            int dx = Math.abs(x1 - x0);
            int dz = -Math.abs(z1 - z0);
            int stepX = x0 < x1 ? 1 : -1;
            int stepZ = z0 < z1 ? 1 : -1;
            int error = dx + dz;
            int x = x0;
            int z = z0;
            while (true) {
                plot(argb, colors, x, z, line.color());
                if (x == x1 && z == z1) {
                    break;
                }
                int doubled = 2 * error;
                if (doubled >= dz) {
                    error += dz;
                    x += stepX;
                }
                if (doubled <= dx) {
                    error += dx;
                    z += stepZ;
                }
            }
        }
    }

    private static void drawMarker(int[] argb, MapMarker marker, PaperMapGeometry geometry, byte[] colors) {
        int x = geometry.pixelX(marker.pos().getX());
        int z = geometry.pixelZ(marker.pos().getZ());
        if (x < 0 || z < 0 || x >= SIZE || z >= SIZE || colors[x + z * SIZE] == 0) {
            return;
        }
        int color = 0xFF000000 | marker.color();
        plot(argb, colors, x - 1, z, color);
        plot(argb, colors, x + 1, z, color);
        plot(argb, colors, x, z - 1, color);
        plot(argb, colors, x, z + 1, color);
        plot(argb, colors, x, z, MARKER_MIDDLE);
    }

    private static void plot(int[] argb, byte[] colors, int x, int z, int color) {
        if (x < 0 || z < 0 || x >= SIZE || z >= SIZE || colors[x + z * SIZE] == 0) {
            return;
        }
        argb[x + z * SIZE] = blend(argb[x + z * SIZE], color);
    }

    /** A color above another color, in ARGB. */
    static int blend(int below, int above) {
        int alpha = above >>> 24;
        if (alpha == 0xFF || below == 0) {
            return alpha == 0 ? below : above;
        }
        if (alpha == 0) {
            return below;
        }
        int belowAlpha = below >>> 24;
        int outAlpha = alpha + belowAlpha * (255 - alpha) / 255;
        int red = mix((above >> 16) & 0xFF, alpha, (below >> 16) & 0xFF, belowAlpha, outAlpha);
        int green = mix((above >> 8) & 0xFF, alpha, (below >> 8) & 0xFF, belowAlpha, outAlpha);
        int blue = mix(above & 0xFF, alpha, below & 0xFF, belowAlpha, outAlpha);
        return (outAlpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int mix(int above, int alpha, int below, int belowAlpha, int outAlpha) {
        return (above * alpha + below * belowAlpha * (255 - alpha) / 255) / outAlpha;
    }
}
