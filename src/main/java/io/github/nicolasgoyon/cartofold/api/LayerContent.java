package io.github.nicolasgoyon.cartofold.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * All the content of a layer, in all the dimensions: its description, its areas of chunks, its lines and its markers.
 * A new content of a layer replaces the old content. A {@link LayerUpdate} changes some elements of a layer.
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

    /**
     * The content after a change. The new elements go after the other elements. A changed element keeps its place.
     *
     * @throws IllegalArgumentException if the new content has more than {@link #MAX_ELEMENTS} elements of a kind
     */
    public LayerContent apply(LayerUpdate update) {
        return new LayerContent(info, merge(areas, update.areas(), update.removedAreas(), ChunkArea::id),
                merge(lines, update.lines(), update.removedLines(), MapLine::id),
                merge(markers, update.markers(), update.removedMarkers(), MapMarker::id));
    }

    private static <T> List<T> merge(List<T> elements, List<T> changed, List<String> removed, Function<T, String> id) {
        if (changed.isEmpty() && removed.isEmpty()) {
            return elements;
        }
        Map<String, T> merged = new LinkedHashMap<>();
        elements.forEach(element -> merged.put(id.apply(element), element));
        removed.forEach(merged::remove);
        changed.forEach(element -> merged.put(id.apply(element), element));
        return new ArrayList<>(merged.values());
    }

    /** The marker with an identifier, if the layer has it. */
    public Optional<MapMarker> marker(String id) {
        return markers.stream().filter(marker -> marker.id().equals(id)).findFirst();
    }
}
