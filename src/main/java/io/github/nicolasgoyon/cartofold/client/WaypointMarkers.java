package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.view.MapProjection;
import io.github.nicolasgoyon.cartofold.view.MinimapLayout;
import io.github.nicolasgoyon.cartofold.waypoint.Waypoint;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Draws the waypoints of the player on the map views. */
public final class WaypointMarkers {
    /** The distance in units of the screen to select a waypoint with the mouse. */

    private WaypointMarkers() {
    }

    /** The name that the views show for a waypoint. */
    public static Component name(Waypoint waypoint) {
        if (waypoint.name().isEmpty()) {
            return Component.translatable(waypoint.death() ? "cartofold.waypoint.death" : "cartofold.waypoint");
        }
        return Component.literal(waypoint.name());
    }

    /**
     * Draws the waypoints of a dimension on a minimap. A waypoint out of the minimap stays on its side.
     *
     * @param round true for a round minimap
     */
    public static void renderMinimap(GuiGraphics graphics, ResourceKey<Level> dimension, MapProjection projection,
            boolean round) {
        double limit = projection.width() / 2 - CartofoldClientConfig.markerSizes().nameOffset();
        for (Waypoint waypoint : ClientWaypoints.get().in(dimension)) {
            double[] point = projection.toScreen(waypoint.pos().getX() + 0.5, waypoint.pos().getZ() + 0.5);
            double[] inside = MinimapLayout.keepInside(point[0] - projection.screenCenterX(),
                    point[1] - projection.screenCenterY(), limit, round);
            MapShapes.drawMarker(graphics, (float) (projection.screenCenterX() + inside[0]),
                    (float) (projection.screenCenterY() + inside[1]), waypoint.color(), waypoint.death());
        }
    }

    /**
     * Draws the waypoints of a dimension on the map of the world.
     *
     * @param labels the names of the markers: the waypoints add their names to it, or null to show no name
     * @param mouseX the X of the mouse: the waypoint under the mouse always shows its name
     * @param mouseY the Y of the mouse
     */
    public static void renderWorldMap(GuiGraphics graphics, ResourceKey<Level> dimension, MapProjection projection,
            @Nullable MapLabels labels, double mouseX, double mouseY) {
        for (Waypoint waypoint : ClientWaypoints.get().in(dimension)) {
            double[] point = projection.toScreen(waypoint.pos().getX() + 0.5, waypoint.pos().getZ() + 0.5);
            MapShapes.drawMarker(graphics, (float) point[0], (float) point[1], waypoint.color(), waypoint.death());
            if (labels != null && projection.contains(point[0], point[1])) {
                labels.add(name(waypoint), point[0], point[1] + CartofoldClientConfig.markerSizes().nameOffset(),
                        Math.hypot(point[0] - mouseX, point[1] - mouseY) <= CartofoldClientConfig.markerSizes().hit());
            }
        }
    }

    /** The waypoint of a dimension at a point of the map of the world, or empty. */
    public static Optional<Waypoint> at(ResourceKey<Level> dimension, MapProjection projection, double screenX,
            double screenY) {
        @Nullable
        Waypoint nearest = null;
        double best = CartofoldClientConfig.markerSizes().hit();
        for (Waypoint waypoint : ClientWaypoints.get().in(dimension)) {
            double[] point = projection.toScreen(waypoint.pos().getX() + 0.5, waypoint.pos().getZ() + 0.5);
            double distance = Math.hypot(point[0] - screenX, point[1] - screenY);
            if (distance <= best) {
                best = distance;
                nearest = waypoint;
            }
        }
        return Optional.ofNullable(nearest);
    }
}
