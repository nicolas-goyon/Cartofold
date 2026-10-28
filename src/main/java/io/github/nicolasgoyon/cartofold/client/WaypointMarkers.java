package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.view.MapProjection;
import io.github.nicolasgoyon.cartofold.view.MinimapLayout;
import io.github.nicolasgoyon.cartofold.waypoint.Waypoint;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Draws the waypoints of the player on the map views. */
public final class WaypointMarkers {
    /** The distance in units of the screen to select a waypoint with the mouse. */
    public static final double SELECT_DISTANCE = 6;
    private static final int MARKER_SIZE = 5;

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
        double limit = projection.width() / 2 - MARKER_SIZE;
        for (Waypoint waypoint : ClientWaypoints.get().in(dimension)) {
            double[] point = projection.toScreen(waypoint.pos().getX() + 0.5, waypoint.pos().getZ() + 0.5);
            double[] inside = MinimapLayout.keepInside(point[0] - projection.screenCenterX(),
                    point[1] - projection.screenCenterY(), limit, round);
            MapShapes.drawMarker(graphics, (float) (projection.screenCenterX() + inside[0]),
                    (float) (projection.screenCenterY() + inside[1]), waypoint.color(), waypoint.death());
        }
    }

    /** Draws the waypoints of a dimension on the map of the world, with their names. */
    public static void renderWorldMap(GuiGraphics graphics, Font font, ResourceKey<Level> dimension,
            MapProjection projection) {
        for (Waypoint waypoint : ClientWaypoints.get().in(dimension)) {
            double[] point = projection.toScreen(waypoint.pos().getX() + 0.5, waypoint.pos().getZ() + 0.5);
            MapShapes.drawMarker(graphics, (float) point[0], (float) point[1], waypoint.color(), waypoint.death());
            graphics.drawCenteredString(font, name(waypoint), (int) Math.round(point[0]),
                    (int) Math.round(point[1]) + MARKER_SIZE + 1, 0xFFFFFFFF);
        }
    }

    /** The waypoint of a dimension at a point of the map of the world, or empty. */
    public static Optional<Waypoint> at(ResourceKey<Level> dimension, MapProjection projection, double screenX,
            double screenY) {
        @Nullable
        Waypoint nearest = null;
        double best = SELECT_DISTANCE;
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
