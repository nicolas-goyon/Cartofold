package io.github.nicolasgoyon.cartofold.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.network.WaypointActionPayload;
import io.github.nicolasgoyon.cartofold.network.WaypointsPayload;
import io.github.nicolasgoyon.cartofold.waypoint.Waypoint;
import io.github.nicolasgoyon.cartofold.waypoint.Waypoints;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The waypoints of the players, on the server. The waypoints of a player are in memory while the player is on the
 * server, and in the file {@code data/cartofold/waypoints/<uuid>.json} of the world. Each change writes the file on an
 * IO thread of the game and sends the list to the client.
 */
public final class WaypointService {
    @Nullable
    private static WaypointService instance;

    private final Path root;
    private final Map<UUID, List<Waypoint>> players = new HashMap<>();
    private CompletableFuture<Void> writes = CompletableFuture.completedFuture(null);

    WaypointService(Path root) {
        this.root = root;
    }

    /** The service of the running server, or empty with no server. */
    public static Optional<WaypointService> get() {
        return Optional.ofNullable(instance);
    }

    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        instance = new WaypointService(server.getWorldPath(LevelResource.ROOT).resolve("data")
                .resolve(Cartofold.MOD_ID).resolve("waypoints"));
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        if (instance != null) {
            instance.writes.join();
            instance = null;
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (instance != null && event.getEntity() instanceof ServerPlayer player) {
            instance.send(player);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (instance != null) {
            instance.players.remove(event.getEntity().getUUID());
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        if (instance != null && event.getEntity() instanceof ServerPlayer player) {
            instance.update(player, Waypoints.addDeath(instance.waypoints(player.getUUID()), new Waypoint(
                    UUID.randomUUID(), "", 0xFFFFFF, player.level().dimension(), player.blockPosition(), true)));
        }
    }

    /** Receives a change of a waypoint from a client. */
    public static void onAction(WaypointActionPayload payload, IPayloadContext context) {
        if (instance != null && context.player() instanceof ServerPlayer player) {
            instance.apply(player, payload);
        }
    }

    /** Applies a change of a waypoint of a player. */
    public void apply(ServerPlayer player, WaypointActionPayload payload) {
        List<Waypoint> waypoints = waypoints(player.getUUID());
        update(player, payload.remove() ? Waypoints.remove(waypoints, payload.waypoint().id())
                : Waypoints.set(waypoints, payload.waypoint()));
    }

    /** The waypoints of a player, from the memory or from the disk. */
    public List<Waypoint> waypoints(UUID player) {
        return players.computeIfAbsent(player, this::read);
    }

    private Path file(UUID player) {
        return root.resolve(player + ".json");
    }

    private List<Waypoint> read(UUID player) {
        Path file = file(player);
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return Waypoints.LIST_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IOException::new);
        } catch (IOException | RuntimeException e) {
            Cartofold.LOGGER.error("The waypoints {} are not valid: the player has no waypoint", file, e);
            return List.of();
        }
    }

    private void update(ServerPlayer player, List<Waypoint> waypoints) {
        players.put(player.getUUID(), waypoints);
        Path file = file(player.getUUID());
        byte[] bytes = Waypoints.LIST_CODEC.encodeStart(JsonOps.INSTANCE, waypoints).getOrThrow().toString()
                .getBytes(StandardCharsets.UTF_8);
        writes = writes.thenRunAsync(() -> {
            try {
                ExplorationStorage.write(file, bytes);
            } catch (IOException e) {
                Cartofold.LOGGER.error("The waypoints {} cannot be written", file, e);
            }
        }, Util.ioPool());
        send(player);
    }

    private void send(ServerPlayer player) {
        if (!(player instanceof FakePlayer) && player.connection.hasChannel(WaypointsPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new WaypointsPayload(waypoints(player.getUUID())));
        }
    }
}
