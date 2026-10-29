package io.github.nicolasgoyon.cartofold.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.network.SurveyMapPayload;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapId;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapItem;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapLookup;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderItemInFrameEvent;
import org.joml.Matrix4f;

/** The survey maps in the hands of the player and in the item frames, with the poses of the vanilla maps. */
@EventBusSubscriber(modid = Cartofold.MOD_ID, value = Dist.CLIENT)
public final class SurveyMapHands {
    private static final RenderType BACKGROUND =
            RenderType.text(ResourceLocation.withDefaultNamespace("textures/map/map_background.png"));
    private static final RenderType BACKGROUND_CHECKERBOARD =
            RenderType.text(ResourceLocation.withDefaultNamespace("textures/map/map_background_checkerboard.png"));

    private SurveyMapHands() {
    }

    /** Draws a survey map in a hand, with the poses of the vanilla map. */
    public static void render(PoseStack pose, MultiBufferSource buffers, int packedLight, ItemStack stack) {
        pose.mulPose(Axis.YP.rotationDegrees(180.0f));
        pose.mulPose(Axis.ZP.rotationDegrees(180.0f));
        pose.scale(0.38f, 0.38f, 0.38f);
        pose.translate(-0.5f, -0.5f, 0.0f);
        pose.scale(0.0078125f, 0.0078125f, 0.0078125f);
        SurveyMapId id = SurveyMapItem.id(stack);
        SurveyMapPayload map = id == null ? null : SurveyMapLookup.client(id);
        VertexConsumer consumer = buffers.getBuffer(map == null ? BACKGROUND : BACKGROUND_CHECKERBOARD);
        Matrix4f matrix = pose.last().pose();
        consumer.addVertex(matrix, -7.0f, 135.0f, 0.0f).setColor(-1).setUv(0.0f, 1.0f).setLight(packedLight);
        consumer.addVertex(matrix, 135.0f, 135.0f, 0.0f).setColor(-1).setUv(1.0f, 1.0f).setLight(packedLight);
        consumer.addVertex(matrix, 135.0f, -7.0f, 0.0f).setColor(-1).setUv(1.0f, 0.0f).setLight(packedLight);
        consumer.addVertex(matrix, -7.0f, -7.0f, 0.0f).setColor(-1).setUv(0.0f, 0.0f).setLight(packedLight);
        if (map != null) {
            SurveyMapRenderer.render(pose, buffers, map, packedLight, true);
        }
    }

    /** Draws a survey map in an item frame, with the poses of the vanilla map. */
    @SubscribeEvent
    public static void onRenderItemInFrame(RenderItemInFrameEvent event) {
        ItemFrame frame = event.getItemFrameEntity();
        ItemStack stack = frame.getItem();
        SurveyMapId id = SurveyMapItem.id(stack);
        if (id == null) {
            return;
        }
        event.setCanceled(true);
        SurveyMapPayload map = SurveyMapLookup.client(id);
        if (map == null) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        int rotation = frame.getRotation();
        // The frame turns an item by 1/8 of a turn for each rotation, and a map by 1/4 of a turn.
        pose.mulPose(Axis.ZP.rotationDegrees((rotation % 4 * 2 - rotation) * 45.0f));
        pose.mulPose(Axis.ZP.rotationDegrees(180.0f));
        pose.scale(0.0078125f, 0.0078125f, 0.0078125f);
        pose.translate(-64.0f, -64.0f, 0.0f);
        pose.translate(0.0f, 0.0f, -1.0f);
        int light = frame.getType() == EntityType.GLOW_ITEM_FRAME ? 15728850
                : event.getPackedLight();
        SurveyMapRenderer.render(pose, event.getMultiBufferSource(), map, light, false);
    }
}
