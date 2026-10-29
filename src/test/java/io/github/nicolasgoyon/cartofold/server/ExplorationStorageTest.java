package io.github.nicolasgoyon.cartofold.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.cartofold.data.ChunkColumns;
import io.github.nicolasgoyon.cartofold.data.ExplorationKey;
import io.github.nicolasgoyon.cartofold.data.ExploredMask;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.MapSlice;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExplorationStorageTest {
    private static final RegionPos POS = new RegionPos(0, -1);
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    /** A region and a mask go to their files in the folder of their dimension, and come back. */
    @Test
    void filesKeepTheData(@TempDir Path root) throws IOException {
        ExplorationStorage storage = new ExplorationStorage(root);
        MapRegion region = new MapRegion(POS);
        ChunkColumns columns = ChunkColumns.empty();
        columns.colors()[0] = 7;
        region.put(0, -32, columns, 5L);
        ExploredMask mask = new ExploredMask(POS);
        mask.markChunk(0, -32);

        ExplorationStorage.write(storage.regionFile(ExplorationKey.surface(Level.OVERWORLD), POS), ExplorationStorage.bytes(region));
        ExplorationStorage.write(storage.maskFile(ExplorationKey.surface(Level.OVERWORLD), PLAYER, POS), ExplorationStorage.bytes(mask));

        assertTrue(Files.isRegularFile(root.resolve("minecraft/overworld/regions/r.0.-1.dat")));
        assertTrue(Files.isRegularFile(root.resolve("minecraft/overworld/players/" + PLAYER + "/r.0.-1.dat")));
        assertEquals(7, storage.readRegion(ExplorationKey.surface(Level.OVERWORLD), POS).orElseThrow().color(0, -512));
        assertEquals(256, storage.readMask(ExplorationKey.surface(Level.OVERWORLD), PLAYER, POS).orElseThrow().count());
        assertTrue(storage.readRegion(ExplorationKey.surface(Level.NETHER), POS).isEmpty(), "No file: no region");
    }

    @Test
    void caveSlicesHaveFoldersOfTheirOwn(@TempDir Path root) throws IOException {
        ExplorationStorage storage = new ExplorationStorage(root);
        ExplorationKey cave = new ExplorationKey(Level.OVERWORLD, MapSlice.caveAt(-20));
        MapRegion region = new MapRegion(POS);
        ChunkColumns columns = ChunkColumns.empty();
        columns.colors()[0] = 9;
        region.put(0, -32, columns, 1);

        ExplorationStorage.write(storage.regionFile(cave, POS), ExplorationStorage.bytes(region));

        assertTrue(storage.regionFile(cave, POS).toString().contains("caves" + java.io.File.separator + "-2"));
        assertEquals(9, storage.readRegion(cave, POS).orElseThrow().color(0, -512));
        assertTrue(storage.readRegion(ExplorationKey.surface(Level.OVERWORLD), POS).isEmpty(),
                "The surface and the caves have their own files");
    }
}
