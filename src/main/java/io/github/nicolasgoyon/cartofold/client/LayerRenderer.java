package io.github.nicolasgoyon.cartofold.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.nicolasgoyon.cartofold.api.ChunkArea;
import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.api.LayerInfo;
import io.github.nicolasgoyon.cartofold.api.MapLine;
import io.github.nicolasgoyon.cartofold.api.MapMarker;
import io.github.nicolasgoyon.cartofold.api.MapViewKind;
import io.github.nicolasgoyon.cartofold.api.client.LayerRenderContext;
import io.github.nicolasgoyon.cartofold.api.client.MapLayerRenderer;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.view.LayerGeometry;
import io.github.nicolasgoyon.cartofold.view.MapProjection;
import io.github.nicolasgoyon.cartofold.view.MarkerSizes;
import io.github.nicolasgoyon.cartofold.waypoint.BannerMark;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

/** Draws the layers in the map views, and finds the marker and the area under the mouse. */
public final class LayerRenderer {
    /** The width of the border of an area, in units of the screen. */
    private static final float BORDER_WIDTH = 1.5f;
    /** The side of the textures of the icons in the draw, before the scale of {@link MarkerSizes#icon}. */
    private static final int ICON_TEXTURE = 10;
    /** The distance in units of the screen to select a marker with the mouse. */

    private LayerRenderer() {
    }

    /** A marker of a layer. */
    public record Hit(ResourceLocation layer, MapMarker marker) {
        /** The key of the marker in the names of a map. */
        public MapLabels.Key key() {
            return new MapLabels.Key(layer, marker.id());
        }
    }

    /** True if the player shows the layer in this kind of view. */
    public static boolean visible(LayerInfo info, MapViewKind kind) {
        if (info.id().equals(BannerMark.LAYER) && !CartofoldClientConfig.BANNER_VIEWS.get().shows(kind)) {
            return false;
        }
        return info.views().contains(kind) && !CartofoldClientConfig.HIDDEN_LAYERS.get().contains(info.id()
                .toString());
    }

    /**
     * Draws the visible layers of a dimension, in their order.
     *
     * @param layers the layers to draw, among the visible layers
     * @param labels the names of the markers, or null for a view with no names
     */
    public static void render(GuiGraphics graphics, ResourceKey<Level> dimension, MapProjection projection,
            MapViewKind kind, Predicate<ResourceLocation> layers, @Nullable MapLabels labels) {
        record Entry(int order, Runnable draw) {
        }
        List<Entry> entries = new ArrayList<>();
        Explored explored = new Explored(dimension);
        LayerRenderContext context = new LayerRenderContext(graphics, projection, dimension, kind);
        for (LayerContent content : ClientLayers.get().contents()) {
            if (visible(content.info(), kind) && layers.test(content.info().id())) {
                entries.add(new Entry(content.info().order(),
                        () -> drawContent(graphics, content, dimension, projection, labels, explored)));
            }
        }
        for (MapLayerRenderer renderer : ClientLayers.get().renderers()) {
            if (visible(renderer.info(), kind) && layers.test(renderer.info().id())) {
                entries.add(new Entry(renderer.info().order(), () -> renderer.render(context)));
            }
        }
        entries.sort(Comparator.comparingInt(Entry::order));
        entries.forEach(entry -> entry.draw().run());
    }

    /** The explored chunks of the client, with a cache of the regions for one draw. */
    private static final class Explored {
        private final ResourceKey<Level> dimension;
        private final Map<RegionPos, Optional<MapRegion>> regions = new HashMap<>();

        Explored(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }

        boolean chunk(int chunkX, int chunkZ) {
            Optional<MapRegion> region = regions.computeIfAbsent(RegionPos.ofChunk(chunkX, chunkZ),
                    pos -> ClientExploration.get().region(dimension, pos));
            return region.isPresent() && region.get().sampled(chunkX, chunkZ) != MapRegion.NEVER;
        }

        boolean block(BlockPos pos) {
            return chunk(pos.getX() >> 4, pos.getZ() >> 4);
        }
    }

