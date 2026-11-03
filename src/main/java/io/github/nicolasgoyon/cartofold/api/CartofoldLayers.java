package io.github.nicolasgoyon.cartofold.api;

import io.github.nicolasgoyon.cartofold.server.LayerService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The entry point of the layers on the server. Call these methods on the server thread, after the start of the
 * server.
 *
 * <pre>{@code
 * LayerInfo info = LayerInfo.of(ResourceLocation.fromNamespaceAndPath("mymod", "territories"),
 *         Component.literal("Territories"), 10, true);
 * ChunkArea area = ChunkArea.of("north_village", Level.OVERWORLD, chunks, 0x4000FF00, 0xFF00FF00,
 *         Component.literal("North village"));
 * CartofoldLayers.setForAll(new LayerContent(info, List.of(area), List.of(), List.of()));
 * }</pre>
 */
public final class CartofoldLayers {
    private CartofoldLayers() {
    }

    private static LayerService service() {
        return LayerService.get().orElseThrow(() -> new IllegalStateException("The server is not running"));
    }

    /**
     * Sets a layer for all the players: the players on the server get it now, and the players that join later get it
     * when they join.
     */
    public static void setForAll(LayerContent content) {
        service().setGlobal(content);
    }

    /**
     * Changes some elements of a layer of {@link #setForAll}. The players get only the change. A layer that the server
     * does not have stays out.
     *
     * @param layer  the identifier of the layer
     * @param update the change
     * @throws IllegalArgumentException if the new layer has too many elements
     */
    public static void updateForAll(ResourceLocation layer, LayerUpdate update) {
        service().updateGlobal(layer, update);
    }

    /** Removes a layer of {@link #setForAll}. */
    public static void removeForAll(ResourceLocation layer) {
        service().removeGlobal(layer);
    }

    /**
     * Sets a layer for one player. The player keeps it until it leaves the server, or until {@link #remove}. The
     * mod sends it again when the player joins again.
     */
    public static void set(ServerPlayer player, LayerContent content) {
        service().send(player, content);
    }

    /**
     * Changes some elements of a layer of a player. The player gets only the change. A layer that the player does not
     * have stays out.
     *
     * @param player the player
     * @param layer  the identifier of the layer
     * @param update the change
     * @throws IllegalArgumentException if the new layer has too many elements
     */
    public static void update(ServerPlayer player, ResourceLocation layer, LayerUpdate update) {
        service().update(player, layer, update);
    }

    /** Removes a layer of a player. */
    public static void remove(ServerPlayer player, ResourceLocation layer) {
        service().remove(player, layer);
    }
}
