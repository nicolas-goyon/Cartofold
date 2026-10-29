package io.github.nicolasgoyon.cartofold.gametest;

import com.mojang.authlib.GameProfile;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.paper.PaperWaypoints;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapData;
import io.github.nicolasgoyon.cartofold.paper.SurveyMarker;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the waypoints of blocks on the survey maps (see {@link PaperWaypoints}). */
@GameTestHolder(Cartofold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PaperWaypointGameTests {
    private static final BlockPos BLOCK = new BlockPos(1, 1, 1);

    private PaperWaypointGameTests() {
    }

    private static ServerPlayer player(ServerLevel level) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "cartofold_marker"));
    }

    /**
     * A survey map on a lodestone gets a marker with the icon of Cartofold. A second use removes it. A marker of a block
     * that goes away goes away from the map.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lodestoneMarksTheMap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(BLOCK, Blocks.LODESTONE);
        BlockPos pos = helper.absolutePos(BLOCK);
        SurveyMapData data = SurveyMapData.around(level.dimension(), pos.getX(), pos.getZ(), 0);
        ServerPlayer player = player(level);

        helper.assertTrue(PaperWaypoints.use(player, level, pos, data), "The lodestone is a source");
        helper.assertTrue(data.markers().size() == 1, "The map has the marker");
        SurveyMarker marker = data.markers().get(0);
        helper.assertTrue(marker.type().equals(ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "waypoint"))
                && marker.name().equals(Blocks.LODESTONE.getName().getString()), "The icon and the name: " + marker);
        helper.assertTrue(PaperWaypoints.use(player, level, pos, data), "The second use");
        helper.assertTrue(data.markers().isEmpty(), "The second use removes the marker");
        helper.assertFalse(PaperWaypoints.use(player, level, pos.above(), data), "A block of no source");

        PaperWaypoints.use(player, level, pos, data);
        helper.setBlock(BLOCK, Blocks.STONE);
        PaperWaypoints.check(level, data);
        helper.assertTrue(data.markers().isEmpty(), "The marker of a removed lodestone goes away");
        helper.succeed();
    }

    /** A survey map on a banner gets the icon of the color of the banner. A locked map does not change. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bannerMarksTheMap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(BLOCK, Blocks.RED_BANNER);
        BlockPos pos = helper.absolutePos(BLOCK);
        SurveyMapData data = SurveyMapData.around(level.dimension(), pos.getX(), pos.getZ(), 0);
        SurveyMapData locked = data.lockedCopy();

        helper.assertTrue(PaperWaypoints.use(player(level), level, pos, data), "The banner is a source");
        helper.assertTrue(data.markers().size() == 1 && data.markers().get(0).type()
                .equals(ResourceLocation.withDefaultNamespace("banner_red")), "The icon of a red banner");
        helper.assertFalse(PaperWaypoints.use(player(level), level, pos, locked), "A locked map does not change");
        helper.succeed();
    }
}
