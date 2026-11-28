package io.github.nicolasgoyon.cartofold.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SpyglassMapsTest {
    @Test
    void radiusIsTheNormalRadiusTimesTheFactor() {
        assertEquals(24, SpyglassMaps.explorationRadius(12, SpyglassMaps.DEFAULT_FACTOR));
        assertEquals(20, SpyglassMaps.explorationRadius(10, 2.0));
        assertEquals(15, SpyglassMaps.explorationRadius(10, 1.5));
        assertEquals(48, SpyglassMaps.explorationRadius(12, SpyglassMaps.MAX_FACTOR));
    }

    @Test
    void factorOneGivesTheNormalRadius() {
        assertEquals(12, SpyglassMaps.explorationRadius(12, SpyglassMaps.MIN_FACTOR));
    }

    /** The radius goes to the nearest chunk: 7 x 1.5 = 10.5 gives 11. */
    @Test
    void radiusGoesToTheNearestChunk() {
        assertEquals(11, SpyglassMaps.explorationRadius(7, 1.5));
        assertEquals(9, SpyglassMaps.explorationRadius(7, 1.3));
    }

    /** A factor out of the range goes to the nearest limit. */
    @Test
    void factorOutOfTheRangeGoesToTheLimit() {
        assertEquals(12, SpyglassMaps.explorationRadius(12, 0.5));
        assertEquals(48, SpyglassMaps.explorationRadius(12, 10.0));
        assertEquals(12, SpyglassMaps.explorationRadius(12, Double.NaN));
    }
}
