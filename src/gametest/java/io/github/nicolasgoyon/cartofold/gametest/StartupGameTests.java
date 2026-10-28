package io.github.nicolasgoyon.cartofold.gametest;

import io.github.nicolasgoyon.cartofold.Cartofold;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the start of the mod. */
@GameTestHolder(Cartofold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StartupGameTests {
    private StartupGameTests() {
    }

    /** The server loads the mod. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void modIsLoaded(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded(Cartofold.MOD_ID), "The mod must be loaded");
        helper.succeed();
    }
}
