package io.github.nicolasgoyon.cartofold.network;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the player clicks a marker of a layer.
 *
 * @param layer  the identifier of the layer
 * @param marker the identifier of the marker in the layer
 */
public record MarkerClickPayload(ResourceLocation layer, String marker) implements CustomPacketPayload {
    public static final Type<MarkerClickPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "marker_click"));

    public static final StreamCodec<ByteBuf, MarkerClickPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, MarkerClickPayload::layer,
            ByteBufCodecs.stringUtf8(256), MarkerClickPayload::marker,
            MarkerClickPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
