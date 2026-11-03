package io.github.nicolasgoyon.cartofold.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.api.MapViewKind;
import io.github.nicolasgoyon.cartofold.network.SurveyMapPayload;
import io.github.nicolasgoyon.cartofold.paper.PaperMapGeometry;
import io.github.nicolasgoyon.cartofold.paper.PaperOverlay;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapLookup;
import io.github.nicolasgoyon.cartofold.paper.SurveyMarker;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.joml.Matrix4f;

/**
 * Draws a survey map: its pixels and its layers in one texture, then its markers and the player.
 *
 * <ul>
 *   <li>The map is 128 x 128 units from the origin of the pose, as a vanilla map.</li>
 *   <li>The texture of a map changes when the server sends new data of the map, or when the layers change. A texture
 *       with no use for {@link #IDLE_MILLIS} milliseconds is freed.</li>
 *   <li>The layers show only on the explored pixels (see {@link PaperOverlay}).</li>
 * </ul>
 *
 * <p>Use this class on the render thread only.
 */
@EventBusSubscriber(modid = Cartofold.MOD_ID, value = Dist.CLIENT)
public final class SurveyMapRenderer {
    /** A texture with no use for this number of milliseconds is freed. */
    public static final long IDLE_MILLIS = 30_000L;
    private static final int SIZE = PaperMapGeometry.SIZE;
    private static final Map<Integer, Texture> TEXTURES = new HashMap<>();

    private SurveyMapRenderer() {
    }

    /** The texture of a map, with the data that it shows. */
    private static final class Texture {
        final ResourceLocation location;
        final DynamicTexture texture;
        int key;
        long lastUse;

