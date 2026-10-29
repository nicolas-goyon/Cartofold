package io.github.nicolasgoyon.cartofold.api;

import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * The description of a layer.
 *
 * @param id           the identifier of the layer, in the namespace of its mod
 * @param name         the name of the layer, in the settings of the player
 * @param order        the order of the layer: a layer with a higher order draws above the layers with a lower order.
 *                     The waypoints of the player are above all the layers.
 * @param views        the kinds of views that show the layer
 * @param onlyExplored true to show the parts of the layer only on the chunks that the player explored
 */
public record LayerInfo(ResourceLocation id, Component name, int order, Set<MapViewKind> views,
        boolean onlyExplored) {
    public static final StreamCodec<RegistryFriendlyByteBuf, LayerInfo> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, LayerInfo::id,
            ComponentSerialization.STREAM_CODEC, LayerInfo::name,
            ByteBufCodecs.VAR_INT, LayerInfo::order,
            ByteBufCodecs.VAR_INT.map(MapViewKind::fromMask, MapViewKind::toMask), LayerInfo::views,
            ByteBufCodecs.BOOL, LayerInfo::onlyExplored,
            LayerInfo::new);

    public LayerInfo {
        views = Set.copyOf(views);
    }

    /** A layer on all the kinds of views. */
    public static LayerInfo of(ResourceLocation id, Component name, int order, boolean onlyExplored) {
        return new LayerInfo(id, name, order, MapViewKind.all(), onlyExplored);
    }
}
