package io.github.nicolasgoyon.cartofold.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.nicolasgoyon.cartofold.api.MapViewKind;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.tile.TileImage;
import io.github.nicolasgoyon.cartofold.view.MapProjection;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Draws the map of a dimension in the rectangle of a projection. The minimap, the map of the world and the map frames
 * use this view. The view draws only the explored chunks: the other pixels keep what is under the view. The layers
 * draw above the chunks (see {@link LayerRenderer}).
 */
public final class MapView {
    private MapView() {
    }

    /**
     * Draws the tiles of a dimension in the rectangle of a projection, then the visible layers.
     *
     * @param graphics   the graphics of the screen
     * @param dimension  the dimension
     * @param projection the projection of the view
     * @param kind       the kind of the view, for the visibility of the layers
     */
    public static void render(GuiGraphics graphics, ResourceKey<Level> dimension, MapProjection projection,
            MapViewKind kind) {
        int lod = projection.lod(Minecraft.getInstance().getWindow().getGuiScale());
        int size = TileImage.size(lod);
        graphics.enableScissor((int) Math.floor(projection.x()), (int) Math.floor(projection.y()),
                (int) Math.ceil(projection.x() + projection.width()),
                (int) Math.ceil(projection.y() + projection.height()));
        RenderSystem.enableBlend();
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(projection.screenCenterX(), projection.screenCenterY(), 0);
        pose.mulPose(Axis.ZP.rotation((float) projection.rotation()));
        pose.scale((float) projection.scale(), (float) projection.scale(), 1);
        for (RegionPos pos : projection.regions()) {
            Optional<ResourceLocation> tile = MapTiles.get().tile(dimension, pos, lod);
            if (tile.isEmpty()) {
                continue;
            }
            pose.pushPose();
            // The offset is small: a translation in floats keeps the precision far from the origin of the world.
            pose.translate((float) (pos.minBlockX() - projection.centerX()),
                    (float) (pos.minBlockZ() - projection.centerZ()), 0);
            graphics.blit(tile.get(), 0, 0, MapRegion.SIZE, MapRegion.SIZE, 0, 0, size, size, size, size);
            pose.popPose();
        }
        pose.popPose();
        graphics.flush();
        LayerRenderer.render(graphics, dimension, projection, kind);
        graphics.flush();
        RenderSystem.disableBlend();
        graphics.disableScissor();
    }
}
