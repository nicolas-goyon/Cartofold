package io.github.nicolasgoyon.cartofold.gametest;

import com.mojang.authlib.GameProfile;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.paper.CartofoldItems;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapData;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapId;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapItem;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the survey map items (see {@link SurveyMapItem}). */
@GameTestHolder(Cartofold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SurveyMapGameTests {
    private SurveyMapGameTests() {
    }

    /** The game has the recipe of the empty survey map. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void recipeGivesTheEmptySurveyMap(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "empty_survey_map")).isPresent(),
                "The recipe of the empty survey map must exist");
        helper.succeed();
    }

    /** A use of an empty survey map gives a survey map around the player, with new data. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void emptySurveyMapGivesAMapAroundThePlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "cartofold_maps"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(helper.getBounds().getCenter().subtract(helper.getBounds().minX,
                helper.getBounds().minY, helper.getBounds().minZ)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CartofoldItems.EMPTY_SURVEY_MAP.get()));

        ItemStack map = player.getItemInHand(InteractionHand.MAIN_HAND).use(level, player, InteractionHand.MAIN_HAND)
                .getObject();

        SurveyMapId id = SurveyMapItem.id(map);
        helper.assertTrue(map.is(CartofoldItems.SURVEY_MAP.get()) && id != null, "The use gives a survey map: " + map);
        SurveyMapData data = SurveyMapData.get(level.getServer(), id);
        helper.assertTrue(data != null && data.dimension().equals(level.dimension()), "The map has data");
        int px = data.geometry().pixelX(player.getBlockX());
        int pz = data.geometry().pixelZ(player.getBlockZ());
        helper.assertTrue(px >= 0 && px < 128 && pz >= 0 && pz < 128, "The player is on the map");
        helper.succeed();
    }
}
