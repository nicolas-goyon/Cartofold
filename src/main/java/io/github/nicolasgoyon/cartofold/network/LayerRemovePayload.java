package io.github.nicolasgoyon.cartofold.network;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the client removes a layer.
 *
 * @param layer the identifier of the layer
 */
public record LayerRemovePayload(ResourceLocation layer) implements CustomPacketPayload {
    public static final Type<LayerRemovePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "layer_remove"));

    public static final StreamCodec<ByteBuf, LayerRemovePayload> STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.map(LayerRemovePayload::new, LayerRemovePayload::layer);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
