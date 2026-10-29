package io.github.nicolasgoyon.cartofold.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nicolasgoyon.cartofold.api.ChunkArea;
import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.api.LayerInfo;
import io.github.nicolasgoyon.cartofold.api.MapLine;
import io.github.nicolasgoyon.cartofold.api.MapMarker;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class PaperOverlayTest {
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"));
    /** A map of scale 0 around 64, 64: the pixel (x, z) is the block (x, z). */
    private static final PaperMapGeometry GEOMETRY = new PaperMapGeometry(64, 64, 0);
    private static final int FILL = 0xFF00FF00;
    private static final int BORDER = 0xFFFF0000;

    private static byte[] explored() {
        byte[] colors = new byte[128 * 128];
        Arrays.fill(colors, (byte) 4);
        return colors;
    }

    private static LayerContent layer(List<ChunkArea> areas, List<MapLine> lines, List<MapMarker> markers) {
        return new LayerContent(LayerInfo.of(ResourceLocation.fromNamespaceAndPath("test", "layer"),
                Component.literal("Layer"), 0, true), areas, lines, markers);
    }

    /** The ABGR of an ARGB color. */
    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }

    @Test
    void geometryFollowsTheGame() {
        PaperMapGeometry geometry = new PaperMapGeometry(-100, 300, 2);

        assertEquals((-100 / 4 - 64) * 4, geometry.blockX(0));
        assertEquals(10, geometry.pixelX(geometry.blockX(10) + 3));
        assertEquals(-1, geometry.pixelZ(geometry.blockZ(0) - 1));
    }

    @Test
    void areaFillsItsChunkAndDrawsItsBorder() {
        ChunkArea area = ChunkArea.of("a", OVERWORLD, List.of(new ChunkPos(1, 1)), FILL, BORDER,
                Component.literal("A"));

        int[] pixels = PaperOverlay.render(GEOMETRY, OVERWORLD, explored(), List.of(layer(List.of(area), List.of(),
                List.of())));

        assertEquals(abgr(FILL), pixels[20 + 20 * 128]);
        assertEquals(abgr(BORDER), pixels[16 + 20 * 128]);
        assertEquals(abgr(BORDER), pixels[31 + 20 * 128]);
        assertEquals(0, pixels[32 + 20 * 128]);
    }

    @Test
    void layersShowOnlyOnTheExploredPixels() {
        ChunkArea area = ChunkArea.of("a", OVERWORLD, List.of(new ChunkPos(1, 1)), FILL, BORDER,
                Component.literal("A"));
        byte[] colors = explored();
        colors[20 + 20 * 128] = 0;

        int[] pixels = PaperOverlay.render(GEOMETRY, OVERWORLD, colors, List.of(layer(List.of(area), List.of(),
                List.of())));

        assertEquals(0, pixels[20 + 20 * 128]);
        assertEquals(abgr(FILL), pixels[21 + 20 * 128]);
    }

    @Test
    void lineAndMarkerDrawTheirPixels() {
        MapLine line = new MapLine("l", OVERWORLD, List.of(new BlockPos(10, 0, 50), new BlockPos(20, 0, 50)),
                0xFF0000FF, 1);
        MapMarker marker = MapMarker.of("m", OVERWORLD, new BlockPos(100, 64, 100), 0xFFFF00, Component.literal("M"));

        int[] pixels = PaperOverlay.render(GEOMETRY, OVERWORLD, explored(), List.of(layer(List.of(), List.of(line),
                List.of(marker))));

        assertEquals(abgr(0xFF0000FF), pixels[15 + 50 * 128]);
        assertEquals(0, pixels[21 + 50 * 128]);
        assertEquals(abgr(0xFFFFFF00), pixels[101 + 100 * 128]);
    }

    @Test
    void layerOfAnotherDimensionDrawsNothing() {
        ChunkArea area = ChunkArea.of("a", NETHER, List.of(new ChunkPos(1, 1)), FILL, BORDER, Component.literal("A"));

        int[] pixels = PaperOverlay.render(GEOMETRY, OVERWORLD, explored(), List.of(layer(List.of(area), List.of(),
                List.of())));

        assertEquals(0, Arrays.stream(pixels).filter(pixel -> pixel != 0).count());
    }

    @Test
    void halfTransparentColorsMix() {
        int mixed = PaperOverlay.blend(0xFFFF0000, 0x800000FF);

        assertEquals(0xFF, mixed >>> 24);
        assertEquals(0x7F, (mixed >> 16) & 0xFF);
        assertEquals(0x80, mixed & 0xFF);
    }
}
