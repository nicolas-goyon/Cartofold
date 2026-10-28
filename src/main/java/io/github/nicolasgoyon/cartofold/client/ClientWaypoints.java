package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.waypoint.Waypoint;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The waypoints of the player that the client received from the server. This class uses no class of the client: the
 * payload handler on the common side can call it.
 */
public final class ClientWaypoints {
    private static final ClientWaypoints INSTANCE = new ClientWaypoints();

    private volatile List<Waypoint> waypoints = List.of();

    private ClientWaypoints() {
    }

    /** The waypoints of the client. */
    public static ClientWaypoints get() {
        return INSTANCE;
    }

    /** Replaces all the waypoints. */
    public void set(List<Waypoint> waypoints) {
        this.waypoints = List.copyOf(waypoints);
    }

    /** All the waypoints. */
    public List<Waypoint> all() {
        return waypoints;
    }

    /** The waypoints of a dimension. */
    public List<Waypoint> in(ResourceKey<Level> dimension) {
        return waypoints.stream().filter(waypoint -> waypoint.dimension().equals(dimension)).toList();
    }
}
