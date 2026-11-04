package io.github.nicolasgoyon.cartofold.api.client;

import io.github.nicolasgoyon.cartofold.client.MapLabels;
import io.github.nicolasgoyon.cartofold.client.WorldMapScreen;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Opens the map of the world of Cartofold at a position that a mod selects.
 *
 * <pre>{@code
 * CartofoldWorldMap.at(Level.OVERWORLD, villageX, villageZ)
 *         .scale(2)
 *         .parent(this)
 *         .select(layer, "north_village")
 *         .open();
 * }</pre>
 */
public final class CartofoldWorldMap {
    private final ResourceKey<Level> dimension;
    private final double blockX;
    private final double blockZ;
    @Nullable
    private Double scale;
    @Nullable
    private Screen parent;
    @Nullable
    private MapLabels.Key selected;

    private CartofoldWorldMap(ResourceKey<Level> dimension, double blockX, double blockZ) {
        this.dimension = dimension;
        this.blockX = blockX;
        this.blockZ = blockZ;
    }

    /**
     * A map of the world with a block of a dimension at the center. With no call of {@link #open}, nothing shows.
     *
     * @param dimension the dimension of the map
     * @param blockX    the X of the block at the center
     * @param blockZ    the Z of the block at the center
     */
    public static CartofoldWorldMap at(ResourceKey<Level> dimension, double blockX, double blockZ) {
        return new CartofoldWorldMap(dimension, blockX, blockZ);
    }

    /**
     * Sets the scale: the number of units of the screen for one block. By default, the map keeps the scale of the last
     * map.
     */
    public CartofoldWorldMap scale(double value) {
        this.scale = value;
        return this;
    }

    /** Sets the screen to show when the player closes the map. By default, the game shows no screen. */
    public CartofoldWorldMap parent(@Nullable Screen screen) {
        this.parent = screen;
        return this;
    }

    /**
     * Shows a marker as selected: the marker has a mark, and its name always shows.
     *
     * @param layer  the identifier of the layer of the marker
     * @param marker the identifier of the marker in its layer
     */
    public CartofoldWorldMap select(ResourceLocation layer, String marker) {
        this.selected = new MapLabels.Key(layer, marker);
        return this;
    }

    /** Shows the map. With no player, this method does nothing. */
    public void open() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player != null) {
            minecraft.setScreen(new WorldMapScreen(player, dimension, blockX, blockZ, scale, parent, selected));
        }
    }
}
