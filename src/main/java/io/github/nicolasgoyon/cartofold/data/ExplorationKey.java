package io.github.nicolasgoyon.cartofold.data;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The map data of a slice of a dimension: the server and the client keep the regions and the masks of each key.
 *
 * @param dimension the dimension
 * @param slice     the slice: the surface or a band of caves
 */
public record ExplorationKey(ResourceKey<Level> dimension, MapSlice slice) {
    public static final StreamCodec<ByteBuf, ExplorationKey> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), ExplorationKey::dimension,
            MapSlice.STREAM_CODEC, ExplorationKey::slice,
            ExplorationKey::new);

    /** The surface of a dimension. */
    public static ExplorationKey surface(ResourceKey<Level> dimension) {
        return new ExplorationKey(dimension, MapSlice.SURFACE);
    }
}
