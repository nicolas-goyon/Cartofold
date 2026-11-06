package io.github.nicolasgoyon.cartofold.api;

import io.netty.buffer.ByteBuf;
import java.util.List;
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
 * @param color     the color of the marker, in RGB: the color of the diamond of a marker with no icon, and the color
 *                  of a tinted icon
 * @param icon      the texture of the icon (a square image), or empty for a colored diamond
 * @param name      the name, under the marker on the map of the world and on the map frames
 * @param tooltipLines the lines when the mouse is on the marker, {@link #MAX_TOOLTIP_LINES} at most. With no line, the
 *                  marker has no tooltip.
 * @param clickable true if a click on the marker sends a {@link MarkerClickEvent} on the server
 * @param tinted    true to multiply the pixels of the icon by the color: a white pixel takes the color. With false, the
 *                  icon keeps its own colors.
 */
public record MapMarker(String id, ResourceKey<Level> dimension, BlockPos pos, int color,
        Optional<ResourceLocation> icon, Component name, List<Component> tooltipLines, boolean clickable,
        boolean tinted) {
    /** The largest number of lines of a tooltip. */
    public static final int MAX_TOOLTIP_LINES = 16;

    private static final StreamCodec<ByteBuf, Optional<ResourceLocation>> ICON_CODEC =
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC);
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> TOOLTIP_CODEC =
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TOOLTIP_LINES));

    public static final StreamCodec<RegistryFriendlyByteBuf, MapMarker> STREAM_CODEC = StreamCodec.of(
            (buf, marker) -> {
                buf.writeUtf(marker.id);
                ResourceKey.streamCodec(Registries.DIMENSION).encode(buf, marker.dimension);
                buf.writeBlockPos(marker.pos);
                buf.writeInt(marker.color);
                ICON_CODEC.encode(buf, marker.icon);
                ComponentSerialization.STREAM_CODEC.encode(buf, marker.name);
                TOOLTIP_CODEC.encode(buf, marker.tooltipLines);
                buf.writeBoolean(marker.clickable);
                buf.writeBoolean(marker.tinted);
            },
            buf -> new MapMarker(buf.readUtf(), ResourceKey.streamCodec(Registries.DIMENSION).decode(buf),
                    buf.readBlockPos(), buf.readInt(), ICON_CODEC.decode(buf),
                    ComponentSerialization.STREAM_CODEC.decode(buf), TOOLTIP_CODEC.decode(buf), buf.readBoolean(),
                    buf.readBoolean()));

    public MapMarker {
        if (tooltipLines.size() > MAX_TOOLTIP_LINES) {
            throw new IllegalArgumentException("A tooltip has " + MAX_TOOLTIP_LINES + " lines at most");
        }
        tooltipLines = List.copyOf(tooltipLines);
    }

    /** A marker with a tooltip of one line or no tooltip. */
    public MapMarker(String id, ResourceKey<Level> dimension, BlockPos pos, int color, Optional<ResourceLocation> icon,
            Component name, Optional<Component> tooltip, boolean clickable, boolean tinted) {
        this(id, dimension, pos, color, icon, name, tooltip.map(List::of).orElse(List.of()), clickable, tinted);
    }

    /** A marker with an icon that keeps its own colors, and a tooltip of one line or no tooltip. */
    public MapMarker(String id, ResourceKey<Level> dimension, BlockPos pos, int color, Optional<ResourceLocation> icon,
            Component name, Optional<Component> tooltip, boolean clickable) {
        this(id, dimension, pos, color, icon, name, tooltip, clickable, false);
    }

    /** The first line of the tooltip, or empty with no tooltip. {@link #tooltipLines} gives all the lines. */
    public Optional<Component> tooltip() {
        return tooltipLines.isEmpty() ? Optional.empty() : Optional.of(tooltipLines.getFirst());
    }

    /** The same marker with other lines of tooltip, {@link #MAX_TOOLTIP_LINES} at most. */
    public MapMarker withTooltip(List<Component> lines) {
        return new MapMarker(id, dimension, pos, color, icon, name, lines, clickable, tinted);
    }

    /** The same marker, with a click that posts a {@link MarkerClickEvent} on the server, or with no click. */
    public MapMarker withClick(boolean click) {
        return new MapMarker(id, dimension, pos, color, icon, name, tooltipLines, click, tinted);
    }

    /** A marker with no icon and no tooltip, and no click. */
    public static MapMarker of(String id, ResourceKey<Level> dimension, BlockPos pos, int color, Component name) {
        return new MapMarker(id, dimension, pos, color, Optional.empty(), name, Optional.empty(), false);
    }

    /**
     * A marker with an icon in the color of the marker, with no tooltip and no click. One white icon thus shows in all
     * the colors.
     *
     * @param icon the texture of the icon: a white icon of a mod, or a shape of {@link MarkerShapes}
     */
    public static MapMarker tinted(String id, ResourceKey<Level> dimension, BlockPos pos, int color,
            ResourceLocation icon, Component name) {
        return new MapMarker(id, dimension, pos, color, Optional.of(icon), name, Optional.empty(), false, true);
    }
}
