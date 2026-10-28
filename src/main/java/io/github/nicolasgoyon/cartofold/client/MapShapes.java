package io.github.nicolasgoyon.cartofold.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/** Shapes that the map views draw on the screen: the arrow of the player and colored triangles. */
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

    static BufferBuilder begin(VertexFormat.Mode mode) {
        return Tesselator.getInstance().begin(mode, DefaultVertexFormat.POSITION_COLOR);
    }

    static void draw(BufferBuilder buffer) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableCull();
    }
}
