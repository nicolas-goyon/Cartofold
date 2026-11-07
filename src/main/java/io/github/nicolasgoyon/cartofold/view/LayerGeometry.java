package io.github.nicolasgoyon.cartofold.view;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.world.level.ChunkPos;

/** The geometry of the layers, in blocks. */
public final class LayerGeometry {
    private LayerGeometry() {
    }

    /** A box of blocks that contains all the world, for {@link #border}. */
    public static double[] all() {
        return new double[] {
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
    }

    /**
     * The chunks of an area as rows: each row is a run of chunks next to each other on X, with the same Z. A large
     * area has much fewer rows than chunks, so the fill of the area needs much fewer quads.
     *
     * @param chunks the chunks of the area, as {@link ChunkPos#toLong()}
     * @return the rows, as groups of three values: the Z of the chunks, the X of the first chunk and the X of the last
     *         chunk; the rows go from the north to the south, then from the west to the east
     */
    public static int[] rows(LongSet chunks) {
        long[] sorted = new long[chunks.size()];
        int count = 0;
        for (long packed : chunks) {
            // The sign bit of X flips: the order of the unsigned low half is then the order of X.
            sorted[count++] = ((long) ChunkPos.getZ(packed) << 32)
                    | ((ChunkPos.getX(packed) ^ Integer.MIN_VALUE) & 0xFFFFFFFFL);
        }
        Arrays.sort(sorted);
        int[] rows = new int[count * 3];
        int size = 0;
        for (long key : sorted) {
            int z = (int) (key >> 32);
            int x = (int) key ^ Integer.MIN_VALUE;
            if (size > 0 && rows[size - 3] == z && rows[size - 1] == x - 1) {
                rows[size - 1] = x;
            } else {
                rows[size++] = z;
                rows[size++] = x;
                rows[size++] = x;
            }
        }
        return Arrays.copyOf(rows, size);
    }

    /**
     * Joins the sides on the same line that touch: the border needs fewer lines.
     *
     * @param sides the sides, as the X and the Z of their two ends, in blocks, from the smallest to the largest
     *              coordinate, as {@link #border} gives them
     * @return the joined sides, in the same format
     */
    public static List<int[]> join(List<int[]> sides) {
        List<int[]> horizontal = new ArrayList<>();
        List<int[]> vertical = new ArrayList<>();
        for (int[] side : sides) {
            (side[1] == side[3] ? horizontal : vertical).add(side);
        }
        List<int[]> result = new ArrayList<>();
        // A horizontal side keeps its Z: it goes on X. A vertical side keeps its X: it goes on Z.
        joinLine(horizontal, 1, 0, 2, result);
        joinLine(vertical, 0, 1, 3, result);
        return result;
    }

    private static void joinLine(List<int[]> sides, int line, int start, int end, List<int[]> result) {
        sides.sort(Comparator.<int[]>comparingInt(side -> side[line]).thenComparingInt(side -> side[start]));
        int[] current = null;
        for (int[] side : sides) {
            if (current != null && current[line] == side[line] && current[end] >= side[start]) {
                current[end] = Math.max(current[end], side[end]);
            } else {
                current = side.clone();
                result.add(current);
            }
        }
    }

    /**
     * The sides of the chunks of an area that have no chunk of the area on the other side: the border of the area.
     *
     * @param chunks the chunks of the area, as {@link ChunkPos#toLong()}
     * @param bounds the box of blocks to keep (minimum X, minimum Z, maximum X, maximum Z): the sides of the chunks
     *               out of this box are not in the result
     * @return the sides, as the X and the Z of their two ends, in blocks
     */
    public static List<int[]> border(LongSet chunks, double[] bounds) {
        List<int[]> sides = new ArrayList<>();
        chunks.forEach(packed -> {
            int chunkX = ChunkPos.getX(packed);
            int chunkZ = ChunkPos.getZ(packed);
            int minX = chunkX << 4;
            int minZ = chunkZ << 4;
            if (minX + 16 < bounds[0] || minX > bounds[2] || minZ + 16 < bounds[1] || minZ > bounds[3]) {
                return;
            }
            if (!chunks.contains(ChunkPos.asLong(chunkX, chunkZ - 1))) {
                sides.add(new int[] {minX, minZ, minX + 16, minZ});
            }
            if (!chunks.contains(ChunkPos.asLong(chunkX, chunkZ + 1))) {
                sides.add(new int[] {minX, minZ + 16, minX + 16, minZ + 16});
            }
            if (!chunks.contains(ChunkPos.asLong(chunkX - 1, chunkZ))) {
                sides.add(new int[] {minX, minZ, minX, minZ + 16});
            }
            if (!chunks.contains(ChunkPos.asLong(chunkX + 1, chunkZ))) {
                sides.add(new int[] {minX + 16, minZ, minX + 16, minZ + 16});
            }
        });
        return sides;
    }
}
