package io.github.nicolasgoyon.cartofold.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nicolasgoyon.cartofold.data.ChunkColumns;
import io.github.nicolasgoyon.cartofold.data.ExplorationKey;
import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ExplorationChunksPayloadTest {
    private static ChunkColumns columns(int seed) {
        ChunkColumns columns = ChunkColumns.empty();
        for (int i = 0; i < ChunkColumns.COLUMNS; i++) {
            columns.colors()[i] = (byte) (i + seed);
            columns.heights()[i] = (short) (i * 3 - 64 + seed);
            columns.depths()[i] = (byte) (i % 7);
        }
        return columns;
    }

    @Test
    void chunksStayTheSameAfterTheCompression() {
        List<ExplorationChunksPayload.Chunk> chunks = List.of(
                new ExplorationChunksPayload.Chunk(3, -4, columns(1)),
                new ExplorationChunksPayload.Chunk(-100, 70, columns(9)));

        List<ExplorationChunksPayload.Chunk> read =
                ExplorationChunksPayload.decompress(ExplorationChunksPayload.compress(chunks));

        assertEquals(2, read.size());
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(chunks.get(i).x(), read.get(i).x());
            assertEquals(chunks.get(i).z(), read.get(i).z());
            assertArrayEquals(chunks.get(i).columns().colors(), read.get(i).columns().colors());
            assertArrayEquals(chunks.get(i).columns().heights(), read.get(i).columns().heights());
            assertArrayEquals(chunks.get(i).columns().depths(), read.get(i).columns().depths());
        }
    }

    @Test
    void payloadRefusesTooManyChunks() {
        List<ExplorationChunksPayload.Chunk> chunks = new ArrayList<>(Collections.nCopies(
                ExplorationChunksPayload.MAX_CHUNKS + 1, new ExplorationChunksPayload.Chunk(0, 0, columns(0))));
        assertThrows(IllegalArgumentException.class, () -> new ExplorationChunksPayload(
                ExplorationKey.surface(ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.withDefaultNamespace("overworld"))), chunks));
    }

    @Test
    void decompressionRefusesBadBytes() {
        assertThrows(DecoderException.class, () -> ExplorationChunksPayload.decompress(new byte[] {1, 2, 3}));
    }
}
