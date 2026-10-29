package io.github.nicolasgoyon.cartofold.server;

import io.github.nicolasgoyon.cartofold.data.ChunkColumns;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Reads the map data of the columns of a chunk, as the vanilla maps do: the top block with a map color, its height,
 * and the depth of the water over the floor.
 *
 * <ul>
 *   <li>The top of a column is the block under the surface heightmap ({@link Heightmap.Types#WORLD_SURFACE}). The
 *       sampler goes down to the first block with a map color.</li>
 *   <li>For a column of water, the depth is the number of blocks of water over the floor (255 at most). The color is the
 *       color of the water when the water block is not a full block.</li>
 *   <li>In a dimension with a ceiling (the Nether), the sampler starts at the top of the logical height of the
 *       dimension, goes down through the ceiling to the first block with no map color (the air under the ceiling),
 *       then down to the first block with a map color: the floor under the ceiling. A column with no air under the
 *       ceiling has no color.</li>
 * </ul>
 */
public final class ColumnSampler {
    private static final int MAX_DEPTH = 255;

    private ColumnSampler() {
    }

    /**
     * Reads the columns of a chunk.
     *
     * @param level the level of the chunk
     * @param chunk the chunk
     * @return the columns
     */
    public static Optional<ChunkColumns> sample(Level level, LevelChunk chunk) {
        OptionalInt ceiling = level.dimensionType().hasCeiling()
                ? OptionalInt.of(level.getMinBuildHeight() + level.dimensionType().logicalHeight() - 1)
                : OptionalInt.empty();
        return Optional.of(sample(level, chunk, ceiling));
    }

    /**
     * Reads the columns of a chunk under a ceiling, for the tests in a dimension with no ceiling.
     *
     * @param level   the level of the chunk
     * @param chunk   the chunk
     * @param ceiling the Y of the top of the ceiling
     * @return the columns
     */
    public static ChunkColumns sampleUnder(Level level, LevelChunk chunk, int ceiling) {
        return sample(level, chunk, OptionalInt.of(ceiling));
    }

    /**
     * Reads the columns of a chunk.
     *
     * @param level   the level of the chunk
     * @param chunk   the chunk
     * @param ceiling the Y of the top of the ceiling, or empty for a dimension with no ceiling
     * @return the columns
     */
    static ChunkColumns sample(Level level, LevelChunk chunk, OptionalInt ceiling) {
        ChunkColumns columns = ChunkColumns.empty();
        int minY = level.getMinBuildHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
                BlockState state;
                int depth = 0;
                MapColor color = MapColor.NONE;
                if (ceiling.isPresent()) {
                    y = ceiling.getAsInt() + 1;
                    // Down through the ceiling, to the first block with no map color.
                    do {
                        pos.set(baseX + x, --y, baseZ + z);
                    } while (y > minY && chunk.getBlockState(pos).getMapColor(level, pos) != MapColor.NONE);
                    y++;
                }
                if (y > minY + 1) {
                    do {
                        pos.set(baseX + x, --y, baseZ + z);
                        state = chunk.getBlockState(pos);
                        color = state.getMapColor(level, pos);
                    } while (color == MapColor.NONE && y > minY);
                    if (y > minY && !state.getFluidState().isEmpty()) {
                        int bottom = y - 1;
                        below.set(pos);
                        BlockState under;
                        do {
                            below.setY(bottom--);
                            under = chunk.getBlockState(below);
                            depth++;
                        } while (bottom > minY && !under.getFluidState().isEmpty() && depth < MAX_DEPTH);
                        if (!state.isFaceSturdy(level, pos, Direction.UP)) {
                            color = state.getFluidState().createLegacyBlock().getMapColor(level, pos);
                        }
                    }
                }
                int column = x + z * 16;
                columns.colors()[column] = (byte) color.id;
                columns.heights()[column] = (short) y;
                columns.depths()[column] = (byte) depth;
            }
        }
        return columns;
    }
}
