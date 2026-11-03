package io.github.nicolasgoyon.cartofold.network;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.waypoint.Waypoint;
import io.github.nicolasgoyon.cartofold.waypoint.Waypoints;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: all the waypoints of the player. The server sends them when the player joins, and after each
 * change.
 *
 * @param waypoints the waypoints
 */
public record WaypointsPayload(List<Waypoint> waypoints) implements CustomPacketPayload {
    public static final Type<WaypointsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "waypoints"));

    public static final StreamCodec<ByteBuf, WaypointsPayload> STREAM_CODEC = StreamCodec.composite(
            Waypoint.STREAM_CODEC.apply(ByteBufCodecs.list(Waypoints.MAX_WAYPOINTS)), WaypointsPayload::waypoints,
            WaypointsPayload::new);

    public WaypointsPayload {
        waypoints = List.copyOf(waypoints);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
