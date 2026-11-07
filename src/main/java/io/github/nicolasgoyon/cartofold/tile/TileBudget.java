package io.github.nicolasgoyon.cartofold.tile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToLongFunction;

/**
 * The choice of the textures to free, for a budget of memory. The texture with the oldest use goes first. A texture
 * that a view used recently stays, also over the budget: a view that shows it in each frame does not make it again.
 */
public final class TileBudget {
    private TileBudget() {
    }

    /**
     * The textures to free.
     *
     * @param tiles   the textures
     * @param bytes   the memory of a texture, in bytes
     * @param lastUse the time of the last use of a texture, in milliseconds
     * @param budget  the largest memory of all the textures, in bytes
     * @param now     the time now, in milliseconds
     * @param inUse   a texture with a use in this number of milliseconds stays
     * @return the textures to free, the oldest use first
     */
    public static <T> List<T> over(List<T> tiles, ToLongFunction<T> bytes, ToLongFunction<T> lastUse, long budget,
            long now, long inUse) {
        long total = 0;
        for (T tile : tiles) {
            total += bytes.applyAsLong(tile);
        }
        List<T> result = new ArrayList<>();
        if (total <= budget) {
            return result;
        }
        List<T> sorted = new ArrayList<>(tiles);
        sorted.sort(Comparator.comparingLong(lastUse));
        for (T tile : sorted) {
            if (total <= budget || now - lastUse.applyAsLong(tile) < inUse) {
                break;
            }
            result.add(tile);
            total -= bytes.applyAsLong(tile);
        }
        return result;
    }
}
