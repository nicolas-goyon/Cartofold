package io.github.nicolasgoyon.cartofold.api;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * An area of chunks of a layer, for example a territory. The views fill the chunks with a color and draw a border
 * around the area.
 */
public final class ChunkArea {
    /** The largest number of chunks of an area. */
    public static final int MAX_CHUNKS = 1 << 16;
    /** The largest number of lines of a tooltip. */
    public static final int MAX_TOOLTIP_LINES = 16;

    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> TOOLTIP_CODEC =
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TOOLTIP_LINES));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkArea> STREAM_CODEC = StreamCodec.of(
            (buf, area) -> {
                buf.writeUtf(area.id);
                ResourceKey.streamCodec(Registries.DIMENSION).encode(buf, area.dimension);
                buf.writeVarInt(area.chunks.size());
                area.chunks.forEach(buf::writeLong);
                buf.writeInt(area.fillColor);
                buf.writeInt(area.borderColor);
                ComponentSerialization.STREAM_CODEC.encode(buf, area.name);
                TOOLTIP_CODEC.encode(buf, area.tooltip);
            },
            buf -> {
                String id = buf.readUtf();
                ResourceKey<Level> dimension = ResourceKey.streamCodec(Registries.DIMENSION).decode(buf);
                int count = ByteBufCodecs.VAR_INT.decode(buf);
                if (count < 0 || count > MAX_CHUNKS) {
                    throw new io.netty.handler.codec.DecoderException("Too many chunks: " + count);
                }
                LongOpenHashSet chunks = new LongOpenHashSet(count);
                for (int i = 0; i < count; i++) {
                    chunks.add(buf.readLong());
                }
                int fill = buf.readInt();
                int border = buf.readInt();
                Component name = ComponentSerialization.STREAM_CODEC.decode(buf);
                List<Component> tooltip = TOOLTIP_CODEC.decode(buf);
                return new ChunkArea(id, dimension, chunks, fill, border, name, tooltip);
            });

    private final String id;
    private final ResourceKey<Level> dimension;
    private final LongSet chunks;
    private final int fillColor;
    private final int borderColor;
    private final Component name;
    private final List<Component> tooltip;

    private ChunkArea(String id, ResourceKey<Level> dimension, LongOpenHashSet chunks, int fillColor,
            int borderColor, Component name, List<Component> tooltip) {
        if (chunks.size() > MAX_CHUNKS) {
            throw new IllegalArgumentException("An area has " + MAX_CHUNKS + " chunks at most");
        }
        if (tooltip.size() > MAX_TOOLTIP_LINES) {
            throw new IllegalArgumentException("A tooltip has " + MAX_TOOLTIP_LINES + " lines at most");
        }
        this.id = id;
        this.dimension = dimension;
        this.chunks = LongSets.unmodifiable(chunks);
        this.fillColor = fillColor;
        this.borderColor = borderColor;
        this.name = name;
        this.tooltip = List.copyOf(tooltip);
    }

    /**
     * Makes an area with no tooltip.
     *
     * @param id          the identifier of the area in its layer
     * @param dimension   the dimension of the chunks
     * @param chunks      the chunks
     * @param fillColor   the color of the fill, in ARGB (an alpha of 0 for no fill)
     * @param borderColor the color of the border, in ARGB (an alpha of 0 for no border)
     * @param name        the name of the area
     */
    public static ChunkArea of(String id, ResourceKey<Level> dimension, Collection<ChunkPos> chunks, int fillColor,
            int borderColor, Component name) {
        return of(id, dimension, chunks, fillColor, borderColor, name, List.of());
    }

    /**
     * Makes an area with a tooltip.
     *
     * @param id          the identifier of the area in its layer
     * @param dimension   the dimension of the chunks
     * @param chunks      the chunks
     * @param fillColor   the color of the fill, in ARGB (an alpha of 0 for no fill)
     * @param borderColor the color of the border, in ARGB (an alpha of 0 for no border)
     * @param name        the name of the area
     * @param tooltip     the lines when the mouse is above a chunk of the area, {@link #MAX_TOOLTIP_LINES} at most
     */
    public static ChunkArea of(String id, ResourceKey<Level> dimension, Collection<ChunkPos> chunks, int fillColor,
            int borderColor, Component name, List<Component> tooltip) {
        LongOpenHashSet packed = new LongOpenHashSet(chunks.size());
        chunks.forEach(chunk -> packed.add(chunk.toLong()));
        return new ChunkArea(id, dimension, packed, fillColor, borderColor, name, tooltip);
    }

    public String id() {
        return id;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    /** The chunks, as {@link ChunkPos#toLong()}. */
    public LongSet chunks() {
        return chunks;
    }

    /** True if the area has a chunk. */
    public boolean contains(int chunkX, int chunkZ) {
        return chunks.contains(ChunkPos.asLong(chunkX, chunkZ));
    }

    public int fillColor() {
        return fillColor;
    }

    public int borderColor() {
        return borderColor;
    }

    public Component name() {
        return name;
    }

    /** The lines when the mouse is above a chunk of the area. With no line, the area has no tooltip. */
    public List<Component> tooltip() {
        return tooltip;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ChunkArea area && id.equals(area.id) && dimension.equals(area.dimension)
                && chunks.equals(area.chunks) && fillColor == area.fillColor && borderColor == area.borderColor
                && name.equals(area.name) && tooltip.equals(area.tooltip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, dimension, chunks, fillColor, borderColor, name, tooltip);
    }
}
