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
