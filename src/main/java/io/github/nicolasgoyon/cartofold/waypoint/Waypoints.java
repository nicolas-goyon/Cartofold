package io.github.nicolasgoyon.cartofold.waypoint;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The rules of the list of the waypoints of a player. The lists are immutable: each change gives a new list. */
public final class Waypoints {
    /** The largest number of waypoints of a player. */
    public static final int MAX_WAYPOINTS = 256;
    /** The largest number of characters of a name. */
    public static final int MAX_NAME = 32;
    /** The number of waypoints of the deaths that stay: a new death removes the oldest. */
    public static final int MAX_DEATHS = 3;

    public static final Codec<List<Waypoint>> LIST_CODEC = Waypoint.CODEC.listOf();

    private Waypoints() {
    }

    /**
     * Adds a waypoint, or changes the waypoint with the same identifier.
     *
     * @return the new list, or the same list if the list is full
     */
    public static List<Waypoint> set(List<Waypoint> waypoints, Waypoint waypoint) {
        List<Waypoint> result = new ArrayList<>(waypoints);
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).id().equals(waypoint.id())) {
                result.set(i, waypoint);
                return List.copyOf(result);
            }
        }
        if (result.size() >= MAX_WAYPOINTS) {
            return waypoints;
        }
        result.add(waypoint);
        return List.copyOf(result);
    }

    /** Removes the waypoint with an identifier. */
    public static List<Waypoint> remove(List<Waypoint> waypoints, UUID id) {
        return waypoints.stream().filter(waypoint -> !waypoint.id().equals(id)).toList();
    }

    /**
     * Adds the waypoint of a death. The oldest waypoints of the deaths go out of the list, to keep
     * {@link #MAX_DEATHS} of them. If the list is full, the death replaces the oldest death.
     */
    public static List<Waypoint> addDeath(List<Waypoint> waypoints, Waypoint death) {
        List<Waypoint> result = new ArrayList<>(waypoints);
        long deaths = result.stream().filter(Waypoint::death).count();
        while ((deaths >= MAX_DEATHS || result.size() >= MAX_WAYPOINTS) && deaths > 0) {
            for (int i = 0; i < result.size(); i++) {
                if (result.get(i).death()) {
                    result.remove(i);
                    deaths--;
                    break;
                }
            }
        }
        if (result.size() >= MAX_WAYPOINTS) {
            return waypoints;
        }
        result.add(death);
        return List.copyOf(result);
    }
}
