package io.github.nicolasgoyon.cartofold.gametest;

import com.mojang.authlib.GameProfile;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.paper.PaperWaypoints;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapData;
import io.github.nicolasgoyon.cartofold.server.BannerService;
import io.github.nicolasgoyon.cartofold.server.LayerService;
import io.github.nicolasgoyon.cartofold.waypoint.BannerMark;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the banners on the minimap and on the map of the world (see {@link BannerService}). */
@GameTestHolder(Cartofold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BannerGameTests {
    private static final BlockPos BLOCK = new BlockPos(1, 1, 1);

    private BannerGameTests() {
    }

    private static ServerPlayer player(ServerLevel level) {
        return FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "cartofold_banner"));
    }

    private static List<BannerMark> marks(ServerPlayer player) {
        return BannerService.get().orElseThrow().marks(player.getUUID());
    }

    private static LayerContent layer(ServerPlayer player) {
        return LayerService.get().orElseThrow().layer(player.getUUID(), BannerMark.LAYER).orElseThrow();
    }

    private static void useVanillaMap(ServerPlayer player, ItemStack map, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, map);
        map.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos),
                Direction.UP, pos, false)));
    }

    /** A vanilla map on a banner marks it for this player only. A second use removes the mark. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void vanillaMapMarksTheBanner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(BLOCK, Blocks.RED_BANNER);
        BlockPos pos = helper.absolutePos(BLOCK);
        ServerPlayer player = player(level);
        ServerPlayer other = player(level);
        ItemStack map = MapItem.create(level, pos.getX(), pos.getZ(), (byte) 0, false, false);

        useVanillaMap(player, map, pos);

        helper.assertTrue(marks(player).size() == 1, "The player has the mark");
        helper.assertTrue(marks(other).isEmpty(), "The other player has no mark");
        helper.assertTrue(layer(player).markers().size() == 1, "The layer has the marker");
        helper.assertTrue(layer(player).markers().get(0).icon().orElseThrow().getPath().endsWith("red_banner.png"),
                "The marker has the icon of the banner");
        helper.assertTrue(layer(player).markers().get(0).tooltip().isEmpty(), "A banner with no name has no tooltip");

        useVanillaMap(player, map, pos);

        helper.assertTrue(marks(player).isEmpty(), "The second use removes the mark");
        helper.assertTrue(layer(player).markers().isEmpty(), "The layer loses the marker");
        helper.succeed();
    }

    /** A survey map on a banner marks it. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void surveyMapMarksTheBanner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(BLOCK, Blocks.BLUE_BANNER);
        BlockPos pos = helper.absolutePos(BLOCK);
        SurveyMapData data = SurveyMapData.around(level.dimension(), pos.getX(), pos.getZ(), 0);
        ServerPlayer player = player(level);

        helper.assertTrue(PaperWaypoints.use(player, level, pos, data), "The banner is a source");
        helper.assertTrue(marks(player).size() == 1 && marks(player).get(0).color() == DyeColor.BLUE, "The mark");

        helper.assertTrue(PaperWaypoints.use(player, level, pos, data), "The second use");
        helper.assertTrue(marks(player).isEmpty(), "The second use removes the mark");
        helper.succeed();
    }

    /** A mark follows the color of its banner, and goes away with its banner. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void markFollowsItsBanner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(BLOCK, Blocks.RED_BANNER);
        BlockPos pos = helper.absolutePos(BLOCK);
        BannerService service = BannerService.get().orElseThrow();
        ServerPlayer player = player(level);
        service.mark(player, level, pos, true);

        helper.setBlock(BLOCK, Blocks.GREEN_BANNER);
        service.check(player);
        helper.assertTrue(marks(player).get(0).color() == DyeColor.GREEN, "The mark follows the color");

        helper.setBlock(BLOCK, Blocks.AIR);
        service.check(player);
        helper.assertTrue(marks(player).isEmpty(), "A broken banner removes the mark");
        helper.assertTrue(layer(player).markers().isEmpty(), "A broken banner removes the marker");
        helper.succeed();
    }
}
