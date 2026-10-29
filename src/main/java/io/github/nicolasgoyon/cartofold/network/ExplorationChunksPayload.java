package io.github.nicolasgoyon.cartofold.network;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.data.ChunkColumns;
import io.github.nicolasgoyon.cartofold.data.ExplorationKey;
import io.netty.handler.codec.DecoderException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the map data of explored chunks of a slice of a dimension. The chunks are compressed together.
 *
 * @param key    the dimension and the slice of the chunks
 * @param chunks the chunks, {@link #MAX_CHUNKS} at most
 */
public record ExplorationChunksPayload(ExplorationKey key, List<Chunk> chunks)
        implements CustomPacketPayload {
    /** The largest number of chunks in one payload: 256 KiB of data before the compression. */
    public static final int MAX_CHUNKS = 256;

    public static final Type<ExplorationChunksPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "exploration_chunks"));

    /**
     * The map data of a chunk.
     *
     * @param x       the X of the chunk
     * @param z       the Z of the chunk
     * @param columns the columns of the chunk
     */
    public record Chunk(int x, int z, ChunkColumns columns) {
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ExplorationChunksPayload> STREAM_CODEC =
            StreamCodec.of(ExplorationChunksPayload::write, ExplorationChunksPayload::read);

    public ExplorationChunksPayload {
        if (chunks.size() > MAX_CHUNKS) {
            throw new IllegalArgumentException("Too many chunks: " + chunks.size());
        }
        chunks = List.copyOf(chunks);
    }

    private static void write(RegistryFriendlyByteBuf buf, ExplorationChunksPayload payload) {
        ExplorationKey.STREAM_CODEC.encode(buf, payload.key());
        buf.writeByteArray(compress(payload.chunks()));
    }

    private static ExplorationChunksPayload read(RegistryFriendlyByteBuf buf) {
        ExplorationKey key = ExplorationKey.STREAM_CODEC.decode(buf);
        return new ExplorationChunksPayload(key, decompress(buf.readByteArray()));
    }

    /** The chunks as compressed bytes. */
    static byte[] compress(List<Chunk> chunks) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) {
            out.writeShort(chunks.size());
            for (Chunk chunk : chunks) {
                out.writeInt(chunk.x());
                out.writeInt(chunk.z());
                out.write(chunk.columns().colors());
                for (short height : chunk.columns().heights()) {
                    out.writeShort(height);
                }
                out.write(chunk.columns().depths());
            }
        } catch (IOException e) {
            throw new IllegalStateException("A memory stream does not fail", e);
        }
        return bytes.toByteArray();
    }

    /** The chunks of compressed bytes. */
    static List<Chunk> decompress(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes)))) {
            int count = in.readUnsignedShort();
            if (count > MAX_CHUNKS) {
                throw new DecoderException("Too many chunks: " + count);
            }
            List<Chunk> chunks = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int z = in.readInt();
                ChunkColumns columns = ChunkColumns.empty();
                in.readFully(columns.colors());
                for (int c = 0; c < ChunkColumns.COLUMNS; c++) {
                    columns.heights()[c] = in.readShort();
                }
                in.readFully(columns.depths());
                chunks.add(new Chunk(x, z, columns));
            }
            return chunks;
        } catch (IOException e) {
            throw new DecoderException("The exploration chunks are not valid", e);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
