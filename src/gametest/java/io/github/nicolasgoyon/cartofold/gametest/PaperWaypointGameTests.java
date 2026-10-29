package io.github.nicolasgoyon.cartofold.gametest;

import com.mojang.authlib.GameProfile;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.paper.PaperWaypoints;
import java.util.UUID;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the waypoints of blocks on the paper maps (see {@link PaperWaypoints}). */
@GameTestHolder(Cartofold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PaperWaypointGameTests {
    private static final BlockPos LODESTONE = new BlockPos(1, 1, 1);

    private PaperWaypointGameTests() {
    }

    private static boolean has(MapItemSavedData data, String id) {
        return StreamSupport.stream(data.getDecorations().spliterator(), false)
                .anyMatch(decoration -> decoration.name().map(name -> name.getString().equals(id)).orElse(false));
    }

    /**
     * A map on a lodestone gets a waypoint with the name of the block. A second use removes it. A waypoint of a block
     * that goes away goes away from the map.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void lodestoneMarksTheMap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(LODESTONE, Blocks.LODESTONE);
        BlockPos pos = helper.absolutePos(LODESTONE);
        ItemStack map = MapItem.create(level, pos.getX(), pos.getZ(), (byte) 0, false, false);
        MapItemSavedData data = MapItem.getSavedData(map, level);
        int id = map.get(DataComponents.MAP_ID).id();
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "cartofold_lodestone"));
        String name = Blocks.LODESTONE.getName().getString();

        helper.assertTrue(PaperWaypoints.use(player, level, pos, map), "The lodestone is a source");
        helper.assertTrue(has(data, name), "The map has the waypoint");
        helper.assertTrue(PaperWaypoints.use(player, level, pos, map), "The second use");
        helper.assertFalse(has(data, name), "The second use removes the waypoint");
        helper.assertFalse(PaperWaypoints.use(player, level, pos.above(), map), "A block of no source");

        PaperWaypoints.use(player, level, pos, map);
        helper.setBlock(LODESTONE, Blocks.STONE);
        PaperWaypoints.apply(level, id, data);
        helper.assertFalse(has(data, name), "The waypoint of a removed lodestone goes away");
        helper.succeed();
    }
}
