package io.github.nicolasgoyon.cartofold.network;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.api.LayerUpdate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: a change of some elements of a layer.
 *
 * @param layer  the identifier of the layer
 * @param update the change
 */
public record LayerUpdatePayload(ResourceLocation layer, LayerUpdate update) implements CustomPacketPayload {
    public static final Type<LayerUpdatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "layer_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LayerUpdatePayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, LayerUpdatePayload::layer,
            LayerUpdate.STREAM_CODEC, LayerUpdatePayload::update,
            LayerUpdatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