    private static void drawContent(GuiGraphics graphics, LayerContent content, ResourceKey<Level> dimension,
            MapProjection projection, @Nullable MapLabels labels, Explored explored) {
        boolean onlyExplored = content.info().onlyExplored();
        double[] bounds = projection.blockBounds();
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = MapShapes.begin(VertexFormat.Mode.TRIANGLES);
        for (ChunkArea area : content.areas()) {
            if (!area.dimension().equals(dimension)) {
                continue;
            }
            // An area of its own with only the explored chunks: its fill and its border follow the explored part.
            LongSet chunks = area.onlyExplored() ? exploredChunks(area.chunks(), explored) : area.chunks();
            if ((area.fillColor() >>> 24) != 0) {
                chunks.forEach(packed -> {
                    int chunkX = ChunkPos.getX(packed);
                    int chunkZ = ChunkPos.getZ(packed);
                    int minX = chunkX << 4;
                    int minZ = chunkZ << 4;
                    if (minX + 16 < bounds[0] || minX > bounds[2] || minZ + 16 < bounds[1] || minZ > bounds[3]
                            || (onlyExplored && !explored.chunk(chunkX, chunkZ))) {
                        return;
                    }
                    MapShapes.quad(buffer, matrix, projection.toScreen(minX, minZ),
                            projection.toScreen(minX + 16, minZ), projection.toScreen(minX + 16, minZ + 16),
                            projection.toScreen(minX, minZ + 16), area.fillColor());
                });
            }
            if ((area.borderColor() >>> 24) != 0) {
                for (int[] side : LayerGeometry.border(chunks, bounds)) {
                    // A side is between a chunk of the area and a chunk out of the area: one of the two is explored.
                    if (onlyExplored && !sideExplored(side, explored)) {
                        continue;
                    }
                    MapShapes.line(buffer, matrix, projection.toScreen(side[0], side[1]),
                            projection.toScreen(side[2], side[3]), BORDER_WIDTH, area.borderColor());
                }
            }
        }
        for (MapLine line : content.lines()) {
            if (!line.dimension().equals(dimension)) {
                continue;
            }
            for (int i = 1; i < line.points().size(); i++) {
                BlockPos from = line.points().get(i - 1);
                BlockPos to = line.points().get(i);
                if (onlyExplored && !explored.block(from) && !explored.block(to)) {
                    continue;
                }
                MapShapes.line(buffer, matrix, projection.toScreen(from.getX() + 0.5, from.getZ() + 0.5),
                        projection.toScreen(to.getX() + 0.5, to.getZ() + 0.5), line.width(), line.color());
            }
        }
        MapShapes.draw(buffer);
        MarkerSizes sizes = CartofoldClientConfig.markerSizes();
        for (MapMarker marker : content.markers()) {
            if (!marker.dimension().equals(dimension) || (onlyExplored && !explored.block(marker.pos()))) {
                continue;
            }
            double[] point = projection.toScreen(marker.pos().getX() + 0.5, marker.pos().getZ() + 0.5);
            if (labels != null && labels.selected(content.info().id(), marker.id())) {
                MapShapes.drawSelection(graphics, (float) point[0], (float) point[1]);
            }
            if (marker.icon().isPresent()) {
                if (marker.tinted()) {
                    graphics.setColor(((marker.color() >> 16) & 0xFF) / 255f, ((marker.color() >> 8) & 0xFF) / 255f,
                            (marker.color() & 0xFF) / 255f, 1);
                }
                // The pose keeps the point with no rounding: the icon stays on its pixel of the map.
                graphics.pose().pushPose();
                float scale = (float) (sizes.icon() / ICON_TEXTURE);
                graphics.pose().translate(point[0], point[1], 0);
                graphics.pose().scale(scale, scale, 1);
                graphics.blit(marker.icon().get(), -ICON_TEXTURE / 2, -ICON_TEXTURE / 2, ICON_TEXTURE, ICON_TEXTURE, 0,
                        0, 1, 1, 1, 1);
                graphics.pose().popPose();
                graphics.setColor(1, 1, 1, 1);
            } else {
                MapShapes.drawMarker(graphics, (float) point[0], (float) point[1], marker.color(), false);
            }
            if (labels != null && projection.contains(point[0], point[1])) {
                labels.add(content.info().id(), marker.id(), marker.name(), point[0], point[1] + sizes.nameOffset());
            }
        }
    }

    /** The chunks of a set that the player explored. */
    private static LongSet exploredChunks(LongSet chunks, Explored explored) {
        LongOpenHashSet result = new LongOpenHashSet();
        chunks.forEach(packed -> {
            if (explored.chunk(ChunkPos.getX(packed), ChunkPos.getZ(packed))) {
                result.add(packed);
            }
        });
        return result;
    }

