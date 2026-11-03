package io.github.nicolasgoyon.cartofold.api;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A change of some elements of a layer, with no copy of the other elements. An element with a new identifier goes into
 * the layer. An element with the identifier of an element of the layer replaces it.
 *
 * @param areas          the new or changed areas, {@link LayerContent#MAX_ELEMENTS} at most
 * @param lines          the new or changed lines, {@link LayerContent#MAX_ELEMENTS} at most
 * @param markers        the new or changed markers, {@link LayerContent#MAX_ELEMENTS} at most
 * @param removedAreas   the identifiers of the areas to remove, {@link LayerContent#MAX_ELEMENTS} at most
 * @param removedLines   the identifiers of the lines to remove, {@link LayerContent#MAX_ELEMENTS} at most
 * @param removedMarkers the identifiers of the markers to remove, {@link LayerContent#MAX_ELEMENTS} at most
 */
public record LayerUpdate(List<ChunkArea> areas, List<MapLine> lines, List<MapMarker> markers,
        List<String> removedAreas, List<String> removedLines, List<String> removedMarkers) {
    private static final StreamCodec<ByteBuf, List<String>> IDS_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(LayerContent.MAX_ELEMENTS));

    public static final StreamCodec<RegistryFriendlyByteBuf, LayerUpdate> STREAM_CODEC = StreamCodec.composite(
            ChunkArea.STREAM_CODEC.apply(ByteBufCodecs.list(LayerContent.MAX_ELEMENTS)), LayerUpdate::areas,
            MapLine.STREAM_CODEC.apply(ByteBufCodecs.list(LayerContent.MAX_ELEMENTS)), LayerUpdate::lines,
            MapMarker.STREAM_CODEC.apply(ByteBufCodecs.list(LayerContent.MAX_ELEMENTS)), LayerUpdate::markers,
            IDS_CODEC, LayerUpdate::removedAreas,
            IDS_CODEC, LayerUpdate::removedLines,
            IDS_CODEC, LayerUpdate::removedMarkers,
            LayerUpdate::new);

    public LayerUpdate {
        if (areas.size() > LayerContent.MAX_ELEMENTS || lines.size() > LayerContent.MAX_ELEMENTS
                || markers.size() > LayerContent.MAX_ELEMENTS || removedAreas.size() > LayerContent.MAX_ELEMENTS
                || removedLines.size() > LayerContent.MAX_ELEMENTS
                || removedMarkers.size() > LayerContent.MAX_ELEMENTS) {
            throw new IllegalArgumentException("An update has " + LayerContent.MAX_ELEMENTS
                    + " elements of each kind at most");
        }
        areas = List.copyOf(areas);
        lines = List.copyOf(lines);
        markers = List.copyOf(markers);
        removedAreas = List.copyOf(removedAreas);
        removedLines = List.copyOf(removedLines);
        removedMarkers = List.copyOf(removedMarkers);
    }

    /** Adds or changes markers. */
    public static LayerUpdate markers(List<MapMarker> markers) {
        return new LayerUpdate(List.of(), List.of(), markers, List.of(), List.of(), List.of());
    }

    /** Adds or changes one marker. */
    public static LayerUpdate marker(MapMarker marker) {
        return markers(List.of(marker));
    }

    /** Removes one marker. */
    public static LayerUpdate removeMarker(String id) {
        return new LayerUpdate(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(id));
    }

    /** Adds or changes one area. */
    public static LayerUpdate area(ChunkArea area) {
        return new LayerUpdate(List.of(area), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /** Removes one area. */
    public static LayerUpdate removeArea(String id) {
        return new LayerUpdate(List.of(), List.of(), List.of(), List.of(id), List.of(), List.of());
    }

    /** Adds or changes one line. */
    public static LayerUpdate line(MapLine line) {
        return new LayerUpdate(List.of(), List.of(line), List.of(), List.of(), List.of(), List.of());
    }

    /** Removes one line. */
    public static LayerUpdate removeLine(String id) {
        return new LayerUpdate(List.of(), List.of(), List.of(), List.of(), List.of(id), List.of());
    }
}
