package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.data.ExplorationKey;
import io.github.nicolasgoyon.cartofold.data.MapSlice;
import io.github.nicolasgoyon.cartofold.server.ExplorationService;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** The slices of the views on the client: the surface, or the band of caves of the player. */
public final class MapSlices {
    private MapSlices() {
    }

    /** The slice that a view shows for a player: its band of caves in a cave (with the setting on), or the surface. */
    public static MapSlice of(Player player) {
        return CartofoldClientConfig.CAVES.get() && ExplorationService.underground(player)
                ? MapSlice.caveAt(player.getBlockY()) : MapSlice.SURFACE;
    }

    /** The slice of a player in its dimension. */
    public static ExplorationKey key(Player player) {
        return new ExplorationKey(player.level().dimension(), of(player));
    }

    /** The name of a slice: "Surface" or "Caves: Y a to b". */
    public static Component label(MapSlice slice) {
        return slice.surface() ? Component.translatable("cartofold.slice.surface")
                : Component.translatable("cartofold.slice.caves", slice.minY(), slice.maxY());
    }
}
