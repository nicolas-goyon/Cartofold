package io.github.nicolasgoyon.cartofold.view;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldMapStateTest {
    private static final double EPSILON = 1e-9;

    @Test
    void dragMovesTheMapWithTheMouse() {
        WorldMapState state = new WorldMapState(100, 200, 2);
        MapProjection before = state.projection(0, 0, 400, 300);
        double[] block = before.toBlock(150, 100);

        state.drag(30, -10);

        assertArrayEquals(new double[] {180, 90}, state.projection(0, 0, 400, 300).toScreen(block[0], block[1]),
                EPSILON);
        assertFalse(state.follows());
    }

    @Test
    void zoomKeepsTheBlockUnderTheMouse() {
        WorldMapState state = new WorldMapState(-50, 70, 1);
        MapProjection before = state.projection(0, 0, 400, 300);
        double[] block = before.toBlock(320, 40);

        state.zoom(before, 320, 40, 1);

        assertEquals(2, state.scale(), EPSILON);
        assertArrayEquals(new double[] {320, 40}, state.projection(0, 0, 400, 300).toScreen(block[0], block[1]),
                EPSILON);
    }

    @Test
    void zoomStaysInTheRange() {
        WorldMapState state = new WorldMapState(0, 0, 1);
        state.zoom(state.projection(0, 0, 100, 100), 50, 50, 20);
        assertEquals(WorldMapState.MAX_SCALE, state.scale(), EPSILON);
        state.zoom(state.projection(0, 0, 100, 100), 50, 50, -20);
        assertEquals(WorldMapState.MIN_SCALE, state.scale(), EPSILON);
        assertTrue(state.follows());
    }

    @Test
    void stateFollowsThePlayerUntilAMove() {
        WorldMapState state = new WorldMapState(0, 0, 1);
        state.track(10, 20);
        assertEquals(10, state.centerX(), EPSILON);
        state.drag(5, 5);
        state.track(30, 40);
        assertEquals(5, state.centerX(), EPSILON);
        state.center(30, 40);
        assertTrue(state.follows());
        assertEquals(40, state.centerZ(), EPSILON);
    }
}