        Texture(int id) {
            this.location = ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "survey_map/" + id);
            this.texture = new DynamicTexture(SIZE, SIZE, true);
            Minecraft.getInstance().getTextureManager().register(location, texture);
        }
    }

    /**
     * Draws a map.
     *
     * @param pose        the pose of the map: the map is 128 x 128 units from the origin
     * @param buffers     the buffers of the frame
     * @param map         the data of the map
     * @param packedLight the light of the map
     * @param showPlayer  true to show the player that holds the map
     */
    public static void render(PoseStack pose, MultiBufferSource buffers, SurveyMapPayload map, int packedLight,
            boolean showPlayer) {
        Texture texture = TEXTURES.computeIfAbsent(map.id(), Texture::new);
        texture.lastUse = Util.getMillis();
        List<LayerContent> layers = ClientLayers.get().contents().stream()
                .filter(content -> LayerRenderer.visible(content.info(), MapViewKind.PAPER)).toList();
        int key = Objects.hash(System.identityHashCode(map), ClientLayers.get().version(),
                CartofoldClientConfig.HIDDEN_LAYERS.get());
        if (key != texture.key) {
            texture.key = key;
            draw(texture, map, layers);
        }
        Matrix4f matrix = pose.last().pose();
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(texture.location));
        consumer.addVertex(matrix, 0, SIZE, -0.01f).setColor(-1).setUv(0, 1).setLight(packedLight);
        consumer.addVertex(matrix, SIZE, SIZE, -0.01f).setColor(-1).setUv(1, 1).setLight(packedLight);
        consumer.addVertex(matrix, SIZE, 0, -0.01f).setColor(-1).setUv(1, 0).setLight(packedLight);
        consumer.addVertex(matrix, 0, 0, -0.01f).setColor(-1).setUv(0, 0).setLight(packedLight);
        int index = 0;
        for (SurveyMarker marker : map.markers()) {
            Optional<Holder.Reference<MapDecorationType>> type = decorationType(marker.type());
            if (type.isPresent()) {
                PaperMapGeometry geometry = new PaperMapGeometry(map.centerX(), map.centerZ(), map.scale());
                byte x = decorationCoordinate(marker.pos().getX() + 0.5, map.centerX(), geometry.factor());
                byte z = decorationCoordinate(marker.pos().getZ() + 0.5, map.centerZ(), geometry.factor());
                Optional<Component> name = marker.name().isEmpty() ? Optional.empty()
                        : Optional.of(Component.literal(marker.name()));
                drawDecoration(pose, buffers, new MapDecoration(type.get(), x, z, (byte) 8, name), packedLight,
                        index++);
            }
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (showPlayer && player != null && player.level().dimension().equals(map.dimension())) {
            PaperMapGeometry geometry = new PaperMapGeometry(map.centerX(), map.centerZ(), map.scale());
            double dx = (player.getX() - map.centerX()) / geometry.factor() * 2;
            double dz = (player.getZ() - map.centerZ()) / geometry.factor() * 2;
            boolean inside = Math.abs(dx) < 128 && Math.abs(dz) < 128;
            byte x = (byte) Mth.clamp(Math.round(dx), -128, 127);
            byte z = (byte) Mth.clamp(Math.round(dz), -128, 127);
            byte rot = (byte) (inside ? Math.round(player.getYRot() * 16 / 360.0) & 15 : 0);
            drawDecoration(pose, buffers, new MapDecoration(inside ? MapDecorationTypes.PLAYER
                    : MapDecorationTypes.PLAYER_OFF_MAP, x, z, rot, Optional.empty()), packedLight, index);
        }
    }

    private static byte decorationCoordinate(double block, int center, int factor) {
        return (byte) Mth.clamp(Math.round((block - center) / factor * 2), -128, 127);
    }

    private static Optional<Holder.Reference<MapDecorationType>> decorationType(ResourceLocation id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return Optional.empty();
        }
        return minecraft.level.registryAccess().registryOrThrow(Registries.MAP_DECORATION_TYPE)
                .getHolder(ResourceKey.create(Registries.MAP_DECORATION_TYPE, id));
    }

    /** Draws a decoration as the vanilla maps: its icon, then its name under it. */
    private static void drawDecoration(PoseStack pose, MultiBufferSource buffers, MapDecoration decoration,
            int packedLight, int index) {
        Minecraft minecraft = Minecraft.getInstance();
        pose.pushPose();
        pose.translate(decoration.x() / 2.0f + 64.0f, decoration.y() / 2.0f + 64.0f, -0.02f);
        pose.mulPose(Axis.ZP.rotationDegrees(decoration.rot() * 360 / 16.0f));
        pose.scale(4.0f, 4.0f, 3.0f);
        pose.translate(-0.125f, 0.125f, 0.0f);
        TextureAtlasSprite sprite = minecraft.getMapDecorationTextures().get(decoration);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer consumer = buffers.getBuffer(RenderType.text(sprite.atlasLocation()));
        float depth = index * -0.001f;
        consumer.addVertex(matrix, -1, 1, depth).setColor(-1).setUv(sprite.getU0(), sprite.getV0())
                .setLight(packedLight);
        consumer.addVertex(matrix, 1, 1, depth).setColor(-1).setUv(sprite.getU1(), sprite.getV0())
                .setLight(packedLight);
        consumer.addVertex(matrix, 1, -1, depth).setColor(-1).setUv(sprite.getU1(), sprite.getV1())
                .setLight(packedLight);
        consumer.addVertex(matrix, -1, -1, depth).setColor(-1).setUv(sprite.getU0(), sprite.getV1())
                .setLight(packedLight);
        pose.popPose();
        if (decoration.name().isPresent()) {
            Font font = minecraft.font;
            Component name = decoration.name().get();
            float width = font.width(name);
            float scale = Mth.clamp(25.0f / width, 0.0f, 6.0f / 9.0f);
            pose.pushPose();
            pose.translate(decoration.x() / 2.0f + 64.0f - width * scale / 2.0f, decoration.y() / 2.0f + 64.0f + 4.0f,
                    -0.025f);
            pose.scale(scale, scale, 1.0f);
            pose.translate(0.0f, 0.0f, -0.1f);
            font.drawInBatch(name, 0.0f, 0.0f, -1, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL,
                    Integer.MIN_VALUE, packedLight);
            pose.popPose();
        }
    }

    private static void draw(Texture texture, SurveyMapPayload map, List<LayerContent> layers) {
        NativeImage image = texture.texture.getPixels();
        if (image == null) {
            return;
        }
        int[] overlay = layers.isEmpty() ? null : PaperOverlay.render(new PaperMapGeometry(map.centerX(),
                map.centerZ(), map.scale()), map.dimension(), map.colors(), layers);
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                int index = x + z * SIZE;
                int packed = map.colors()[index] & 0xFF;
                int base = packed == 0 ? 0 : MapColor.getColorFromPackedId(packed);
                image.setPixelRGBA(x, z, overlay == null ? base : over(base, overlay[index]));
            }
        }
        texture.texture.upload();
    }

    /** A color in ABGR above an opaque or transparent color in ABGR. */
    private static int over(int below, int above) {
        int alpha = above >>> 24;
        if (alpha == 0) {
            return below;
        }
        if (alpha == 0xFF || below == 0) {
            return above;
        }
        int mixed = 0xFF000000;
        for (int shift = 0; shift < 24; shift += 8) {
            int channel = (((above >> shift) & 0xFF) * alpha + ((below >> shift) & 0xFF) * (255 - alpha)) / 255;
            mixed |= channel << shift;
        }
        return mixed;
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Post event) {
        long now = Util.getMillis();
        TEXTURES.values().removeIf(texture -> {
            boolean idle = now - texture.lastUse >= IDLE_MILLIS;
            if (idle) {
                Minecraft.getInstance().getTextureManager().release(texture.location);
            }
            return idle;
        });
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TEXTURES.values().forEach(texture -> Minecraft.getInstance().getTextureManager().release(texture.location));
        TEXTURES.clear();
        SurveyMapLookup.clear();
    }
}
