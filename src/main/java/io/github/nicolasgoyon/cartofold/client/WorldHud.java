package io.github.nicolasgoyon.cartofold.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.api.LayerInfo;
import io.github.nicolasgoyon.cartofold.api.MapMarker;
import io.github.nicolasgoyon.cartofold.api.MapViewKind;
import io.github.nicolasgoyon.cartofold.view.HudLayout;
import io.github.nicolasgoyon.cartofold.waypoint.Waypoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/**
 * The markers in the world (the HUD): a layer of the screen of the game. It shows the waypoints of the player and the
 * markers of the layers with the kind {@link MapViewKind#HUD} at their positions, with their icon, their name and their
 * distance (see {@link CartofoldClientConfig} for the settings).
 *
 * <p>The draw of the world gives the matrices of the camera. The layer of the screen then projects each marker on the
 * screen with these matrices. Each marker has its own depth: a near marker is above a far marker. Thus the layer draws
 * all the backgrounds in one draw, the icons in one draw for each texture, and all the texts in one draw.
 */
public final class WorldHud {
    private static final int TEXT = 0xFFFFFF;
    /** The opacity of the background of a text, for an opacity of 1 of the markers. */
    private static final float BACKGROUND_OPACITY = 0.4f;
    /** The space around the screen where a marker still draws: a part of a marker on the side stays visible. */
    private static final double MARGIN = 32;
    /** The depth of all the markers, in units of the screen. It stays below the next layer of the screen. */
    private static final float DEPTH = 100;
    /** The space between the text lines, in units of the screen. */
    private static final int LINE_SPACE = 2;

    private static final Matrix4f VIEW_PROJECTION = new Matrix4f();
    private static Vec3 camera = Vec3.ZERO;
    /** True when the draw of the world of this frame gave the matrices. */
    private static boolean captured;

    private WorldHud() {
    }

    /** A marker to draw: a waypoint or a marker of a layer. */
    private record Marker(Component name, int color, @Nullable ResourceLocation icon, boolean tinted,
            boolean death) {
    }

    /** A marker on the screen, with its matrix and its text lines. */
    private record Placed(Marker marker, Matrix4f matrix, List<Component> lines) {
    }

    /** Keeps the matrices and the position of the camera, at the end of the draw of the world. */
    public static void capture(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        VIEW_PROJECTION.set(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        camera = event.getCamera().getPosition();
        captured = true;
    }

    /** Draws the markers, if the settings show them. */
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        boolean ready = captured;
        captured = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (!ready || minecraft.player == null || minecraft.level == null || minecraft.options.hideGui
                || !CartofoldClientConfig.HUD.get()) {
            return;
        }
        List<HudLayout.Point<Marker>> points = HudLayout.select(points(minecraft.level.dimension()),
                CartofoldClientConfig.HUD_MIN_DISTANCE.get(), CartofoldClientConfig.HUD_MAX_DISTANCE.get());
        List<Placed> placed = place(graphics, points);
        if (placed.isEmpty()) {
            return;
        }
        float opacity = CartofoldClientConfig.HUD_OPACITY.get() / 100f;
        graphics.flush();
        RenderSystem.enableDepthTest();
        drawShapes(minecraft.font, placed, opacity);
        drawIcons(placed, opacity);
        drawTexts(graphics, minecraft.font, placed, opacity);
    }

