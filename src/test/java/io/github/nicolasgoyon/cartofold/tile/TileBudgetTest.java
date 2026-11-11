package io.github.nicolasgoyon.cartofold.tile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class TileBudgetTest {
    /** A texture with its memory and the time of its last use. */
    private record Tile(String name, long bytes, long lastUse) {
    }

    private static List<String> over(List<Tile> tiles, long budget, long now) {
        return TileBudget.over(tiles, Tile::bytes, Tile::lastUse, budget, now, 1_000).stream().map(Tile::name)
                .toList();
    }

    @Test
    void underTheBudgetNothingGoes() {
        List<Tile> tiles = List.of(new Tile("a", 40, 0), new Tile("b", 60, 0));

        assertEquals(List.of(), over(tiles, 100, 10_000));
    }

    /** The oldest textures go first, until the memory is in the budget. */
    @Test
    void overTheBudgetTheOldestGoFirst() {
        List<Tile> tiles = List.of(new Tile("new", 50, 9_000), new Tile("old", 50, 1_000),
                new Tile("middle", 50, 5_000));

        assertEquals(List.of("old", "middle"), over(tiles, 60, 10_000));
    }

    /** A texture that a view used in the last second stays, also over the budget. */
    @Test
    void recentTexturesStayOverTheBudget() {
        List<Tile> tiles = List.of(new Tile("a", 50, 9_500), new Tile("b", 50, 9_800), new Tile("old", 50, 1_000));

        assertEquals(List.of("old"), over(tiles, 10, 10_000));
    }
}
