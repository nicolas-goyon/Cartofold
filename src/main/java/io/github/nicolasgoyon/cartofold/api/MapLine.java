package io.github.nicolasgoyon.cartofold.api;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A line of a layer through points, for example a route. The views use the X and the Z of the points.
 *
 * @param id        the identifier of the line in its layer
 * @param dimension the dimension of the points
 * @param points    the points, {@link #MAX_POINTS} at most
 * @param color     the color, in ARGB
 * @param width     the width, in units of the screen
 */
public record MapLine(String id, ResourceKey<Level> dimension, List<BlockPos> points, int color, float width) {
    /** The largest number of points of a line. */
    public static final int MAX_POINTS = 4096;

    public static final StreamCodec<RegistryFriendlyByteBuf, MapLine> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, MapLine::id,
            ResourceKey.streamCodec(Registries.DIMENSION), MapLine::dimension,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_POINTS)), MapLine::points,
            ByteBufCodecs.INT, MapLine::color,
            ByteBufCodecs.FLOAT, MapLine::width,
            MapLine::new);

    public MapLine {
        if (points.size() > MAX_POINTS) {
            throw new IllegalArgumentException("A line has " + MAX_POINTS + " points at most");
        }
        points = List.copyOf(points);
    }
}
