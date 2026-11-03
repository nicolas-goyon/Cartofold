package io.github.nicolasgoyon.cartofold.paper;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * A spyglass explores the survey maps farther (see {@link SurveyMapExploration}): a player with a spyglass in the
 * inventory updates the map in the hand in a radius {@link #FACTOR} times larger. The update reads only the
 * exploration data of Cartofold: it never loads or generates a chunk.
 */
public final class SpyglassMaps {
    /** The radius of the update of a map with a spyglass, as a multiple of the radius of the game. */
    public static final int FACTOR = 3;

    private SpyglassMaps() {
    }

    /**
     * The radius of the update of a map for a viewer.
     *
     * @param radius the radius of the game, in pixels of a map of scale 0
     * @param viewer the entity that holds the map
     * @return the radius
     */
    public static int radius(int radius, Entity viewer) {
        return viewer instanceof Player player && hasSpyglass(player) ? radius * FACTOR : radius;
    }

    /** True if a player has a spyglass in the inventory. */
    public static boolean hasSpyglass(Player player) {
        return player.getInventory().contains(stack -> stack.is(Items.SPYGLASS));
    }
}
