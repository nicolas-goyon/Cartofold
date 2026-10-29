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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

/** Draws the layers in the map views, and finds the marker under the mouse. */
public final class LayerRenderer {
    /** The width of the border of an area, in units of the screen. */
    private static final float BORDER_WIDTH = 1.5f;
    /** The size of an icon of a marker, in units of the screen. */
    private static final int ICON_SIZE = 10;
    /** The distance in units of the screen to select a marker with the mouse. */
    private static final double SELECT_DISTANCE = 6;

    private LayerRenderer() {
    }

    /** A marker of a layer. */
    public record Hit(ResourceLocation layer, MapMarker marker) {
    }

    /** True if the player shows the layer in this kind of view. */
    public static boolean visible(LayerInfo info, MapViewKind kind) {
        return info.views().contains(kind) && !CartofoldClientConfig.HIDDEN_LAYERS.get().contains(info.id()
                .toString());
    }

    /** Draws the visible layers of a dimension, in their order. */
    public static void render(GuiGraphics graphics, ResourceKey<Level> dimension, MapProjection projection,
            MapViewKind kind) {
        record Entry(int order, Runnable draw) {
        }
        List<Entry> entries = new ArrayList<>();
        Explored explored = new Explored(dimension);
        LayerRenderContext context = new LayerRenderContext(graphics, projection, dimension, kind);
        for (LayerContent content : ClientLayers.get().contents()) {
            if (visible(content.info(), kind)) {
                entries.add(new Entry(content.info().order(),
                        () -> drawContent(graphics, content, dimension, projection, kind, explored)));
            }
        }
        for (MapLayerRenderer renderer : ClientLayers.get().renderers()) {
            if (visible(renderer.info(), kind)) {
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
            MapProjection projection, MapViewKind kind, Explored explored) {
        boolean onlyExplored = content.info().onlyExplored();
        double[] bounds = projection.blockBounds();
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = MapShapes.begin(VertexFormat.Mode.TRIANGLES);
        for (ChunkArea area : content.areas()) {
            if (!area.dimension().equals(dimension)) {
                continue;
            }
            if ((area.fillColor() >>> 24) != 0) {
                area.chunks().forEach(packed -> {
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
                for (int[] side : LayerGeometry.border(area.chunks(), bounds)) {
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
        Font font = Minecraft.getInstance().font;
        boolean names = kind == MapViewKind.WORLD_MAP || kind == MapViewKind.FRAME;
        for (MapMarker marker : content.markers()) {
            if (!marker.dimension().equals(dimension) || (onlyExplored && !explored.block(marker.pos()))) {
                continue;
            }
            double[] point = projection.toScreen(marker.pos().getX() + 0.5, marker.pos().getZ() + 0.5);
            if (marker.icon().isPresent()) {
                graphics.blit(marker.icon().get(), (int) Math.round(point[0]) - ICON_SIZE / 2,
                        (int) Math.round(point[1]) - ICON_SIZE / 2, ICON_SIZE, ICON_SIZE, 0, 0, 1, 1, 1, 1);
            } else {
                MapShapes.drawMarker(graphics, (float) point[0], (float) point[1], marker.color(), false);
            }
            if (names) {
                graphics.drawCenteredString(font, marker.name(), (int) Math.round(point[0]),
                        (int) Math.round(point[1]) + ICON_SIZE / 2 + 2, 0xFFFFFFFF);
            }
        }
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
            double screenX, double screenY) {
        Hit best = null;
        double bestDistance = SELECT_DISTANCE;
        Explored explored = new Explored(dimension);
        for (LayerContent content : ClientLayers.get().contents()) {
            if (!visible(content.info(), kind)) {
                continue;
            }
            for (MapMarker marker : content.markers()) {
                if (!marker.dimension().equals(dimension)
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
}
