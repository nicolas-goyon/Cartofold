package io.github.nicolasgoyon.cartofold.paper;

import io.github.nicolasgoyon.cartofold.CartofoldServerConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * The effects of a spyglass in the inventory of a player (see {@link #hasSpyglass}).
 *
 * <ul>
 *   <li>The exploration of the maps (see {@link io.github.nicolasgoyon.cartofold.server.ExplorationService}): the
 *       player explores a radius {@link #explorationFactor} times larger. Past the normal radius, the exploration
 *       reads only the chunks that the server has in memory and the map data of Cartofold.</li>
 *   <li>The survey maps (see {@link SurveyMapExploration}): the player updates the map in the hand in a radius
 *       {@link #FACTOR} times larger. The update reads only the exploration data of Cartofold.</li>
 * </ul>
 *
 * <p>A spyglass never loads or generates a chunk.
 */
public final class SpyglassMaps {
    /** The radius of the update of a survey map with a spyglass, as a multiple of the radius of the game. */
    public static final int FACTOR = 3;
    /** The default factor of the exploration radius with a spyglass. */
    public static final double DEFAULT_FACTOR = 2.0;
    /** The smallest factor of the exploration radius with a spyglass: 1 gives the spyglass no effect. */
    public static final double MIN_FACTOR = 1.0;
    /** The largest factor of the exploration radius with a spyglass. */
    public static final double MAX_FACTOR = 4.0;

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

    /**
     * True if a player has a spyglass in the inventory: in the slots of the inventory, in the armor slots or in the
     * hands. The player does not need to use the spyglass.
     */
    public static boolean hasSpyglass(Player player) {
        return player.getInventory().contains(stack -> stack.is(Items.SPYGLASS));
    }

    /**
     * The factor of the exploration radius with a spyglass: the option {@code exploration.spyglassFactor} of the
     * server, or {@link #DEFAULT_FACTOR} before the load of the settings of the server.
     */
    public static double explorationFactor() {
        return CartofoldServerConfig.SPEC.isLoaded() ? CartofoldServerConfig.SPYGLASS_FACTOR.get() : DEFAULT_FACTOR;
    }

    /**
     * The exploration radius with a spyglass.
     *
     * @param radius the radius with no spyglass, in chunks
     * @param factor the factor, from {@link #MIN_FACTOR} to {@link #MAX_FACTOR}: a value out of this range goes to the
     *               nearest limit
     * @return the radius times the factor, rounded to the nearest chunk, in chunks
     */
    public static int explorationRadius(int radius, double factor) {
        double clamped = Double.isNaN(factor) ? MIN_FACTOR : Math.max(MIN_FACTOR, Math.min(MAX_FACTOR, factor));
        return (int) Math.round(radius * clamped);
    }
}
