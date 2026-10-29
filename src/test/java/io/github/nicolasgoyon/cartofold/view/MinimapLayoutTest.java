package io.github.nicolasgoyon.cartofold.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MinimapLayoutTest {
    private static final double EPSILON = 1e-9;

    @Test
    void minimapGoesInItsCorner() {
        assertArrayEquals(new int[] {6, 6}, MinimapLayout.position(MinimapLayout.Corner.TOP_LEFT, 100, 400, 300, 10));
        assertArrayEquals(new int[] {294, 6},
                MinimapLayout.position(MinimapLayout.Corner.TOP_RIGHT, 100, 400, 300, 10));
        assertArrayEquals(new int[] {6, 184},
                MinimapLayout.position(MinimapLayout.Corner.BOTTOM_LEFT, 100, 400, 300, 10));
        assertArrayEquals(new int[] {294, 194},
                MinimapLayout.position(MinimapLayout.Corner.BOTTOM_RIGHT, 100, 400, 300, 0));
    }

    @Test
    void zoomLevelsStayInTheRange() {
        assertEquals(0.25, MinimapLayout.scale(-3), EPSILON);
        assertEquals(1, MinimapLayout.scale(2), EPSILON);
        assertEquals(4, MinimapLayout.scale(99), EPSILON);
    }

    @Test
    void turningMapPutsTheDirectionOfThePlayerAtTheTop() {
        for (float yaw : new float[] {0, 90, -135, 200}) {
            MapProjection projection = new MapProjection(0, 0, 100, 100, 0, 0, 1,
                    MinimapLayout.mapRotation(true, yaw));
            double radians = Math.toRadians(yaw);
            double[] ahead = projection.toScreen(-Math.sin(radians) * 10, Math.cos(radians) * 10);
            assertArrayEquals(new double[] {50, 40}, ahead, 1e-6);
            assertEquals(0, MinimapLayout.arrowRotation(true, yaw), EPSILON);
        }
    }

    @Test
    void arrowPointsInTheDirectionOfThePlayerOnANorthMap() {
        // Yaw 90: the player looks to the west, to the left of the screen.
        double angle = MinimapLayout.arrowRotation(false, 90);
        assertArrayEquals(new double[] {-1, 0}, new double[] {Math.sin(angle), -Math.cos(angle)}, 1e-9);
        assertEquals(0, MinimapLayout.mapRotation(false, 90), EPSILON);
    }

    @Test
    void pointsOutOfTheMinimapGoToItsSide() {
        assertArrayEquals(new double[] {10, -20}, MinimapLayout.keepInside(10, -20, 40, false), EPSILON);
        assertArrayEquals(new double[] {40, 20}, MinimapLayout.keepInside(80, 40, 40, false), EPSILON);
        assertArrayEquals(new double[] {24, -32}, MinimapLayout.keepInside(30, -40, 40, true), EPSILON);
    }
}
