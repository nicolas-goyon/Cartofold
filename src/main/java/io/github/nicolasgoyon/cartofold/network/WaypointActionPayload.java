package io.github.nicolasgoyon.cartofold.network;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.waypoint.Waypoint;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the player adds, changes or removes a waypoint.
 *
 * @param waypoint the waypoint to add or to change, or the waypoint to remove (only its identifier counts)
 * @param remove   true to remove the waypoint
 */
public record WaypointActionPayload(Waypoint waypoint, boolean remove) implements CustomPacketPayload {
    public static final Type<WaypointActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "waypoint_action"));

    public static final StreamCodec<ByteBuf, WaypointActionPayload> STREAM_CODEC = StreamCodec.composite(
            Waypoint.STREAM_CODEC, WaypointActionPayload::waypoint,
            ByteBufCodecs.BOOL, WaypointActionPayload::remove,
            WaypointActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
