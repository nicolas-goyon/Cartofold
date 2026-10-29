package io.github.nicolasgoyon.cartofold.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.cartofold.data.ChunkColumns;
import io.github.nicolasgoyon.cartofold.data.ExplorationKey;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.network.ExplorationChunksPayload;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ClientExplorationTest {
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"));

    @Test
    void storeKeepsTheChunksOfEachDimension() {
        ClientExploration store = new ClientExploration();
        ChunkColumns columns = ChunkColumns.empty();
        columns.colors()[0] = 7;
        RegionPos pos = RegionPos.ofChunk(-1, 2);

        store.accept(new ExplorationChunksPayload(ExplorationKey.surface(OVERWORLD), List.of(new ExplorationChunksPayload.Chunk(-1, 2,
                columns))));

        MapRegion region = store.region(OVERWORLD, pos).orElseThrow();
        assertEquals(7, region.color(-16, 32));
        assertNotEquals(MapRegion.NEVER, region.sampled(-1, 2));
        assertEquals(MapRegion.NEVER, region.sampled(0, 2));
        assertTrue(store.region(NETHER, pos).isEmpty());
    }

    @Test
    void eachChangeIncreasesTheVersions() {
        ClientExploration store = new ClientExploration();
        RegionPos pos = RegionPos.ofChunk(0, 0);
        assertEquals(0, store.version(OVERWORLD, pos));

        store.accept(new ExplorationChunksPayload(ExplorationKey.surface(OVERWORLD), List.of(new ExplorationChunksPayload.Chunk(0, 0,
                ChunkColumns.empty()))));
        long first = store.version(OVERWORLD, pos);
        store.accept(new ExplorationChunksPayload(ExplorationKey.surface(OVERWORLD), List.of(new ExplorationChunksPayload.Chunk(1, 0,
                ChunkColumns.empty()))));

        assertTrue(store.version(OVERWORLD, pos) > first);
        assertEquals(store.version(), store.version(OVERWORLD, pos));
        store.clear();
        assertTrue(store.region(OVERWORLD, pos).isEmpty());
    }

    @Test
    void storeFreesTheOldRegionsOutOfTheRequest() {
        ClientExploration store = new ClientExploration();
        for (int i = 0; i < ClientExploration.MAX_REGIONS + 10; i++) {
            store.accept(new ExplorationChunksPayload(ExplorationKey.surface(OVERWORLD), List.of(new ExplorationChunksPayload.Chunk(i * 32, 0,
                    ChunkColumns.empty()))));
        }
        RegionPos oldest = RegionPos.ofChunk(0, 0);
        RegionPos second = RegionPos.ofChunk(32, 0);

        store.retain(Map.of(ExplorationKey.surface(OVERWORLD), List.of(oldest)));

        assertEquals(ClientExploration.MAX_REGIONS, store.size());
        assertTrue(store.region(OVERWORLD, oldest).isPresent(), "A region of the request stays");
        assertTrue(store.region(OVERWORLD, second).isEmpty(), "The oldest region out of the request goes out");
    }
}
