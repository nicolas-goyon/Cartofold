package io.github.nicolasgoyon.cartofold.network;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapData;
import io.github.nicolasgoyon.cartofold.paper.SurveyMarker;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Server to client: all the data of a survey map. The pixels are compressed.
 */
public final class SurveyMapPayload implements CustomPacketPayload {
    /** The largest number of markers of a map in a payload. */
    public static final int MAX_MARKERS = 1024;
    private static final int PIXELS = 128 * 128;

    public static final Type<SurveyMapPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "survey_map"));

    private static final StreamCodec<ByteBuf, List<SurveyMarker>> MARKERS_CODEC =
            SurveyMarker.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_MARKERS));

    public static final StreamCodec<FriendlyByteBuf, SurveyMapPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.id);
                buf.writeVarInt(payload.version);
                ResourceKey.streamCodec(Registries.DIMENSION).encode(buf, payload.dimension);
                buf.writeInt(payload.centerX);
                buf.writeInt(payload.centerZ);
                buf.writeByte(payload.scale);
                buf.writeBoolean(payload.locked);
                buf.writeByteArray(compress(payload.colors));
                MARKERS_CODEC.encode(buf, payload.markers);
            },
            buf -> new SurveyMapPayload(buf.readVarInt(), buf.readVarInt(),
                    ResourceKey.streamCodec(Registries.DIMENSION).decode(buf), buf.readInt(), buf.readInt(),
                    buf.readByte(), buf.readBoolean(), decompress(buf.readByteArray()), MARKERS_CODEC.decode(buf)));

    private final int id;
    private final int version;
    private final ResourceKey<Level> dimension;
    private final int centerX;
    private final int centerZ;
    private final int scale;
    private final boolean locked;
    private final byte[] colors;
    private final List<SurveyMarker> markers;

    /**
     * Makes a payload.
     *
     * @param id        the id of the map
     * @param version   the version of the map on the server
     * @param dimension the dimension
     * @param centerX   the X of the center
     * @param centerZ   the Z of the center
     * @param scale     the scale
     * @param locked    true for a locked map
     * @param colors    the pixels, at the index {@code x + z * 128}
     * @param markers   the markers
     */
    public SurveyMapPayload(int id, int version, ResourceKey<Level> dimension, int centerX, int centerZ, int scale,
            boolean locked, byte[] colors, List<SurveyMarker> markers) {
        if (colors.length != PIXELS) {
            throw new IllegalArgumentException("A map has " + PIXELS + " pixels");
        }
        this.id = id;
        this.version = version;
        this.dimension = dimension;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.scale = scale;
        this.locked = locked;
        this.colors = colors;
        this.markers = List.copyOf(markers.subList(0, Math.min(markers.size(), MAX_MARKERS)));
    }

    public int id() {
        return id;
    }

    public int version() {
        return version;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    public int scale() {
        return scale;
    }

    public boolean locked() {
        return locked;
    }

    /** The pixels, at the index {@code x + z * 128}. Do not change the array. */
    public byte[] colors() {
        return colors;
    }

    public List<SurveyMarker> markers() {
        return markers;
    }

    /** The payload of the data of a map. */
    public static SurveyMapPayload of(int id, SurveyMapData data) {
        return new SurveyMapPayload(id, data.version(), data.dimension(), data.centerX(), data.centerZ(),
                data.scale(), data.locked(), data.colors().clone(), data.markers());
    }

    static byte[] compress(byte[] colors) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DeflaterOutputStream out = new DeflaterOutputStream(bytes)) {
            out.write(colors);
        } catch (IOException e) {
            throw new IllegalStateException("A memory stream does not fail", e);
        }
        return bytes.toByteArray();
    }

    static byte[] decompress(byte[] bytes) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(bytes);
            byte[] colors = new byte[PIXELS];
            int read = 0;
            while (read < PIXELS && !inflater.finished()) {
                int count = inflater.inflate(colors, read, PIXELS - read);
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                read += count;
            }
            if (read != PIXELS) {
                throw new DecoderException("The pixels of a survey map are not complete");
            }
            return colors;
        } catch (DataFormatException e) {
            throw new DecoderException("The pixels of a survey map are not valid", e);
        } finally {
            inflater.end();
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
