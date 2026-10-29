package io.github.nicolasgoyon.cartofold.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.api.MapViewKind;
import io.github.nicolasgoyon.cartofold.network.PaperMapInfoPayload;
import io.github.nicolasgoyon.cartofold.paper.PaperMapGeometry;
import io.github.nicolasgoyon.cartofold.paper.PaperOverlay;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;

/**
 * The layers on the vanilla maps (the paper maps), in the hand and in the item frames. The vanilla renderer draws the
 * map, then this class draws a texture of the layers above the map and below its decorations (see
 * {@link PaperOverlay}). The server sends the place of each map (see {@link ClientPaperMaps}): a map with no place has
 * no layer.
 *
 * <ul>
 *   <li>The texture of a map changes when its pixels or the layers change. The check of a map runs every
 *       {@link #CHECK_MILLIS} milliseconds at most.</li>
 *   <li>A texture with no use for {@link #IDLE_MILLIS} milliseconds is freed.</li>
 * </ul>
 *
 * <p>Use this class on the render thread only.
 */
@EventBusSubscriber(modid = Cartofold.MOD_ID, value = Dist.CLIENT)
public final class PaperMapOverlays {
    /** The time between two checks of the pixels of a map, in milliseconds. */
    public static final long CHECK_MILLIS = 500;
    /** A texture with no use for this number of milliseconds is freed. */
    public static final long IDLE_MILLIS = 30_000L;
    /** The depth of the layers: above the map (-0.01) and below the decorations (-0.02 and less). */
    private static final float DEPTH = -0.015f;
    private static final int SIZE = PaperMapGeometry.SIZE;

    private static final Map<Integer, Overlay> OVERLAYS = new HashMap<>();

    private PaperMapOverlays() {
    }

    /** The texture of the layers of a map, with the state of its last check. */
    private static final class Overlay {
        final ResourceLocation location;
        final DynamicTexture texture;
        int key;
        boolean empty = true;
        long lastCheck;
        long lastUse;

        Overlay(int id) {
            this.location = ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "paper_map/" + id);
            this.texture = new DynamicTexture(SIZE, SIZE, true);
            Minecraft.getInstance().getTextureManager().register(location, texture);
        }
    }

    /**
     * Draws the layers on a map. The vanilla map renderer calls this method after the map.
     *
     * @param pose        the pose of the map: the map is 128 x 128 units from the origin
     * @param buffers     the buffers of the frame
     * @param id          the id of the map
     * @param data        the data of the map
     * @param packedLight the light of the map
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, MapId id, MapItemSavedData data,
            int packedLight) {
        Optional<PaperMapInfoPayload> info = ClientPaperMaps.get(id.id());
        if (info.isEmpty()) {
            return;
        }
        long now = Util.getMillis();
        Overlay overlay = OVERLAYS.computeIfAbsent(id.id(), Overlay::new);
        overlay.lastUse = now;
        if (now - overlay.lastCheck >= CHECK_MILLIS) {
            overlay.lastCheck = now;
            List<LayerContent> layers = ClientLayers.get().contents().stream()
                    .filter(content -> LayerRenderer.visible(content.info(), MapViewKind.PAPER)).toList();
            int key = Objects.hash(ClientLayers.get().version(), CartofoldClientConfig.HIDDEN_LAYERS.get(),
                    Arrays.hashCode(data.colors), info.get());
            if (key != overlay.key) {
                overlay.key = key;
                draw(overlay, info.get(), data.colors, layers);
            }
        }
        if (overlay.empty) {
            return;
        }
        Matrix4f matrix = pose.last().pose();
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(overlay.location));
        consumer.addVertex(matrix, 0, SIZE, DEPTH).setColor(-1).setUv(0, 1).setLight(packedLight);
        consumer.addVertex(matrix, SIZE, SIZE, DEPTH).setColor(-1).setUv(1, 1).setLight(packedLight);
        consumer.addVertex(matrix, SIZE, 0, DEPTH).setColor(-1).setUv(1, 0).setLight(packedLight);
        consumer.addVertex(matrix, 0, 0, DEPTH).setColor(-1).setUv(0, 0).setLight(packedLight);
    }

    private static void draw(Overlay overlay, PaperMapInfoPayload info, byte[] colors, List<LayerContent> layers) {
        NativeImage image = overlay.texture.getPixels();
        if (image == null) {
            return;
        }
        if (layers.isEmpty()) {
            overlay.empty = true;
            return;
        }
        int[] pixels = PaperOverlay.render(new PaperMapGeometry(info.centerX(), info.centerZ(), info.scale()),
                info.dimension(), colors, layers);
        boolean empty = true;
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int pixel = pixels[x + z * SIZE];
                image.setPixelRGBA(x, z, pixel);
                empty &= pixel == 0;
            }
        }
        overlay.empty = empty;
        if (!empty) {
            overlay.texture.upload();
        }
    }

    private static void release(Overlay overlay) {
        Minecraft.getInstance().getTextureManager().release(overlay.location);
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        long now = Util.getMillis();
        OVERLAYS.values().removeIf(overlay -> {
            boolean idle = now - overlay.lastUse >= IDLE_MILLIS;
            if (idle) {
                release(overlay);
            }
            return idle;
        });
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        OVERLAYS.values().forEach(PaperMapOverlays::release);
        OVERLAYS.clear();
        ClientPaperMaps.clear();
    }
}
