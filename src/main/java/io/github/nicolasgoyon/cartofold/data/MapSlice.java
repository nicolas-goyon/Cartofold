package io.github.nicolasgoyon.cartofold.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A slice of the world for the map data: the surface, or a band of {@link #HEIGHT} blocks of height for the caves.
 *
 * @param band the number of the band ({@code Y / 16}), or {@link Integer#MIN_VALUE} for the surface
 */
public record MapSlice(int band) {
    /** The height of a band, in blocks. */
    public static final int HEIGHT = 16;
    /** The surface: the top of each column (or the floor under the ceiling of the Nether). */
    public static final MapSlice SURFACE = new MapSlice(Integer.MIN_VALUE);

    public static final StreamCodec<ByteBuf, MapSlice> STREAM_CODEC =
            ByteBufCodecs.INT.map(MapSlice::new, MapSlice::band);

    /** The band of caves of a height. */
    public static MapSlice caveAt(int y) {
        return new MapSlice(Math.floorDiv(y, HEIGHT));
    }

    /** True for the surface. */
    public boolean surface() {
        return band == Integer.MIN_VALUE;
    }

    /** The lowest Y of the band. */
    public int minY() {
        return band * HEIGHT;
    }

    /** The highest Y of the band. */
    public int maxY() {
        return band * HEIGHT + HEIGHT - 1;
    }

    /** The folder of the slice in the storage: empty for the surface. */
    public String folder() {
        return surface() ? "" : "caves/" + band;
    }
}
