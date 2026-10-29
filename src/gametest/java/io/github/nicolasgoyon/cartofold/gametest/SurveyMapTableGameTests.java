package io.github.nicolasgoyon.cartofold.gametest;

import com.mojang.authlib.GameProfile;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.paper.CartofoldItems;
import io.github.nicolasgoyon.cartofold.paper.MapMoves;
import io.github.nicolasgoyon.cartofold.paper.MapUpdates;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapData;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapId;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapItem;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the survey maps in the cartography table (see {@link MapMoves} and {@link MapUpdates}). */
@GameTestHolder(Cartofold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SurveyMapTableGameTests {
    private static final BlockPos TABLE = new BlockPos(1, 1, 1);

    private SurveyMapTableGameTests() {
    }

    /** A table with a survey map, an item and a selected button. */
    private record Table(ServerLevel level, ServerPlayer player, CartographyTableMenu menu, BlockPos pos) {
        static Table of(GameTestHelper helper, ItemStack map, Item input, int button) {
            ServerLevel level = helper.getLevel();
            helper.setBlock(TABLE, Blocks.CARTOGRAPHY_TABLE);
            BlockPos pos = helper.absolutePos(TABLE);
            ServerPlayer player = FakePlayerFactory.get(level,
                    new GameProfile(UUID.randomUUID(), "cartofold_table"));
            CartographyTableMenu menu = new CartographyTableMenu(0, player.getInventory(),
                    ContainerLevelAccess.create(level, pos));
            menu.getSlot(0).set(map.copyWithCount(2));
            menu.getSlot(1).set(new ItemStack(input, 2));
            if (button != MapMoves.NONE) {
                menu.clickMenuButton(player, button);
            }
            return new Table(level, player, menu, pos);
        }

        /** Takes the result: the action of the table happens. */
        ItemStack take() {
            ItemStack result = menu.getSlot(2).getItem().copy();
            if (!result.isEmpty()) {
                menu.getSlot(2).onTake(player, result);
            }
            menu.removed(player);
            player.getInventory().clearContent();
            return result;
        }
    }

    private static ItemStack map(GameTestHelper helper, int scale) {
        BlockPos pos = helper.absolutePos(TABLE);
        return SurveyMapItem.create(helper.getLevel().getServer(),
                SurveyMapData.around(helper.getLevel().dimension(), pos.getX(), pos.getZ(), scale));
    }

    private static SurveyMapData data(GameTestHelper helper, ItemStack stack) {
        SurveyMapId id = SurveyMapItem.id(stack);
        helper.assertTrue(id != null, "The result is a survey map");
        SurveyMapData data = SurveyMapData.get(helper.getLevel().getServer(), id);
        helper.assertTrue(data != null, "The result has data");
        return data;
    }

    /** A paper gives a new map with the next scale. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void paperZoomsTheMapOut(GameTestHelper helper) {
        ItemStack map = map(helper, 1);
        ItemStack result = Table.of(helper, map, Items.PAPER, MapMoves.NONE).take();

        SurveyMapData zoomed = data(helper, result);
        helper.assertTrue(!SurveyMapItem.id(result).equals(SurveyMapItem.id(map)), "The zoom gives a new map");
        helper.assertTrue(zoomed.scale() == 2, "The next scale: " + zoomed.scale());
        helper.assertTrue(!result.has(CartofoldItems.SURVEY_MAP_ACTION.get()), "The result has no action");
        helper.succeed();
    }

    /** A glass pane gives a locked copy, and a locked map does not zoom. An empty survey map gives two maps. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void glassLocksAndEmptyMapCopies(GameTestHelper helper) {
        ItemStack map = map(helper, 0);
        ItemStack locked = Table.of(helper, map, Items.GLASS_PANE, MapMoves.NONE).take();
        helper.assertTrue(data(helper, locked).locked(), "The glass pane locks a copy");
        helper.assertFalse(data(helper, map).locked(), "The old map is not locked");

        ItemStack zoom = Table.of(helper, locked, Items.PAPER, MapMoves.NONE).take();
        helper.assertTrue(zoom.isEmpty(), "A locked map does not zoom");

        ItemStack copies = Table.of(helper, map, CartofoldItems.EMPTY_SURVEY_MAP.get(), MapMoves.NONE).take();
        helper.assertTrue(copies.getCount() == 2 && SurveyMapItem.id(copies).equals(SurveyMapItem.id(map)),
                "The copy gives two maps with the same data");
        helper.succeed();
    }

    /** The east button with a paper gives a map half a side to the east, with the explored pixels moved. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void eastButtonMovesTheMap(GameTestHelper helper) {
        ItemStack map = map(helper, 2);
        SurveyMapData data = data(helper, map);
        data.setColor(100, 10, (byte) 7);

        SurveyMapData moved = data(helper, Table.of(helper, map, Items.PAPER, MapMoves.EAST).take());

        int step = MapMoves.STEP << data.scale();
        helper.assertTrue(moved.centerX() == data.centerX() + step && moved.centerZ() == data.centerZ(),
                "The center moves half a map to the east: " + moved.centerX() + " " + moved.centerZ());
        helper.assertTrue(moved.scale() == data.scale(), "The scale stays");
        helper.assertTrue(moved.colors()[36 + 10 * MapMoves.SIZE] == 7, "The explored pixel keeps its place");
        helper.succeed();
    }

    /** The update button gives the same map, and the update paints the pixel of the table from the loaded chunk. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void updateButtonPaintsTheMap(GameTestHelper helper) {
        ItemStack map = map(helper, 0);
        SurveyMapData data = data(helper, map);
        BlockPos table = helper.absolutePos(TABLE);
        int px = data.geometry().pixelX(table.getX());
        int pz = data.geometry().pixelZ(table.getZ());

        ItemStack result = Table.of(helper, map, Items.PAPER, MapMoves.UPDATE).take();

        SurveyMapId id = SurveyMapItem.id(result);
        helper.assertTrue(id.equals(SurveyMapItem.id(map)), "The update keeps the map");
        helper.assertTrue(MapUpdates.running(id), "The update must start");
        helper.assertTrue(MapUpdates.chunks(data).size() == 64, "A map of scale 0 has 64 chunks");
        helper.succeedWhen(() -> {
            helper.assertFalse(MapUpdates.running(id), "The update must end");
            helper.assertTrue(data.colors()[px + pz * MapMoves.SIZE] != 0, "The pixel of the table has a color");
        });
    }
}
