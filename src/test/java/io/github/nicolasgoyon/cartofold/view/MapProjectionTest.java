package io.github.nicolasgoyon.cartofold.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nicolasgoyon.cartofold.data.RegionPos;
import java.util.List;
import org.junit.jupiter.api.Test;

class MapProjectionTest {
    private static final double EPSILON = 1e-9;

    @Test
    void centerOfTheRectangleShowsTheCenterBlock() {
        MapProjection projection = MapProjection.north(10, 20, 100, 60, 1000.5, -300.5, 2);

        assertArrayEquals(new double[] {60, 50}, projection.toScreen(1000.5, -300.5), EPSILON);
        assertArrayEquals(new double[] {64, 48}, projection.toScreen(1002.5, -301.5), EPSILON);
    }

    @Test
    void blockOfAPointReversesTheProjection() {
        MapProjection projection = new MapProjection(0, 0, 200, 100, 30_000_000.25, -12.75, 0.375, 1.1);
        double[] screen = projection.toScreen(29_999_990.5, 4.25);

        assertArrayEquals(new double[] {29_999_990.5, 4.25}, projection.toBlock(screen[0], screen[1]), 1e-6);
    }

    @Test
    void positiveRotationTurnsTheMapClockwise() {
        MapProjection projection = new MapProjection(0, 0, 100, 100, 0, 0, 1, Math.PI / 2);

        // With a quarter turn clockwise, the north goes to the right of the screen.
        assertArrayEquals(new double[] {60, 50}, projection.toScreen(0, -10), EPSILON);
    }

    @Test
    void regionsCoverTheRectangle() {
        MapProjection projection = MapProjection.north(0, 0, 100, 100, 0, 0, 0.25);

        // The rectangle shows the blocks from -200 to 200.
        assertEquals(List.of(new RegionPos(-1, -1), new RegionPos(0, -1), new RegionPos(-1, 0), new RegionPos(0, 0)),
                projection.regions());
        assertArrayEquals(new double[] {-200, -200, 200, 200}, projection.blockBounds(), EPSILON);
    }

    @Test
    void levelOfDetailFollowsTheBlocksForEachPixel() {
        assertEquals(0, MapProjection.north(0, 0, 10, 10, 0, 0, 1).lod(2));
        assertEquals(0, MapProjection.north(0, 0, 10, 10, 0, 0, 0.5).lod(2));
        assertEquals(1, MapProjection.north(0, 0, 10, 10, 0, 0, 0.25).lod(2));
        assertEquals(3, MapProjection.north(0, 0, 10, 10, 0, 0, 0.001).lod(1));
    }

    @Test
    void projectionRefusesAnEmptyRectangle() {
        assertThrows(IllegalArgumentException.class, () -> MapProjection.north(0, 0, 0, 10, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> MapProjection.north(0, 0, 10, 10, 0, 0, 0));
    }
}
