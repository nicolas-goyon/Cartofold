package io.github.nicolasgoyon.cartofold.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MarkerSizesTest {
    @Test
    void defaultSizesAreHalfOfTheOldSizes() {
        MarkerSizes sizes = new MarkerSizes(1);

        // The old icon had 10 units, the old name had the size of the font, and the old arrow had a scale of 1.
        assertEquals(5, sizes.icon());
        assertEquals(0.5, sizes.name());
        assertEquals(1 / 3.0, sizes.arrow());
        assertEquals(0.5, sizes.diamond());
    }

    @Test
    void scaleMultipliesAllTheSizes() {
        MarkerSizes sizes = new MarkerSizes(2);

        assertEquals(10, sizes.icon());
        assertEquals(1, sizes.name());
        assertEquals(6.5, sizes.hit());
    }

    @Test
    void scaleStaysInItsLimits() {
        assertEquals(MarkerSizes.MAX_SCALE, new MarkerSizes(10).scale());
        assertEquals(MarkerSizes.MIN_SCALE, new MarkerSizes(0).scale());
    }

    @Test
    void smallMarkersKeepAClickArea() {
        assertTrue(new MarkerSizes(MarkerSizes.MIN_SCALE).hit() >= 4);
    }
}
