package io.github.nicolasgoyon.cartofold.view;

import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;

/** The geometry of the layers, in blocks. */
public final class LayerGeometry {
    private LayerGeometry() {
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
