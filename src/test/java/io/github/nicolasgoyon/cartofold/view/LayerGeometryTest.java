package io.github.nicolasgoyon.cartofold.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.cartofold.api.MapViewKind;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class LayerGeometryTest {
    private static final double[] ALL = {-1e9, -1e9, 1e9, 1e9};

    /** The chunks next to each other on X make one row, also across the X of 0. */
    @Test
    void rowsJoinTheChunksOfALine() {
        LongOpenHashSet chunks = new LongOpenHashSet(new long[] {
            ChunkPos.asLong(-2, 5), ChunkPos.asLong(-1, 5), ChunkPos.asLong(0, 5), ChunkPos.asLong(1, 5),
            ChunkPos.asLong(3, 5), ChunkPos.asLong(0, -4)});

        int[] rows = LayerGeometry.rows(chunks);

        assertEquals(List.of(-4, 0, 0, 5, -2, 1, 5, 3, 3), java.util.Arrays.stream(rows).boxed().toList());
    }

    @Test
    void rowsOfNoChunkAreEmpty() {
        assertEquals(0, LayerGeometry.rows(new LongOpenHashSet()).length);
    }

    /** A square of 3 by 3 chunks has 4 long sides after the join, in place of 12 short sides. */
    @Test
    void joinMakesOneLineForEachSideOfASquare() {
        LongOpenHashSet chunks = new LongOpenHashSet();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                chunks.add(ChunkPos.asLong(x, z));
            }
        }

        List<int[]> sides = LayerGeometry.join(LayerGeometry.border(chunks, LayerGeometry.all()));

        assertEquals(4, sides.size());
        assertTrue(sides.stream().anyMatch(side -> java.util.Arrays.equals(side, new int[] {-16, -16, 32, -16})));
        assertTrue(sides.stream().anyMatch(side -> java.util.Arrays.equals(side, new int[] {32, -16, 32, 32})));
    }

    /** Two sides on the same line with a space between them stay two sides. */
    @Test
    void joinKeepsTheSidesThatDoNotTouch() {
        LongOpenHashSet chunks = new LongOpenHashSet(new long[] {ChunkPos.asLong(0, 0), ChunkPos.asLong(2, 0)});

        List<int[]> sides = LayerGeometry.join(LayerGeometry.border(chunks, LayerGeometry.all()));

        assertEquals(8, sides.size());
    }

    @Test
    void oneChunkHasFourSides() {
        LongOpenHashSet chunks = new LongOpenHashSet(new long[] {ChunkPos.asLong(1, -1)});

        List<int[]> sides = LayerGeometry.border(chunks, ALL);

        assertEquals(4, sides.size());
        assertTrue(sides.stream().anyMatch(side -> side[0] == 16 && side[1] == -16 && side[2] == 32
                && side[3] == -16));
    }

    @Test
    void twoChunksSideBySideHaveSixSides() {
        LongOpenHashSet chunks = new LongOpenHashSet(new long[] {ChunkPos.asLong(0, 0), ChunkPos.asLong(1, 0)});

        List<int[]> sides = LayerGeometry.border(chunks, ALL);

        assertEquals(6, sides.size());
        assertTrue(sides.stream().noneMatch(side -> side[0] == 16 && side[2] == 16));
    }

    @Test
    void chunksOutOfTheBoxHaveNoSide() {
        LongOpenHashSet chunks = new LongOpenHashSet(new long[] {ChunkPos.asLong(100, 100)});

        assertTrue(LayerGeometry.border(chunks, new double[] {0, 0, 64, 64}).isEmpty());
    }

    @Test
    void viewKindsKeepTheirMask() {
        EnumSet<MapViewKind> kinds = EnumSet.of(MapViewKind.MINIMAP, MapViewKind.FRAME);

        assertEquals(kinds, MapViewKind.fromMask(MapViewKind.toMask(kinds)));
        assertEquals(MapViewKind.all(), MapViewKind.fromMask(MapViewKind.toMask(MapViewKind.all())));
    }
}
