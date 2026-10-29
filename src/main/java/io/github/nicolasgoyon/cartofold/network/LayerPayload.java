package io.github.nicolasgoyon.cartofold.network;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.api.LayerContent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the new content of a layer.
 *
 * @param content the content of the layer
 */
public record LayerPayload(LayerContent content) implements CustomPacketPayload {
    public static final Type<LayerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "layer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LayerPayload> STREAM_CODEC =
            LayerContent.STREAM_CODEC.map(LayerPayload::new, LayerPayload::content);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
