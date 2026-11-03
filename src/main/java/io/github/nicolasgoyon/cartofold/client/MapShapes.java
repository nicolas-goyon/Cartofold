package io.github.nicolasgoyon.cartofold.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/** Shapes that the map views draw on the screen: the arrow of the player and the markers of the waypoints. */
public final class MapShapes {
    private static final int ARROW = 0xFFFFFFFF;
    private static final int ARROW_OUTLINE = 0xFF000000;

    private MapShapes() {
    }

    /** Draws the arrow of the player at a point, turned clockwise by an angle. With an angle of 0, it points to the top. */
    public static void drawArrow(GuiGraphics graphics, float x, float y, float angle) {
        Matrix4f matrix = new Matrix4f(graphics.pose().last().pose()).translate(x, y, 0)
                .rotateZ(angle);
        BufferBuilder buffer = begin(VertexFormat.Mode.TRIANGLES);
        arrow(buffer, matrix, 1.6f, ARROW_OUTLINE);
        arrow(buffer, matrix, 1f, ARROW);
        draw(buffer);
    }

    private static void arrow(BufferBuilder buffer, Matrix4f matrix, float grow, int color) {
        float tip = -5f * grow;
        float side = 3.5f * grow;
        float back = 4f * grow;
        float notch = 1.5f * grow;
        buffer.addVertex(matrix, 0, tip, 0).setColor(color);
        buffer.addVertex(matrix, -side, back, 0).setColor(color);
        buffer.addVertex(matrix, 0, notch, 0).setColor(color);
        buffer.addVertex(matrix, 0, tip, 0).setColor(color);
        buffer.addVertex(matrix, 0, notch, 0).setColor(color);
        buffer.addVertex(matrix, side, back, 0).setColor(color);
    }

    /**
     * Draws the marker of a waypoint at a point: a diamond, or a square for the waypoint of a death.
     *
     * @param color the color, in RGB
     */
    public static void drawMarker(GuiGraphics graphics, float x, float y, int color, boolean death) {
        Matrix4f matrix = new Matrix4f(graphics.pose().last().pose()).translate(x, y, 0);
        if (!death) {
            matrix.rotateZ((float) (Math.PI / 4));
        }
        BufferBuilder buffer = begin(VertexFormat.Mode.TRIANGLES);
        square(buffer, matrix, 4f, ARROW_OUTLINE);
        square(buffer, matrix, 2.8f, 0xFF000000 | color);
        draw(buffer);
    }

    private static void square(BufferBuilder buffer, Matrix4f matrix, float half, int color) {
        buffer.addVertex(matrix, -half, -half, 0).setColor(color);
        buffer.addVertex(matrix, -half, half, 0).setColor(color);
        buffer.addVertex(matrix, half, half, 0).setColor(color);
        buffer.addVertex(matrix, -half, -half, 0).setColor(color);
        buffer.addVertex(matrix, half, half, 0).setColor(color);
        buffer.addVertex(matrix, half, -half, 0).setColor(color);
    }

    /** Adds a quad of four points (in order around the quad) to a buffer of triangles. */
    static void quad(BufferBuilder buffer, Matrix4f matrix, double[] a, double[] b, double[] c, double[] d,
            int color) {
        buffer.addVertex(matrix, (float) a[0], (float) a[1], 0).setColor(color);
        buffer.addVertex(matrix, (float) b[0], (float) b[1], 0).setColor(color);
        buffer.addVertex(matrix, (float) c[0], (float) c[1], 0).setColor(color);
        buffer.addVertex(matrix, (float) a[0], (float) a[1], 0).setColor(color);
        buffer.addVertex(matrix, (float) c[0], (float) c[1], 0).setColor(color);
        buffer.addVertex(matrix, (float) d[0], (float) d[1], 0).setColor(color);
    }

    /** Adds a line with a width between two points to a buffer of triangles. */
    static void line(BufferBuilder buffer, Matrix4f matrix, double[] from, double[] to, float width, int color) {
        double dx = to[0] - from[0];
        double dy = to[1] - from[1];
        double length = Math.hypot(dx, dy);
        if (length == 0) {
            return;
        }
        double nx = -dy / length * width / 2;
        double ny = dx / length * width / 2;
        quad(buffer, matrix, new double[] {from[0] + nx, from[1] + ny}, new double[] {to[0] + nx, to[1] + ny},
                new double[] {to[0] - nx, to[1] - ny}, new double[] {from[0] - nx, from[1] - ny}, color);
    }

    /** A new buffer of colored vertices. */
    static BufferBuilder begin(VertexFormat.Mode mode) {
        return Tesselator.getInstance().begin(mode, DefaultVertexFormat.POSITION_COLOR);
    }

    /** Draws a buffer of colored vertices, if it has vertices. */
    static void draw(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
    }
}
