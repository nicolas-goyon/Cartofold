package io.github.nicolasgoyon.cartofold.gametest;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.data.ChunkColumns;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.server.ColumnSampler;
import io.github.nicolasgoyon.cartofold.server.ExplorationService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the exploration data on the server (see {@link ExplorationService}). */
@GameTestHolder(Cartofold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExplorationGameTests {
    private static final BlockPos GRASS = new BlockPos(0, 1, 0);
    private static final int WATER_DEPTH = 3;

    private ExplorationGameTests() {
    }

    /** Builds a grass column and a column of water on stone, with nothing above them. */
    private static void build(GameTestHelper helper, BlockPos waterColumn) {
        for (int y = 1; y < 40; y++) {
            helper.setBlock(GRASS.atY(y), Blocks.AIR);
            helper.setBlock(waterColumn.atY(y), Blocks.AIR);
        }
        helper.setBlock(GRASS, Blocks.GRASS_BLOCK);
        helper.setBlock(waterColumn.atY(0), Blocks.STONE);
        for (int y = 1; y <= WATER_DEPTH; y++) {
            helper.setBlock(waterColumn.atY(y), Blocks.WATER);
        }
    }

    private static int column(BlockPos pos) {
        return (pos.getX() & 15) + (pos.getZ() & 15) * 16;
    }

    /** The sampler gives the color and the height of the top block, and the depth of the water. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void samplerReadsTheColumns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos waterColumn = new BlockPos(1, 0, 0);
        build(helper, waterColumn);
        BlockPos grass = helper.absolutePos(GRASS);
        BlockPos water = helper.absolutePos(waterColumn.atY(WATER_DEPTH));
        helper.assertTrue(grass.getX() >> 4 == water.getX() >> 4 && grass.getZ() >> 4 == water.getZ() >> 4,
                "The two columns are in one chunk");
        LevelChunk chunk = level.getChunkAt(grass);

        ChunkColumns columns = ColumnSampler.sample(level, chunk).orElseThrow();

        helper.assertTrue(columns.colors()[column(grass)] == MapColor.GRASS.id, "The color of the grass");
        helper.assertTrue(columns.heights()[column(grass)] == grass.getY(), "The height of the grass");
        helper.assertTrue(columns.depths()[column(grass)] == 0, "No water on the grass");
        helper.assertTrue(columns.colors()[column(water)] == MapColor.WATER.id, "The color of the water");
        helper.assertTrue(columns.heights()[column(water)] == water.getY(), "The height of the water surface");
        helper.assertTrue(columns.depths()[column(water)] == WATER_DEPTH, "The depth of the water: "
                + columns.depths()[column(water)]);
        helper.succeed();
    }

    /**
     * A player near a loaded chunk explores it: its mask gets the columns of the chunk, and the next ticks of the
     * service sample the chunk into its region.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void playerExploresTheChunksNearIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        build(helper, new BlockPos(1, 0, 0));
        BlockPos grass = helper.absolutePos(GRASS);
        ExplorationService service = ExplorationService.get().orElseThrow();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.setPos(grass.getX() + 0.5, grass.getY() + 1, grass.getZ() + 0.5);
        RegionPos pos = RegionPos.ofBlock(grass.getX(), grass.getZ());

        service.explore(player);

        helper.assertTrue(service.mask(level.dimension(), player.getUUID(), pos, level.getGameTime())
                .isExplored(grass.getX(), grass.getZ()), "The player explores the column");
        helper.succeedWhen(() -> {
            MapRegion region = service.region(level.dimension(), pos, level.getGameTime());
            helper.assertTrue(region.sampled(grass.getX() >> 4, grass.getZ() >> 4) != MapRegion.NEVER,
                    "The chunk must get a sample");
            helper.assertTrue(region.color(grass.getX(), grass.getZ()) == MapColor.GRASS.id,
                    "The region has the color of the grass");
        });
    }
}