    private static boolean sideExplored(int[] side, Explored explored) {
        int midX = (side[0] + side[2]) / 2;
        int midZ = (side[1] + side[3]) / 2;
        boolean vertical = side[0] == side[2];
        int beforeX = vertical ? midX - 1 : midX;
        int beforeZ = vertical ? midZ : midZ - 1;
        return explored.chunk(beforeX >> 4, beforeZ >> 4) || explored.chunk(midX >> 4, midZ >> 4);
    }

    /** The visible marker of the layers of a dimension at a point of the screen, or empty. */
    public static Optional<Hit> markerAt(ResourceKey<Level> dimension, MapProjection projection, MapViewKind kind,
            Predicate<ResourceLocation> layers, double screenX, double screenY) {
        return markerAt(dimension, projection, kind, layers, screenX, screenY, marker -> true);
    }

    /** The visible clickable marker of the layers of a dimension at a point of the screen, or empty. */
    public static Optional<Hit> clickableMarkerAt(ResourceKey<Level> dimension, MapProjection projection,
            MapViewKind kind, Predicate<ResourceLocation> layers, double screenX, double screenY) {
        return markerAt(dimension, projection, kind, layers, screenX, screenY, MapMarker::clickable);
    }

    private static Optional<Hit> markerAt(ResourceKey<Level> dimension, MapProjection projection, MapViewKind kind,
            Predicate<ResourceLocation> layers, double screenX, double screenY, Predicate<MapMarker> filter) {
        Hit best = null;
        double bestDistance = CartofoldClientConfig.markerSizes().hit();
        Explored explored = new Explored(dimension);
        for (LayerContent content : ClientLayers.get().contents()) {
            if (!visible(content.info(), kind) || !layers.test(content.info().id())) {
                continue;
            }
            for (MapMarker marker : content.markers()) {
                if (!filter.test(marker) || !marker.dimension().equals(dimension)
                        || (content.info().onlyExplored() && !explored.block(marker.pos()))) {
                    continue;
                }
                double[] point = projection.toScreen(marker.pos().getX() + 0.5, marker.pos().getZ() + 0.5);
                double distance = Math.hypot(point[0] - screenX, point[1] - screenY);
                if (distance <= bestDistance) {
                    bestDistance = distance;
                    best = new Hit(content.info().id(), marker);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * The visible area with a tooltip of the layers of a dimension at a point of the screen, or empty. The area of the
     * highest layer comes first. In a layer, the last area comes first.
     */
    public static Optional<ChunkArea> areaAt(ResourceKey<Level> dimension, MapProjection projection, MapViewKind kind,
            Predicate<ResourceLocation> layers, double screenX, double screenY) {
        double[] block = projection.toBlock(screenX, screenY);
        int chunkX = (int) Math.floor(block[0]) >> 4;
        int chunkZ = (int) Math.floor(block[1]) >> 4;
        Explored explored = new Explored(dimension);
        List<LayerContent> contents = ClientLayers.get().contents();
        for (LayerContent content : contents.reversed()) {
            if (!visible(content.info(), kind) || !layers.test(content.info().id())
                    || (content.info().onlyExplored() && !explored.chunk(chunkX, chunkZ))) {
                continue;
            }
            for (ChunkArea area : content.areas().reversed()) {
                if (!area.tooltip().isEmpty() && area.dimension().equals(dimension)
                        && area.contains(chunkX, chunkZ) && (!area.onlyExplored() || explored.chunk(chunkX, chunkZ))) {
                    return Optional.of(area);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The lines of the tooltip at a point of the screen: the tooltip of the marker, or the tooltip of the area.
     *
     * @return the lines, or an empty list
     */
    public static List<Component> tooltipAt(ResourceKey<Level> dimension, MapProjection projection, MapViewKind kind,
            Predicate<ResourceLocation> layers, double screenX, double screenY) {
        List<Component> marker = markerAt(dimension, projection, kind, layers, screenX, screenY)
                .map(hit -> hit.marker().tooltipLines()).orElse(List.of());
        if (!marker.isEmpty()) {
            return marker;
        }
        return areaAt(dimension, projection, kind, layers, screenX, screenY).map(ChunkArea::tooltip)
                .orElse(List.of());
    }
}
