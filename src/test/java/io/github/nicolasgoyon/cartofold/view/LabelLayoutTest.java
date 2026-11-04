package io.github.nicolasgoyon.cartofold.view;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LabelLayoutTest {
    @Test
    void boxThatCoversABoxGoesAway() {
        LabelLayout layout = new LabelLayout();

        assertTrue(layout.place(0, 0, 40, 9));
        assertFalse(layout.place(30, 5, 70, 14));
    }

    @Test
    void boxesThatTouchStay() {
        LabelLayout layout = new LabelLayout();

        assertTrue(layout.place(0, 0, 40, 9));
        assertTrue(layout.place(40, 0, 80, 9));
        assertTrue(layout.place(0, 9, 40, 18));
    }

    @Test
    void forcedBoxBlocksTheNextBoxes() {
        LabelLayout layout = new LabelLayout();

        layout.force(0, 0, 40, 9);
        layout.force(10, 0, 50, 9);

        assertFalse(layout.place(45, 0, 90, 9));
    }
}
