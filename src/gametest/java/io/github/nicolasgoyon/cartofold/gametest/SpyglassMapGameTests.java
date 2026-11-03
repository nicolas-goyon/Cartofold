package io.github.nicolasgoyon.cartofold.gametest;

import com.mojang.authlib.GameProfile;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.paper.SpyglassMaps;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the exploration of the paper maps with a spyglass (see {@link SpyglassMaps}). */
@GameTestHolder(Cartofold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpyglassMapGameTests {
    private static final int SLOT = 20;

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
}
