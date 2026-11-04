package io.github.nicolasgoyon.cartofold.view;

import java.util.ArrayList;
import java.util.List;

/** Keeps the boxes of the names that a map drew, so that a new name does not cover a name of the map. */
public final class LabelLayout {
    private final List<double[]> boxes = new ArrayList<>();

    /**
     * Adds a box if it covers no box of the layout.
     *
     * @param minX the left side of the box, in units of the screen
     * @param minY the top side of the box, in units of the screen
     * @param maxX the right side of the box, in units of the screen
     * @param maxY the bottom side of the box, in units of the screen
     * @return true if the layout has the new box
     */
    public boolean place(double minX, double minY, double maxX, double maxY) {
        for (double[] box : boxes) {
            if (minX < box[2] && maxX > box[0] && minY < box[3] && maxY > box[1]) {
                return false;
            }
        }
        force(minX, minY, maxX, maxY);
        return true;
    }

    /** Adds a box, also when it covers a box of the layout. */
    public void force(double minX, double minY, double maxX, double maxY) {
        boxes.add(new double[] {minX, minY, maxX, maxY});
    }
}
