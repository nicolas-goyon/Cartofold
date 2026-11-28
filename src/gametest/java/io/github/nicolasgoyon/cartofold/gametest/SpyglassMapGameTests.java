package io.github.nicolasgoyon.cartofold.gametest;

import com.mojang.authlib.GameProfile;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.CartofoldServerConfig;
import io.github.nicolasgoyon.cartofold.data.ChunkColumns;
import io.github.nicolasgoyon.cartofold.data.ExploredMask;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.paper.SpyglassMaps;
import io.github.nicolasgoyon.cartofold.server.ExplorationService;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the exploration with a spyglass: the survey maps and the discovery (see {@link SpyglassMaps}). */
@GameTestHolder(Cartofold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpyglassMapGameTests {
    private static final int SLOT = 20;
    /** The number of explorations of the measure of the cost. */
    private static final int COST_RUNS = 50;
    /** The largest time of one exploration: a fifth of a tick. */
    private static final long COST_LIMIT_NANOS = 10_000_000L;

    private SpyglassMapGameTests() {
    }

    /** A player with a spyglass in the inventory updates a map in a larger radius. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void spyglassMakesTheRadiusLarger(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "cartofold_spyglass"));
        int without = SpyglassMaps.radius(128, player);
        player.getInventory().setItem(SLOT, new ItemStack(Items.SPYGLASS));
        int with = SpyglassMaps.radius(128, player);

        helper.assertTrue(without == 128, "With no spyglass, the radius of the game: " + without);
        helper.assertTrue(with == 128 * SpyglassMaps.FACTOR, "With a spyglass, a larger radius: " + with);
        helper.succeed();
    }

    /** The radius of the exploration with a spyglass is the normal radius times the factor of the server. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void spyglassMakesTheDiscoveryRadiusLarger(GameTestHelper helper) {
        ExplorationService service = ExplorationService.get().orElseThrow();
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "cartofold_spyglass_radius"));
        int normal = service.normalRadius();
        int without = service.discoveryRadius(player);
        player.getInventory().setItem(SLOT, new ItemStack(Items.SPYGLASS));
        int with = service.discoveryRadius(player);
        CartofoldServerConfig.SPYGLASS_FACTOR.set(1.5);
        int smaller;
        try {
            smaller = service.discoveryRadius(player);
        } finally {
            CartofoldServerConfig.SPYGLASS_FACTOR.set(SpyglassMaps.DEFAULT_FACTOR);
        }

        helper.assertTrue(without == normal, "With no spyglass, the normal radius: " + without);
        helper.assertTrue(with == 2 * normal, "With a spyglass, 2 times the normal radius: " + with);
        helper.assertTrue(smaller == Math.round(normal * 1.5), "The factor of the server: " + smaller);
        helper.succeed();
    }

    /**
     * Past the normal radius, a player with a spyglass explores a chunk that the server keeps loaded with a ticket. A
     * player with no spyglass does not explore it.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void spyglassExploresALoadedChunkPastTheNormalRadius(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ExplorationService service = ExplorationService.get().orElseThrow();
        ServerPlayer player = FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(), "cartofold_spyglass_far"));
        BlockPos site = helper.absolutePos(BlockPos.ZERO.above());
        player.setPos(site.getX() + 0.5, site.getY(), site.getZ() + 0.5);
        ChunkPos center = player.chunkPosition();
        ChunkPos far = new ChunkPos(center.x, center.z - service.normalRadius() - 1);
        level.setChunkForced(far.x, far.z, true);
        helper.succeedWhen(() -> {
            helper.assertTrue(level.getChunkSource().getChunkNow(far.x, far.z) != null, "The forced chunk must load");
            try {
                ExploredMask mask = service.mask(level.dimension(), player.getUUID(), RegionPos.ofChunk(far.x, far.z),
                        level.getGameTime());
                player.getInventory().setItem(SLOT, ItemStack.EMPTY);
                service.explore(player);
                helper.assertFalse(mask.isChunkExplored(far.x, far.z), "With no spyglass, the chunk stays unexplored");
                player.getInventory().setItem(SLOT, new ItemStack(Items.SPYGLASS));
                service.explore(player);
                helper.assertTrue(mask.isChunkExplored(far.x, far.z), "With a spyglass, the player explores the chunk");
            } finally {
                level.setChunkForced(far.x, far.z, false);
            }
        });
    }

    /**
     * Past the normal radius, a player with a spyglass explores a chunk that is not loaded when Cartofold has map data
     * for it. A chunk with no map data stays unexplored. The exploration loads no chunk.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void spyglassExploresTheMapDataButLoadsNoChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ExplorationService service = ExplorationService.get().orElseThrow();
        ServerPlayer player = FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(), "cartofold_spyglass_data"));
        BlockPos site = helper.absolutePos(BlockPos.ZERO.above());
        player.setPos(site.getX() + 0.5, site.getY(), site.getZ() + 0.5);
        ChunkPos center = player.chunkPosition();
        int distance = service.normalRadius() + 2;
        long now = level.getGameTime();
        ChunkPos sampled = null;
        ChunkPos empty = null;
        for (int dx = -distance; dx <= distance && (sampled == null || empty == null); dx++) {
            ChunkPos chunk = new ChunkPos(center.x + dx, center.z + distance);
            MapRegion region = service.region(level.dimension(), RegionPos.ofChunk(chunk.x, chunk.z), now);
            if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null) {
                continue;
            }
            if (sampled == null) {
                sampled = chunk;
                region.put(chunk.x, chunk.z, ChunkColumns.empty(), now);
            } else if (region.sampled(chunk.x, chunk.z) == MapRegion.NEVER) {
                empty = chunk;
            }
        }
        helper.assertTrue(sampled != null && empty != null, "Two chunks that are not loaded");
        ExploredMask sampledMask = service.mask(level.dimension(), player.getUUID(),
                RegionPos.ofChunk(sampled.x, sampled.z), now);
        ExploredMask emptyMask = service.mask(level.dimension(), player.getUUID(),
                RegionPos.ofChunk(empty.x, empty.z), now);

        service.explore(player);
        helper.assertFalse(sampledMask.isChunkExplored(sampled.x, sampled.z), "With no spyglass, no far chunk");
        player.getInventory().setItem(SLOT, new ItemStack(Items.SPYGLASS));
        service.explore(player);

        helper.assertTrue(sampledMask.isChunkExplored(sampled.x, sampled.z), "The chunk with map data is explored");
        helper.assertFalse(emptyMask.isChunkExplored(empty.x, empty.z), "The chunk with no map data is not explored");
        helper.assertTrue(level.getChunkSource().getChunkNow(sampled.x, sampled.z) == null, "No load of the chunk");
        helper.assertTrue(level.getChunkSource().getChunkNow(empty.x, empty.z) == null, "No load of the chunk");
        helper.succeed();
    }

    /**
     * Measures the time of one exploration of a player with a spyglass, for the factors 1, 2 and 4. The service
     * explores each player once every {@link ExplorationService#CHECK_TICKS} ticks. The log gives the times.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void spyglassExplorationCost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ExplorationService service = ExplorationService.get().orElseThrow();
        ServerPlayer player = FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(), "cartofold_spyglass_cost"));
        BlockPos site = helper.absolutePos(BlockPos.ZERO.above());
        player.setPos(site.getX() + 0.5, site.getY(), site.getZ() + 0.5);
        player.getInventory().setItem(SLOT, new ItemStack(Items.SPYGLASS));
        StringBuilder report = new StringBuilder();
        try {
            for (double factor : new double[] {1.0, 2.0, 4.0}) {
                CartofoldServerConfig.SPYGLASS_FACTOR.set(factor);
                long start = System.nanoTime();
                service.explore(player);
                long first = System.nanoTime() - start;
                start = System.nanoTime();
                for (int i = 0; i < COST_RUNS; i++) {
                    service.explore(player);
                }
                long average = (System.nanoTime() - start) / COST_RUNS;
                report.append(String.format(Locale.ROOT, " factor %.0f, radius %d, first %d us, next %d us.",
                        factor, service.discoveryRadius(player), first / 1000, average / 1000));
                helper.assertTrue(average < COST_LIMIT_NANOS, "One exploration must cost less than 10 ms, factor "
                        + factor + ": " + average / 1000 + " us");
            }
        } finally {
            CartofoldServerConfig.SPYGLASS_FACTOR.set(SpyglassMaps.DEFAULT_FACTOR);
        }
        Cartofold.LOGGER.info("Cost of the exploration with a spyglass, normal radius {}:{}", service.normalRadius(),
                report);
        helper.succeed();
    }
}
