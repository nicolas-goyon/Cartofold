package io.github.nicolasgoyon.cartofold.view;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

/**
 * The rules of the markers in the world (the HUD): the screen point of a point of the world, the distance limits and
 * the order of the draws. The HUD and its tests use these rules.
 */
public final class HudLayout {
    /** The smallest depth of a point in front of the camera. A point with a smaller depth does not show. */
    private static final float NEAR = 0.05f;

    private HudLayout() {
    }

    /**
     * A point of the world with a value, for example a waypoint.
     *
     * @param value    the value of the point
     * @param x        the X of the point
     * @param y        the Y of the point
     * @param z        the Z of the point
     * @param distance the distance from the point to the camera, in blocks
     */
    public record Point<T>(T value, double x, double y, double z, double distance) {
        /** A point with its distance to the camera. */
        public static <T> Point<T> of(T value, double x, double y, double z, double cameraX, double cameraY,
                double cameraZ) {
            double dx = x - cameraX;
            double dy = y - cameraY;
            double dz = z - cameraZ;
            return new Point<>(value, x, y, z, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
    }

    /**
     * True if the HUD shows a point at a distance.
     *
     * @param minDistance the points nearer than this distance do not show
     * @param maxDistance the points farther than this distance do not show, or 0 for no limit
     */
    public static boolean shown(double distance, int minDistance, int maxDistance) {
        return distance >= minDistance && (maxDistance <= 0 || distance <= maxDistance);
    }

    /**
     * The points in the distance limits, from the farthest to the nearest. The HUD draws them in this order: a near
     * point is above a far point.
     */
    public static <T> List<Point<T>> select(List<Point<T>> points, int minDistance, int maxDistance) {
        List<Point<T>> shown = new ArrayList<>();
        for (Point<T> point : points) {
            if (shown(point.distance(), minDistance, maxDistance)) {
                shown.add(point);
            }
        }
        shown.sort(Comparator.comparingDouble((Point<T> point) -> point.distance()).reversed());
        return shown;
    }

    /**
     * The point of the screen of a point of the world.
     *
     * @param viewProjection the projection matrix multiplied by the view matrix of the camera. The view matrix turns
     *                       the world around the camera, with the camera at the origin.
     * @param width          the width of the screen
     * @param height         the height of the screen
     * @return the X and the Y on the screen, with Y to the bottom, or null for a point behind the camera
     */
    @Nullable
    public static double[] toScreen(Matrix4fc viewProjection, double cameraX, double cameraY, double cameraZ,
            double x, double y, double z, double width, double height) {
        // The subtraction in double keeps the precision far from the origin of the world.
        Vector4f clip = new Vector4f((float) (x - cameraX), (float) (y - cameraY), (float) (z - cameraZ), 1);
        viewProjection.transform(clip);
        if (clip.w < NEAR) {
            return null;
        }
        double ndcX = clip.x / clip.w;
        double ndcY = clip.y / clip.w;
        return new double[] {(ndcX + 1) / 2 * width, (1 - ndcY) / 2 * height};
    }

    /** True if a point of the screen is in the screen, with a margin around the screen. */
    public static boolean onScreen(double[] point, double width, double height, double margin) {
        return point[0] >= -margin && point[0] <= width + margin && point[1] >= -margin
                && point[1] <= height + margin;
    }

    /** The distance that the HUD shows: a whole number of blocks. */
    public static long distanceText(double distance) {
        return Math.round(distance);
    }
}
