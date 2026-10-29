package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.network.SurveyMapPayload;
import io.github.nicolasgoyon.cartofold.paper.CartofoldItems;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapData;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapId;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapItem;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapLookup;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The preview of a survey map in the cartography table, at the places of the vanilla preview. */
public final class SurveyMapPreview {
    private static final ResourceLocation ERROR = sprite("error");
    private static final ResourceLocation SCALED_MAP = sprite("scaled_map");
    private static final ResourceLocation DUPLICATED_MAP = sprite("duplicated_map");
    private static final ResourceLocation MAP = sprite("map");
    private static final ResourceLocation LOCKED = sprite("locked");
    private static final int FULL_LIGHT = 15728880;

    private SurveyMapPreview() {
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.withDefaultNamespace("container/cartography_table/" + name);
    }

    /**
     * Draws the preview.
     *
     * @param graphics the graphics of the screen
     * @param left     the left side of the screen of the table
     * @param top      the top side of the screen of the table
     * @param stack    the survey map in the first slot
     * @param input    the item in the second slot
     */
    public static void render(GuiGraphics graphics, int left, int top, ItemStack stack, ItemStack input) {
        SurveyMapId id = SurveyMapItem.id(stack);
        SurveyMapPayload map = id == null ? null : SurveyMapLookup.client(id);
        boolean paper = input.is(Items.PAPER);
        boolean copy = input.is(CartofoldItems.EMPTY_SURVEY_MAP.get());
        boolean glass = input.is(Items.GLASS_PANE);
        boolean locked = map != null && map.locked();
        boolean maxScale = map != null && map.scale() >= SurveyMapData.MAX_SCALE;
        if ((locked && (paper || glass)) || (paper && maxScale)) {
            graphics.blitSprite(ERROR, left + 35, top + 31, 28, 21);
        }
        if (paper && !maxScale && !locked) {
            graphics.blitSprite(SCALED_MAP, left + 67, top + 13, 66, 66);
            draw(graphics, map, left + 85, top + 31, 0.226f);
        } else if (copy) {
            graphics.blitSprite(DUPLICATED_MAP, left + 67 + 16, top + 13, 50, 66);
            draw(graphics, map, left + 86, top + 16, 0.34f);
            graphics.pose().pushPose();
            graphics.pose().translate(0.0f, 0.0f, 1.0f);
            graphics.blitSprite(DUPLICATED_MAP, left + 67, top + 13 + 16, 50, 66);
            draw(graphics, map, left + 70, top + 32, 0.34f);
            graphics.pose().popPose();
        } else {
            graphics.blitSprite(MAP, left + 67, top + 13, 66, 66);
            draw(graphics, map, left + 71, top + 17, 0.45f);
            if (glass && !locked) {
                graphics.pose().pushPose();
                graphics.pose().translate(0.0f, 0.0f, 1.0f);
                graphics.blitSprite(LOCKED, left + 118, top + 60, 10, 14);
                graphics.pose().popPose();
            }
        }
    }

    private static void draw(GuiGraphics graphics, @Nullable SurveyMapPayload map, int x, int y, float scale) {
        if (map == null) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 1.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        SurveyMapRenderer.render(graphics.pose(), graphics.bufferSource(), map, FULL_LIGHT, false);
        graphics.flush();
        graphics.pose().popPose();
    }
}
