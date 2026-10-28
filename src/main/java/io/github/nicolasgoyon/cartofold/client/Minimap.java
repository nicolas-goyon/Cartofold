package io.github.nicolasgoyon.cartofold.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.nicolasgoyon.cartofold.view.MapProjection;
import io.github.nicolasgoyon.cartofold.view.MinimapLayout;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * The minimap: a layer of the screen of the game. It shows the map around the player, the north, the arrow of the
 * player and the coordinates (see {@link CartofoldClientConfig} for the settings).
 */
public final class Minimap {
    private static final int BACKGROUND = 0xFF101010;
    private static final int BORDER = 0xFF404040;
    private static final int NORTH = 0xFFFF5555;
    private static final int SEGMENTS = 64;
    /** The depth of the mask of the round shape: above the map, so that the map does not go out of the circle. */
    private static final float MASK_DEPTH = 50;

    private Minimap() {
    }

    /** Draws the minimap, if the settings show it. */
    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || minecraft.options.hideGui
                || !CartofoldClientConfig.MINIMAP.get()) {
            return;
        }
        Font font = minecraft.font;
        boolean round = CartofoldClientConfig.MINIMAP_SHAPE.get() == CartofoldClientConfig.Shape.ROUND;
        boolean rotate = CartofoldClientConfig.MINIMAP_ROTATE.get();
        boolean coordinates = CartofoldClientConfig.MINIMAP_COORDINATES.get();
        MinimapLayout.Corner corner = CartofoldClientConfig.MINIMAP_CORNER.get();
        int size = CartofoldClientConfig.MINIMAP_SIZE.get();
        int textHeight = coordinates ? font.lineHeight + 2 : 0;
        int[] position = MinimapLayout.position(corner, size, graphics.guiWidth(), graphics.guiHeight(), textHeight);
        int x = position[0];
        int y = position[1];
        float partialTick = delta.getGameTimeDeltaPartialTick(false);
        float yaw = player.getViewYRot(partialTick);
        MapProjection projection = new MapProjection(x, y, size, size, player.getX(), player.getZ(),
                MinimapLayout.scale(CartofoldClientConfig.MINIMAP_ZOOM.get()),
                MinimapLayout.mapRotation(rotate, yaw));
        float centerX = x + size / 2f;
        float centerY = y + size / 2f;
        float radius = size / 2f;

        graphics.flush();
        if (round) {
            fillDisc(graphics, centerX, centerY, radius + 1, BORDER);
            writeRoundMask(graphics, size, centerX, centerY, radius);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
        } else {
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, BORDER);
        }
        graphics.fill(x, y, x + size, y + size, BACKGROUND);
        graphics.flush();
        MapView.render(graphics, minecraft.level.dimension(), projection);
        graphics.flush();
        WaypointMarkers.renderMinimap(graphics, minecraft.level.dimension(), projection, round);
        if (round) {
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }

        MapShapes.drawArrow(graphics, centerX, centerY, (float) MinimapLayout.arrowRotation(rotate, yaw));
        drawNorth(graphics, font, projection, centerX, centerY, radius, round);
        if (coordinates) {
            Component text = Component.literal(player.getBlockX() + ", " + player.getBlockY() + ", "
                    + player.getBlockZ());
            graphics.drawCenteredString(font, text, (int) centerX, y + size + 3, 0xFFFFFFFF);
        }
    }

    /** The position on the side of the minimap in a direction from the center. */
    private static float[] edge(float centerX, float centerY, float radius, double angle, boolean round) {
        double dx = Math.sin(angle);
        double dy = -Math.cos(angle);
        double reach = round ? radius : radius / Math.max(Math.abs(dx), Math.abs(dy));
        return new float[] {(float) (centerX + dx * reach), (float) (centerY + dy * reach)};
    }

    private static void drawNorth(
            GuiGraphics graphics, Font font, MapProjection projection, float centerX, float centerY, float radius,
            boolean round) {
        float[] point = edge(centerX, centerY, radius - 5, projection.rotation(), round);
        graphics.drawCenteredString(font, "N", Math.round(point[0]), Math.round(point[1] - font.lineHeight / 2f),
                NORTH);
    }

    private static void fillDisc(GuiGraphics graphics, float centerX, float centerY, float radius, int color) {
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = MapShapes.begin(VertexFormat.Mode.TRIANGLES);
        for (int i = 0; i < SEGMENTS; i++) {
            double from = 2 * Math.PI * i / SEGMENTS;
            double to = 2 * Math.PI * (i + 1) / SEGMENTS;
            buffer.addVertex(matrix, centerX, centerY, 0).setColor(color);
            buffer.addVertex(matrix, centerX + (float) Math.sin(to) * radius,
                    centerY - (float) Math.cos(to) * radius, 0).setColor(color);
            buffer.addVertex(matrix, centerX + (float) Math.sin(from) * radius,
                    centerY - (float) Math.cos(from) * radius, 0).setColor(color);
        }
        MapShapes.draw(buffer);
    }

    /**
     * Writes the depth of the part of the square out of the circle, with no color. The next draws at a lower depth
     * stay in the circle.
     */
    private static void writeRoundMask(GuiGraphics graphics, int size, float centerX, float centerY, float radius) {
        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder buffer = MapShapes.begin(VertexFormat.Mode.TRIANGLES);
        for (int i = 0; i < SEGMENTS; i++) {
            double from = 2 * Math.PI * i / SEGMENTS;
            double to = 2 * Math.PI * (i + 1) / SEGMENTS;
            float[] circleFrom = edge(centerX, centerY, radius, from, true);
            float[] circleTo = edge(centerX, centerY, radius, to, true);
            // A point out of the square in the direction of the segment. Only the draws in the square use the mask.
            float[] farFrom = edge(centerX, centerY, size, from, false);
            float[] farTo = edge(centerX, centerY, size, to, false);
            quad(buffer, matrix, circleFrom, farFrom, farTo, circleTo);
        }
        RenderSystem.colorMask(false, false, false, false);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        MapShapes.draw(buffer);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.colorMask(true, true, true, true);
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix, float[] a, float[] b, float[] c, float[] d) {
        buffer.addVertex(matrix, a[0], a[1], MASK_DEPTH).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, b[0], b[1], MASK_DEPTH).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, c[0], c[1], MASK_DEPTH).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, a[0], a[1], MASK_DEPTH).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, c[0], c[1], MASK_DEPTH).setColor(0xFFFFFFFF);
        buffer.addVertex(matrix, d[0], d[1], MASK_DEPTH).setColor(0xFFFFFFFF);
    }
}
