package io.github.nicolasgoyon.cartofold.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
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
    /** The size of an icon of a marker in the world view (see {@link WorldHud}), in units of the screen. */
    static final int ICON_SIZE = 10;
    /** The shapes of the areas, by area (the same instance, not an equal area: see {@link #shape}). */
    private static final IdentityHashMap<ChunkArea, Shape> SHAPES = new IdentityHashMap<>();
    /** The version of the layers of {@link #SHAPES}. */
    private static long shapesVersion = -1;

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
        forgetOldShapes();
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

    /** The explored blocks of a dimension, with a cache of the regions for one draw. */
    static Predicate<BlockPos> exploredBlocks(ResourceKey<Level> dimension) {
        return new Explored(dimension)::block;
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
            Shape shape = shape(area, onlyExplored, explored);
            int[] rows = shape.rows;
            for (int i = 0; i < rows.length; i += 3) {
                double minZ = rows[i] << 4;
                // The view shows only a part of a long row: the quad stops at the box of the view.
                double minX = Math.max(rows[i + 1] << 4, Math.floor(bounds[0]));
                double maxX = Math.min((rows[i + 2] + 1) << 4, Math.ceil(bounds[2]));
                if (minX > maxX || minZ + 16 < bounds[1] || minZ > bounds[3]) {
                    continue;
                }
                MapShapes.quad(buffer, matrix, projection.toScreen(minX, minZ), projection.toScreen(maxX, minZ),
                        projection.toScreen(maxX, minZ + 16), projection.toScreen(minX, minZ + 16), area.fillColor());
            }
            for (int[] side : shape.sides) {
                double fromX = Math.max(side[0], Math.floor(bounds[0]));
                double fromZ = Math.max(side[1], Math.floor(bounds[1]));
                double toX = Math.min(side[2], Math.ceil(bounds[2]));
                double toZ = Math.min(side[3], Math.ceil(bounds[3]));
                if (fromX > toX || fromZ > toZ) {
                    continue;
                }
                MapShapes.line(buffer, matrix, projection.toScreen(fromX, fromZ), projection.toScreen(toX, toZ),
                        BORDER_WIDTH, area.borderColor());
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
        // A marker out of the view, with its icon and its selection, is not visible: it needs no draw.
        double margin = Math.max(sizes.icon(), sizes.diamond() * 6) + sizes.selection() + 2;
        float diamond = (float) sizes.diamond();
        BufferBuilder diamonds = MapShapes.begin(VertexFormat.Mode.TRIANGLES);
        Map<ResourceLocation, List<Icon>> icons = new LinkedHashMap<>();
        for (MapMarker marker : content.markers()) {
            if (!marker.dimension().equals(dimension) || (onlyExplored && !explored.block(marker.pos()))) {
                continue;
            }
            double[] point = projection.toScreen(marker.pos().getX() + 0.5, marker.pos().getZ() + 0.5);
            if (!projection.contains(point[0], point[1], margin)) {
                continue;
            }
            if (labels != null && labels.selected(content.info().id(), marker.id())) {
                MapShapes.drawSelection(graphics, (float) point[0], (float) point[1]);
            }
            if (marker.icon().isPresent()) {
                int color = marker.tinted() ? marker.color() & 0xFFFFFF : 0xFFFFFF;
                icons.computeIfAbsent(marker.icon().get(), key -> new ArrayList<>())
                        .add(new Icon((float) point[0], (float) point[1], 0xFF000000 | color));
            } else {
                MapShapes.marker(diamonds, new Matrix4f(matrix).translate((float) point[0], (float) point[1], 0)
                        .scale(diamond, diamond, 1), marker.color(), false, 1);
            }
            if (labels != null && projection.contains(point[0], point[1])) {
                labels.add(content.info().id(), marker.id(), marker.name(), point[0], point[1] + sizes.nameOffset());
            }
        }
        MapShapes.draw(diamonds);
        drawIcons(matrix, icons, (float) sizes.icon() / 2);
    }

    /** An icon of a marker to draw: its point on the screen and its color, in ARGB. */
    private record Icon(float x, float y, int color) {
    }

    /** Draws the icons of the markers, in one draw for each texture. */
    private static void drawIcons(Matrix4f matrix, Map<ResourceLocation, List<Icon>> icons, float half) {
        if (icons.isEmpty()) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        icons.forEach((texture, entries) -> {
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Icon icon : entries) {
                // The point has no rounding: the icon stays on its pixel of the map.
                buffer.addVertex(matrix, icon.x() - half, icon.y() - half, 0).setUv(0, 0).setColor(icon.color());
                buffer.addVertex(matrix, icon.x() - half, icon.y() + half, 0).setUv(0, 1).setColor(icon.color());
                buffer.addVertex(matrix, icon.x() + half, icon.y() + half, 0).setUv(1, 1).setColor(icon.color());
                buffer.addVertex(matrix, icon.x() + half, icon.y() - half, 0).setUv(1, 0).setColor(icon.color());
            }
            MeshData mesh = buffer.build();
            if (mesh != null) {
                RenderSystem.setShaderTexture(0, texture);
                BufferUploader.drawWithShader(mesh);
            }
        });
    }

    /**
     * The shape of an area for the draw, in blocks: the rows of its fill (see {@link LayerGeometry#rows}) and the
     * lines of its border.
     */
    private static final class Shape {
        /** True if the layer shows only the explored chunks. */
        final boolean layerExplored;
        /** The version of the exploration of the shape, or 0 for a shape with no use of the exploration. */
        final long exploration;
        final int[] rows;
        final List<int[]> sides;

        Shape(boolean layerExplored, long exploration, int[] rows, List<int[]> sides) {
            this.layerExplored = layerExplored;
            this.exploration = exploration;
            this.rows = rows;
            this.sides = sides;
        }
    }

    /**
     * The shape of an area, from the cache. A shape stays until a change of the layers, or a change of the exploration
     * for an area with only the explored chunks: the views do not compute the shape at each frame.
     */
    private static Shape shape(ChunkArea area, boolean layerExplored, Explored explored) {
        boolean usesExploration = area.onlyExplored() || layerExplored;
        long exploration = usesExploration ? ClientExploration.get().version() : 0;
        Shape shape = SHAPES.get(area);
        if (shape != null && shape.layerExplored == layerExplored && shape.exploration == exploration) {
            return shape;
        }
        LongSet visible = usesExploration ? exploredChunks(area.chunks(), explored) : area.chunks();
        // An area of its own with only the explored chunks: its border follows the explored part.
        LongSet outline = area.onlyExplored() ? visible : area.chunks();
        List<int[]> sides = new ArrayList<>();
        if ((area.borderColor() >>> 24) != 0) {
            for (int[] side : LayerGeometry.border(outline, LayerGeometry.all())) {
                // A side is between a chunk of the area and a chunk out of the area: one of the two is explored.
                if (!layerExplored || sideExplored(side, explored)) {
                    sides.add(side);
                }
            }
        }
        int[] rows = (area.fillColor() >>> 24) != 0 ? LayerGeometry.rows(visible) : new int[0];
        shape = new Shape(layerExplored, exploration, rows, LayerGeometry.join(sides));
        SHAPES.put(area, shape);
        return shape;
    }

    /** Forgets the shapes of the areas that the layers no longer have. */
    private static void forgetOldShapes() {
        long version = ClientLayers.get().version();
        if (version == shapesVersion) {
            return;
        }
        shapesVersion = version;
        Set<ChunkArea> areas = Collections.newSetFromMap(new IdentityHashMap<>());
        for (LayerContent content : ClientLayers.get().contents()) {
            areas.addAll(content.areas());
        }
        SHAPES.keySet().retainAll(areas);
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
