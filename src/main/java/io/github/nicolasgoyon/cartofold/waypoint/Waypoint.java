package io.github.nicolasgoyon.cartofold.waypoint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * A waypoint of a player: a named and colored position in a dimension.
 *
 * @param id        the identifier of the waypoint
 * @param name      the name, {@link Waypoints#MAX_NAME} characters at most. The waypoint of a death has no name: the
 *                  views show a translated name.
 * @param color     the color, in RGB
 * @param dimension the dimension of the position
 * @param pos       the position
 * @param death     true for the waypoint of a death of the player
 */
public record Waypoint(UUID id, String name, int color, ResourceKey<Level> dimension, BlockPos pos, boolean death) {
    public static final Codec<Waypoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Waypoint::id),
            Codec.STRING.fieldOf("name").forGetter(Waypoint::name),
            Codec.INT.fieldOf("color").forGetter(Waypoint::color),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Waypoint::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(Waypoint::pos),
            Codec.BOOL.optionalFieldOf("death", false).forGetter(Waypoint::death))
            .apply(instance, Waypoint::new));

    public static final StreamCodec<ByteBuf, Waypoint> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, Waypoint::id,
            ByteBufCodecs.stringUtf8(Waypoints.MAX_NAME), Waypoint::name,
            ByteBufCodecs.INT, Waypoint::color,
            ResourceKey.streamCodec(Registries.DIMENSION), Waypoint::dimension,
            BlockPos.STREAM_CODEC, Waypoint::pos,
            ByteBufCodecs.BOOL, Waypoint::death,
            Waypoint::new);

    public Waypoint {
        name = name.strip();
        if (name.length() > Waypoints.MAX_NAME) {
            name = name.substring(0, Waypoints.MAX_NAME);
        }
        color &= 0xFFFFFF;
    }
}