    /** The markers in front of the camera, with their matrix, from the farthest to the nearest. */
    private static List<Placed> place(GuiGraphics graphics, List<HudLayout.Point<Marker>> points) {
        double height = CartofoldClientConfig.HUD_HEIGHT.get();
        float scale = CartofoldClientConfig.HUD_SCALE.get().floatValue();
        boolean names = CartofoldClientConfig.HUD_NAMES.get();
        boolean distances = CartofoldClientConfig.HUD_DISTANCES.get();
        int width = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        float step = points.isEmpty() ? 0 : Math.min(1, DEPTH / points.size());
        List<Placed> placed = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            HudLayout.Point<Marker> point = points.get(i);
            double[] screen = HudLayout.toScreen(VIEW_PROJECTION, camera.x, camera.y, camera.z, point.x(),
                    point.y() + height, point.z(), width, screenHeight);
            if (screen == null || !HudLayout.onScreen(screen, width, screenHeight, MARGIN * scale)) {
                continue;
            }
            Matrix4f matrix = new Matrix4f(graphics.pose().last().pose())
                    .translate((float) screen[0], (float) screen[1], i * step).scale(scale, scale, 1);
            List<Component> lines = new ArrayList<>(2);
            if (names) {
                lines.add(point.value().name());
            }
            if (distances) {
                lines.add(Component.translatable("cartofold.hud.distance", HudLayout.distanceText(point.distance())));
            }
            placed.add(new Placed(point.value(), matrix, lines));
        }
        return placed;
    }

    /** Draws the backgrounds of the texts and the markers with no icon, in one draw. */
    private static void drawShapes(Font font, List<Placed> placed, float opacity) {
        int background = Math.round(BACKGROUND_OPACITY * opacity * 255) << 24;
        BufferBuilder buffer = MapShapes.begin(VertexFormat.Mode.TRIANGLES);
        for (Placed entry : placed) {
            if (entry.marker().icon() == null) {
                MapShapes.marker(buffer, entry.matrix(), entry.marker().color(), entry.marker().death(), opacity);
            }
            int y = LayerRenderer.ICON_SIZE / 2 + LINE_SPACE;
            for (Component line : entry.lines()) {
                int width = font.width(line);
                int left = -width / 2;
                MapShapes.rectangle(buffer, entry.matrix(), left - 2, y - 1, left + width + 1, y + font.lineHeight,
                        background);
                y += font.lineHeight + LINE_SPACE;
            }
        }
        MapShapes.draw(buffer);
    }

    /** Draws the icons of the markers, in one draw for each texture. */
    private static void drawIcons(List<Placed> placed, float opacity) {
        Map<ResourceLocation, List<Placed>> textures = new LinkedHashMap<>();
        for (Placed entry : placed) {
            ResourceLocation icon = entry.marker().icon();
            if (icon != null) {
                textures.computeIfAbsent(icon, key -> new ArrayList<>()).add(entry);
            }
        }
        if (textures.isEmpty()) {
            return;
        }
        int alpha = Math.round(opacity * 255) << 24;
        float half = LayerRenderer.ICON_SIZE / 2f;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        textures.forEach((texture, entries) -> {
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_TEX_COLOR);
            for (Placed entry : entries) {
                int color = alpha | (entry.marker().tinted() ? entry.marker().color() & 0xFFFFFF : TEXT);
                Matrix4f matrix = entry.matrix();
                buffer.addVertex(matrix, -half, -half, 0).setUv(0, 0).setColor(color);
                buffer.addVertex(matrix, -half, half, 0).setUv(0, 1).setColor(color);
                buffer.addVertex(matrix, half, half, 0).setUv(1, 1).setColor(color);
                buffer.addVertex(matrix, half, -half, 0).setUv(1, 0).setColor(color);
            }
            MeshData mesh = buffer.build();
            if (mesh != null) {
                RenderSystem.setShaderTexture(0, texture);
                BufferUploader.drawWithShader(mesh);
            }
        });
        RenderSystem.disableBlend();
    }

    /** Draws the names and the distances, in one draw. */
    private static void drawTexts(GuiGraphics graphics, Font font, List<Placed> placed, float opacity) {
        int color = (Math.max(Math.round(opacity * 255), 0x10) << 24) | TEXT;
        MultiBufferSource.BufferSource buffers = graphics.bufferSource();
        for (Placed entry : placed) {
            int y = LayerRenderer.ICON_SIZE / 2 + LINE_SPACE;
            for (Component line : entry.lines()) {
                font.drawInBatch(line, -font.width(line) / 2f, y, color, false, entry.matrix(), buffers,
                        Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                y += font.lineHeight + LINE_SPACE;
            }
        }
        // The texts keep the depth test: a far text stays below a near marker.
        buffers.endBatch();
    }

    /** The points of the waypoints and of the markers of a dimension, with their distance to the camera. */
    private static List<HudLayout.Point<Marker>> points(ResourceKey<Level> dimension) {
        List<HudLayout.Point<Marker>> points = new ArrayList<>();
        if (CartofoldClientConfig.HUD_WAYPOINTS.get()) {
            for (Waypoint waypoint : ClientWaypoints.get().in(dimension)) {
                points.add(point(new Marker(WaypointMarkers.name(waypoint), waypoint.color(), null, false,
                        waypoint.death()), waypoint.pos()));
            }
        }
        List<? extends String> hidden = CartofoldClientConfig.HUD_HIDDEN_LAYERS.get();
        for (LayerContent content : ClientLayers.get().contents()) {
            LayerInfo info = content.info();
            if (!LayerRenderer.visible(info, MapViewKind.HUD) || hidden.contains(info.id().toString())) {
                continue;
            }
            Predicate<BlockPos> explored = info.onlyExplored() ? LayerRenderer.exploredBlocks(dimension) : pos -> true;
            for (MapMarker marker : content.markers()) {
                if (marker.dimension().equals(dimension) && explored.test(marker.pos())) {
                    points.add(point(new Marker(marker.name(), marker.color(), marker.icon().orElse(null),
                            marker.tinted(), false), marker.pos()));
                }
            }
        }
        return points;
    }

    private static HudLayout.Point<Marker> point(Marker marker, BlockPos pos) {
        return HudLayout.Point.of(marker, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, camera.x, camera.y,
                camera.z);
    }
}
