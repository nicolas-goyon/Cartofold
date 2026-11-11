package io.github.nicolasgoyon.cartofold.gametest;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.api.PaperMapMarkers;
import io.github.nicolasgoyon.cartofold.api.PaperMarkerSource;
import io.github.nicolasgoyon.cartofold.paper.CartofoldMapDecorations;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapData;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapItem;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the use of a survey map on the blocks of a source. */
@GameTestHolder(Cartofold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SurveyMapUseGameTests {
    private static final BlockPos BLOCK = new BlockPos(1, 1, 1);

    private SurveyMapUseGameTests() {
    }

    /** A survey map marks a block that opens a screen: the block does not get the use. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void surveyMapMarksABlockWithAUseAction(GameTestHelper helper) {
        PaperMapMarkers.register(PaperMarkerSource.of(ResourceLocation.fromNamespaceAndPath("cartofold_test", "table"),
                state -> state.is(Blocks.CRAFTING_TABLE), CartofoldMapDecorations.WAYPOINT,
                (level, pos) -> Optional.of(Component.literal("Table"))));
        ServerLevel level = helper.getLevel();
        helper.setBlock(BLOCK, Blocks.CRAFTING_TABLE);
        BlockPos pos = helper.absolutePos(BLOCK);
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        ItemStack map = SurveyMapItem.create(level.getServer(), SurveyMapData.around(level.dimension(), pos.getX(),
                pos.getZ(), 0));
        player.setItemInHand(InteractionHand.MAIN_HAND, map);
        helper.assertTrue(PaperMapMarkers.isSurveyMap(map), "A survey map has the tag");

        player.gameMode.useItemOn(player, level, map, InteractionHand.MAIN_HAND, new BlockHitResult(
                Vec3.atCenterOf(pos), Direction.UP, pos, false));

        SurveyMapData data = SurveyMapItem.data(level.getServer(), map);
        helper.assertTrue(data != null && data.markers().size() == 1, "The map has the marker of the table");
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "The table opens no screen");
        helper.succeed();
    }
}
