package io.github.nicolasgoyon.cartofold.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.cartofold.api.MapViewKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class HudLayoutTest {
    private static final double EPSILON = 1e-4;
    private static final int WIDTH = 400;
    private static final int HEIGHT = 200;

    /** A camera that looks to the north (-Z), as the camera of the game with a yaw of 180. */
    private static Matrix4f camera() {
        return new Matrix4f().perspective((float) Math.toRadians(70), (float) WIDTH / HEIGHT, 0.05f, 1000);
    }

    @Test
    void pointInFrontOfTheCameraIsAtTheCenter() {
        double[] point = HudLayout.toScreen(camera(), 1000.5, 64, -2000.5, 1000.5, 64, -2100.5, WIDTH, HEIGHT);
        assertNotNull(point);
        assertArrayEquals(new double[] {200, 100}, point, EPSILON);
    }

    @Test
    void pointBehindTheCameraDoesNotShow() {
        assertNull(HudLayout.toScreen(camera(), 0, 0, 0, 0, 0, 10, WIDTH, HEIGHT));
        assertNull(HudLayout.toScreen(camera(), 0, 0, 0, 0, 0, 0, WIDTH, HEIGHT));
    }

    @Test
    void pointsGoToTheirSideOfTheScreen() {
        double[] right = HudLayout.toScreen(camera(), 0, 0, 0, 5, 0, -10, WIDTH, HEIGHT);
        double[] above = HudLayout.toScreen(camera(), 0, 0, 0, 0, 5, -10, WIDTH, HEIGHT);
        assertNotNull(right);
        assertNotNull(above);
        assertTrue(right[0] > 200);
        assertEquals(100, right[1], EPSILON);
        assertTrue(above[1] < 100);
        assertEquals(200, above[0], EPSILON);
    }

    @Test
    void turnedCameraLooksToItsDirection() {
        // A turn of 90 degrees around Y: the camera looks to the east (+X).
        Matrix4f matrix = camera().mul(new Matrix4f().rotationY((float) Math.toRadians(90)));
        double[] east = HudLayout.toScreen(matrix, 0, 0, 0, 10, 0, 0, WIDTH, HEIGHT);
        assertNotNull(east);
        assertArrayEquals(new double[] {200, 100}, east, EPSILON);
        assertNull(HudLayout.toScreen(matrix, 0, 0, 0, -10, 0, 0, WIDTH, HEIGHT));
    }

    @Test
    void distanceLimitsHideNearAndFarPoints() {
        assertFalse(HudLayout.shown(49.9, 50, 1000));
        assertTrue(HudLayout.shown(50, 50, 1000));
        assertTrue(HudLayout.shown(1000, 50, 1000));
        assertFalse(HudLayout.shown(1000.1, 50, 1000));
        assertTrue(HudLayout.shown(1_000_000, 0, 0));
    }

    @Test
    void selectKeepsThePointsInTheLimitsFromFarToNear() {
        List<HudLayout.Point<String>> points = List.of(
                HudLayout.Point.of("near", 10, 0, 0, 0, 0, 0),
                HudLayout.Point.of("middle", 0, 0, 300, 0, 0, 0),
                HudLayout.Point.of("far", 0, 800, 0, 0, 0, 0),
                HudLayout.Point.of("too far", 2000, 0, 0, 0, 0, 0),
                HudLayout.Point.of("next", 0, 0, -100, 0, 0, 0));
        List<String> values = HudLayout.select(points, 50, 1000).stream().map(HudLayout.Point::value).toList();
        assertEquals(List.of("far", "middle", "next"), values);
    }

    @Test
    void distanceOfAPointIsInThreeDimensions() {
        assertEquals(5, HudLayout.Point.of("a", 3, 4, 0, 0, 0, 0).distance(), EPSILON);
        assertEquals(13, HudLayout.Point.of("a", 1, 2, 3, 4, 6, 15).distance(), EPSILON);
        assertEquals(13, HudLayout.distanceText(12.6));
    }

    @Test
    void onScreenAcceptsTheMargin() {
        assertTrue(HudLayout.onScreen(new double[] {-5, 100}, WIDTH, HEIGHT, 10));
        assertFalse(HudLayout.onScreen(new double[] {-15, 100}, WIDTH, HEIGHT, 10));
        assertFalse(HudLayout.onScreen(new double[] {200, 215}, WIDTH, HEIGHT, 10));
    }

    @Test
    void hudKindHasItsOwnBit() {
        assertEquals(16, MapViewKind.toMask(Set.of(MapViewKind.HUD)));
        assertEquals(Set.of(MapViewKind.HUD, MapViewKind.PAPER), MapViewKind.fromMask(16 | 4));
        assertTrue(MapViewKind.all().contains(MapViewKind.HUD));
    }

    @Test
    void selectOfOneHundredPointsIsFast() {
        List<HudLayout.Point<Integer>> points = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            points.add(HudLayout.Point.of(i, i * 20, 70, -i * 7, 0, 64, 0));
        }
        Matrix4f matrix = camera();
        long start = System.nanoTime();
        for (int frame = 0; frame < 1000; frame++) {
            for (HudLayout.Point<Integer> point : HudLayout.select(points, 50, 1000)) {
                HudLayout.toScreen(matrix, 0, 64, 0, point.x(), point.y(), point.z(), WIDTH, HEIGHT);
            }
        }
        // 1000 frames of 100 points: much less than 1 ms for each frame.
        assertTrue(System.nanoTime() - start < 2_000_000_000L);
    }
}
