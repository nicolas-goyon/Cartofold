package io.github.nicolasgoyon.cartofold.api;

import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * A marker of a layer at a position, for example the center of a village.
 *
 * @param id        the identifier of the marker in its layer
 * @param dimension the dimension of the position
 * @param pos       the position
 * @param color     the color of the marker with no icon, in RGB
 * @param icon      the texture of the icon (a square image), or empty for a colored diamond
 * @param name      the name, under the marker on the map of the world and on the map frames
 * @param tooltip   the text when the mouse is on the marker, or empty
 * @param clickable true if a click on the marker sends a {@link MarkerClickEvent} on the server
 */
public record MapMarker(String id, ResourceKey<Level> dimension, BlockPos pos, int color,
        Optional<ResourceLocation> icon, Component name, Optional<Component> tooltip, boolean clickable) {
    private static final StreamCodec<ByteBuf, Optional<ResourceLocation>> ICON_CODEC =
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC);
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Component>> TOOLTIP_CODEC =
            ByteBufCodecs.optional(ComponentSerialization.STREAM_CODEC);

    public static final StreamCodec<RegistryFriendlyByteBuf, MapMarker> STREAM_CODEC = StreamCodec.of(
            (buf, marker) -> {
                buf.writeUtf(marker.id);
                ResourceKey.streamCodec(Registries.DIMENSION).encode(buf, marker.dimension);
                buf.writeBlockPos(marker.pos);
                buf.writeInt(marker.color);
                ICON_CODEC.encode(buf, marker.icon);
                ComponentSerialization.STREAM_CODEC.encode(buf, marker.name);
                TOOLTIP_CODEC.encode(buf, marker.tooltip);
                buf.writeBoolean(marker.clickable);
            },
            buf -> new MapMarker(buf.readUtf(), ResourceKey.streamCodec(Registries.DIMENSION).decode(buf),
                    buf.readBlockPos(), buf.readInt(), ICON_CODEC.decode(buf),
                    ComponentSerialization.STREAM_CODEC.decode(buf), TOOLTIP_CODEC.decode(buf), buf.readBoolean()));

    /** A marker with no icon and no tooltip, and no click. */
    public static MapMarker of(String id, ResourceKey<Level> dimension, BlockPos pos, int color, Component name) {
        return new MapMarker(id, dimension, pos, color, Optional.empty(), name, Optional.empty(), false);
    }
}
