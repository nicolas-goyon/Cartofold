package io.github.nicolasgoyon.cartofold.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/**
 * A player clicks a clickable marker of a layer on the map of the world or on a map frame. Cartofold posts this
 * event on {@link net.neoforged.neoforge.common.NeoForge#EVENT_BUS}, on the server thread. The server sent the marker
 * to this player: the event never comes for a marker that the player does not have.
 */
public final class MarkerClickEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation layer;
    private final MapMarker marker;

    public MarkerClickEvent(ServerPlayer player, ResourceLocation layer, MapMarker marker) {
        this.player = player;
        this.layer = layer;
        this.marker = marker;
    }

    /** The player that clicks the marker. */
    public ServerPlayer getPlayer() {
        return player;
    }

    /** The identifier of the layer of the marker. */
    public ResourceLocation getLayer() {
        return layer;
    }

    /** The marker. */
    public MapMarker getMarker() {
        return marker;
    }
}
