package io.github.nicolasgoyon.cartofold.server;

import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.api.LayerUpdate;
import io.github.nicolasgoyon.cartofold.api.MapMarker;
import io.github.nicolasgoyon.cartofold.api.MarkerClickEvent;
import io.github.nicolasgoyon.cartofold.network.LayerPayload;
import io.github.nicolasgoyon.cartofold.network.LayerRemovePayload;
import io.github.nicolasgoyon.cartofold.network.LayerUpdatePayload;
import io.github.nicolasgoyon.cartofold.network.MarkerClickPayload;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The layers of the other mods, on the server (see {@link io.github.nicolasgoyon.cartofold.api.CartofoldLayers}).
 *
 * <ul>
 *   <li>A layer for all the players goes to the players on the server, and to each player that joins later.</li>
 *   <li>A layer for one player goes to this player only. A layer for one player replaces a layer for all the
 *       players with the same identifier, for this player.</li>
 * </ul>
 *
 * <p>The service keeps the layers that each player has, to accept only the clicks on markers that the player has.
 */
public final class LayerService {
    /** The largest number of clicks on markers of a player in one second. */
    public static final int CLICKS_PER_SECOND = 10;

    private final ActionLimiter clicks = new ActionLimiter(CLICKS_PER_SECOND, 20);
    @Nullable
    private static LayerService instance;

    private final MinecraftServer server;
    private final Map<ResourceLocation, LayerContent> global = new LinkedHashMap<>();
    private final Map<UUID, Map<ResourceLocation, LayerContent>> players = new HashMap<>();

    LayerService(MinecraftServer server) {
        this.server = server;
    }

    /** The service of the running server, or empty with no server. */
    public static Optional<LayerService> get() {
        return Optional.ofNullable(instance);
    }

    public static void onServerStarted(ServerStartedEvent event) {
        instance = new LayerService(event.getServer());
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        instance = null;
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (instance != null && event.getEntity() instanceof ServerPlayer player) {
            instance.global.values().forEach(content -> instance.send(player, content));
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (instance != null) {
            instance.players.remove(event.getEntity().getUUID());
            instance.clicks.remove(event.getEntity().getUUID());
        }
    }

    /** Receives a click on a marker from a client. */
    public static void onMarkerClick(MarkerClickPayload payload, IPayloadContext context) {
        if (instance != null && context.player() instanceof ServerPlayer player
                && instance.clicks.allow(player.getUUID(), player.server.overworld().getGameTime())) {
            instance.click(player, payload.layer(), payload.marker());
        }
    }

    /** Sets a layer for all the players. */
    public void setGlobal(LayerContent content) {
        LayerContent old = global.put(content.info().id(), content);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (hasGlobal(player, content.info().id(), old)) {
                send(player, content);
            }
        }
    }

    /**
     * True if a player shows the layer for all the players, or no layer with this identifier. A layer of one player
     * replaces the layer for all the players: the changes of the layer for all the players do not reach it.
     */
    private boolean hasGlobal(ServerPlayer player, ResourceLocation layer, @Nullable LayerContent global) {
        Map<ResourceLocation, LayerContent> layers = players.get(player.getUUID());
        LayerContent current = layers == null ? null : layers.get(layer);
        return current == null || current == global;
    }

    /** Changes some elements of a layer for all the players that have this layer. */
    public void updateGlobal(ResourceLocation layer, LayerUpdate update) {
        LayerContent content = global.get(layer);
        if (content == null) {
            return;
        }
        LayerContent next = content.apply(update);
        global.put(layer, next);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Map<ResourceLocation, LayerContent> layers = players.get(player.getUUID());
            // A layer of one player replaces the layer for all the players: it does not get the change.
            if (layers != null && layers.get(layer) == content) {
                layers.put(layer, next);
                distribute(player, new LayerUpdatePayload(layer, update));
            }
        }
    }

    /** Removes a layer for all the players. */
    public void removeGlobal(ResourceLocation layer) {
        LayerContent old = global.remove(layer);
        if (old != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (hasGlobal(player, layer, old)) {
                    remove(player, layer);
                }
            }
        }
    }

    /** Sets a layer for one player. */
    public void send(ServerPlayer player, LayerContent content) {
        players.computeIfAbsent(player.getUUID(), key -> new HashMap<>()).put(content.info().id(), content);
        distribute(player, new LayerPayload(content));
    }

    /** Changes some elements of a layer for one player. */
    public void update(ServerPlayer player, ResourceLocation layer, LayerUpdate update) {
        Map<ResourceLocation, LayerContent> layers = players.get(player.getUUID());
        LayerContent content = layers == null ? null : layers.get(layer);
        if (content != null) {
            layers.put(layer, content.apply(update));
            distribute(player, new LayerUpdatePayload(layer, update));
        }
    }

    /** Removes a layer for one player. */
    public void remove(ServerPlayer player, ResourceLocation layer) {
        Map<ResourceLocation, LayerContent> layers = players.get(player.getUUID());
        if (layers != null && layers.remove(layer) != null) {
            distribute(player, new LayerRemovePayload(layer));
        }
    }

    /** The content of a layer that a player has, or empty. */
    public Optional<LayerContent> layer(UUID player, ResourceLocation layer) {
        return Optional.ofNullable(players.getOrDefault(player, Map.of()).get(layer));
    }

    /**
     * Posts a {@link MarkerClickEvent} if the player has a clickable marker with this identifier.
     *
     * @return true if the event was posted
     */
    public boolean click(ServerPlayer player, ResourceLocation layer, String markerId) {
        Optional<MapMarker> marker = layer(player.getUUID(), layer).flatMap(content -> content.marker(markerId))
                .filter(MapMarker::clickable);
        marker.ifPresent(found -> NeoForge.EVENT_BUS.post(new MarkerClickEvent(player, layer, found)));
        return marker.isPresent();
    }

    private static void distribute(ServerPlayer player, CustomPacketPayload payload) {
        if (!(player instanceof FakePlayer) && player.connection.hasChannel(payload.type())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
