package io.github.nicolasgoyon.cartofold.gametest;

import com.mojang.authlib.GameProfile;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.data.ChunkColumns;
import io.github.nicolasgoyon.cartofold.data.ExplorationKey;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.MapSlice;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.network.ExplorationChunksPayload;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapData;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapExploration;
import io.github.nicolasgoyon.cartofold.server.ColumnSampler;
import io.github.nicolasgoyon.cartofold.server.ExplorationService;
import io.github.nicolasgoyon.cartofold.server.ExplorationSync;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
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

    /** A column beside {@link #GRASS} in the same chunk: the place of the test changes at each run. */
    private static BlockPos besideInTheChunk(GameTestHelper helper) {
        BlockPos grass = helper.absolutePos(GRASS);
        for (BlockPos candidate : new BlockPos[] {
            new BlockPos(1, 0, 0), new BlockPos(-1, 0, 0), new BlockPos(0, 0, 1), new BlockPos(0, 0, -1)
        }) {
            BlockPos absolute = helper.absolutePos(candidate);
            if (absolute.getX() >> 4 == grass.getX() >> 4 && absolute.getZ() >> 4 == grass.getZ() >> 4) {
                return candidate;
            }
        }
        throw new IllegalStateException("A column always has a neighbor in its chunk");
    }

    private static int column(BlockPos pos) {
        return (pos.getX() & 15) + (pos.getZ() & 15) * 16;
    }

    /** The sampler gives the color and the height of the top block, and the depth of the water. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void samplerReadsTheColumns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos waterColumn = besideInTheChunk(helper);
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
        // Another test can sample the chunk before its blocks: a new sample reads them.
        service.requestSample(level.dimension(), new ChunkPos(grass));

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

    /**
     * A client that shows a region gets the chunks of the region that the player explored. A chunk that the player
     * did not explore stays on the server.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void clientGetsTheExploredChunksOfItsRegions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        build(helper, new BlockPos(1, 0, 0));
        BlockPos grass = helper.absolutePos(GRASS);
        ChunkPos chunk = new ChunkPos(grass);
        ExplorationService service = ExplorationService.get().orElseThrow();
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "cartofold_sync"));
        ServerPlayer other = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "cartofold_other"));
        player.setPos(grass.getX() + 0.5, grass.getY() + 1, grass.getZ() + 0.5);
        RegionPos pos = RegionPos.ofChunk(chunk.x, chunk.z);
        ExplorationSync sync = service.sync();

        service.explore(player);
        service.requestSample(level.dimension(), chunk);
        helper.succeedWhen(() -> {
            helper.assertTrue(service.region(level.dimension(), pos, level.getGameTime())
                    .sampled(chunk.x, chunk.z) != MapRegion.NEVER, "The chunk must get a sample");
            sync.requestSurface(player.getUUID(), level.dimension(), List.of(pos), level.getGameTime());
            sync.requestSurface(other.getUUID(), level.dimension(), List.of(pos), level.getGameTime());
            helper.assertTrue(sync.pending(other.getUUID()) == 0, "The other player explored no chunk");
            ExplorationChunksPayload payload = sync.poll(player.getUUID(), ExplorationChunksPayload.MAX_CHUNKS,
                    level.getGameTime());
            helper.assertTrue(payload != null, "The player gets chunks");
            ExplorationChunksPayload.Chunk sent = payload.chunks().stream()
                    .filter(found -> found.x() == chunk.x && found.z() == chunk.z).findFirst().orElseThrow();
            helper.assertTrue(sent.columns().colors()[(grass.getX() & 15) + (grass.getZ() & 15) * 16]
                    == MapColor.GRASS.id, "The chunk has the color of the grass");
            sync.requestSurface(player.getUUID(), level.dimension(), List.of(), level.getGameTime());
            sync.remove(player.getUUID());
            sync.remove(other.getUUID());
        });
    }

    /** Under a ceiling, the sampler gives the floor: the first block with a color under the air under the ceiling. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void samplerReadsTheFloorUnderACeiling(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = new BlockPos(1, 1, 1);
        for (int y = 1; y < 12; y++) {
            helper.setBlock(floor.atY(y), Blocks.AIR);
        }
        helper.setBlock(floor, Blocks.NETHERRACK);
        helper.setBlock(floor.atY(8), Blocks.BEDROCK);
        helper.setBlock(floor.atY(9), Blocks.BEDROCK);
        BlockPos ground = helper.absolutePos(floor);
        BlockPos top = helper.absolutePos(floor.atY(9));
        LevelChunk chunk = level.getChunkAt(ground);

        ChunkColumns columns = ColumnSampler.sampleUnder(level, chunk, top.getY());

        helper.assertTrue(columns.colors()[column(ground)] == MapColor.NETHER.id, "The color of the floor: "
                + columns.colors()[column(ground)]);
        helper.assertTrue(columns.heights()[column(ground)] == ground.getY(), "The height of the floor");
        helper.succeed();
    }

    /**
     * A survey map in the hand of a player gets the colors of the exploration data near the player. A locked map does
     * not change.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void surveyMapExploresNearThePlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        build(helper, new BlockPos(1, 0, 0));
        BlockPos grass = helper.absolutePos(GRASS);
        ExplorationService service = ExplorationService.get().orElseThrow();
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "cartofold_survey"));
        player.setPos(grass.getX() + 0.5, grass.getY() + 1, grass.getZ() + 0.5);
        SurveyMapData data = SurveyMapData.around(level.dimension(), grass.getX(), grass.getZ(), 0);
        SurveyMapData locked = SurveyMapData.around(level.dimension(), grass.getX(), grass.getZ(), 0).lockedCopy();
        int px = data.geometry().pixelX(grass.getX());
        int pz = data.geometry().pixelZ(grass.getZ());

        service.explore(player);
        service.requestSample(level.dimension(), new ChunkPos(grass));
        helper.succeedWhen(() -> {
            SurveyMapExploration.update(player, data);
            SurveyMapExploration.update(player, locked);
            helper.assertTrue((data.colors()[px + pz * 128] & 0xFF) >> 2 == MapColor.GRASS.id,
                    "The pixel of the grass has the color of the grass");
            helper.assertTrue(locked.colors()[px + pz * 128] == 0, "A locked map does not change");
        });
    }

    /**
     * A player in a closed room of rock explores the band of caves of its height: the band gets the floor under the
     * player, under the rock.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void playerInACaveExploresItsBand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = new BlockPos(2, 1, 2);
        BlockPos ground = helper.absolutePos(floor);
        int top = MapSlice.caveAt(ground.getY() + 1).maxY() + 1;
        // A room of 1 x 3 x 1 blocks of air in rock, from the floor up to the top of the band of the player.
        for (int y = ground.getY(); y <= top; y++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    level.setBlockAndUpdate(ground.offset(dx, y - ground.getY(), dz), Blocks.STONE.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(ground, Blocks.MOSS_BLOCK.defaultBlockState());
        for (int y = 1; y <= 3; y++) {
            level.setBlockAndUpdate(ground.above(y), Blocks.AIR.defaultBlockState());
        }
        ExplorationService service = ExplorationService.get().orElseThrow();
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "cartofold_cave"));
        player.setPos(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5);
        ExplorationKey cave = new ExplorationKey(level.dimension(), MapSlice.caveAt(player.getBlockY()));
        RegionPos pos = RegionPos.ofBlock(ground.getX(), ground.getZ());

        helper.succeedWhen(() -> {
            // The light engine needs some ticks for the light of the room.
            helper.assertTrue(ExplorationService.underground(player), "The player is in a cave");
            service.explore(player);
            service.requestSample(cave, new ChunkPos(ground));
            helper.assertTrue(service.mask(cave, player.getUUID(), pos, level.getGameTime())
                    .isExplored(ground.getX(), ground.getZ()), "The player explores the column in its band");
            MapRegion region = service.region(cave, pos, level.getGameTime());
            helper.assertTrue(region.sampled(ground.getX() >> 4, ground.getZ() >> 4) != MapRegion.NEVER,
                    "The chunk of the band must get a sample");
            helper.assertTrue(region.color(ground.getX(), ground.getZ()) == MapColor.COLOR_GREEN.id,
                    "The band has the color of the floor: " + region.color(ground.getX(), ground.getZ()));
        });
    }
}
