package io.github.nicolasgoyon.cartofold.api;

import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * All the content of a layer, in all the dimensions: its description, its areas of chunks, its lines and its markers.
 * A new content of a layer replaces the old content.
 *
 * @param info    the description of the layer
 * @param areas   the areas of chunks, {@link #MAX_ELEMENTS} at most
 * @param lines   the lines, {@link #MAX_ELEMENTS} at most
 * @param markers the markers, {@link #MAX_ELEMENTS} at most
 */
public record LayerContent(LayerInfo info, List<ChunkArea> areas, List<MapLine> lines, List<MapMarker> markers) {
    /** The largest number of areas, of lines and of markers of a layer. */
    public static final int MAX_ELEMENTS = 4096;

    public static final StreamCodec<RegistryFriendlyByteBuf, LayerContent> STREAM_CODEC = StreamCodec.composite(
            LayerInfo.STREAM_CODEC, LayerContent::info,
            ChunkArea.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ELEMENTS)), LayerContent::areas,
            MapLine.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ELEMENTS)), LayerContent::lines,
            MapMarker.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ELEMENTS)), LayerContent::markers,
            LayerContent::new);

    public LayerContent {
        if (areas.size() > MAX_ELEMENTS || lines.size() > MAX_ELEMENTS || markers.size() > MAX_ELEMENTS) {
            throw new IllegalArgumentException("A layer has " + MAX_ELEMENTS + " elements of each kind at most");
        }
        areas = List.copyOf(areas);
        lines = List.copyOf(lines);
        markers = List.copyOf(markers);
    }

    /** The marker with an identifier, if the layer has it. */
    public Optional<MapMarker> marker(String id) {
        return markers.stream().filter(marker -> marker.id().equals(id)).findFirst();
    }
}
