package io.github.nicolasgoyon.cartofold.paper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.cartofold.network.SurveyMapPayload;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class SurveyMapDataTest {
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));

    @Test
    void newMapHasTheCenterOfTheGame() {
        // The rules of the vanilla maps: a map of scale 0 at 100, -30 covers the blocks 64 to 191 and -64 to 63.
        SurveyMapData data = SurveyMapData.around(OVERWORLD, 100, -30, 0);
        assertEquals(128, data.centerX());
        assertEquals(0, data.centerZ());
        SurveyMapData zoomed = SurveyMapData.around(OVERWORLD, 100, -30, 2);
        assertEquals(192, zoomed.centerX());
        assertEquals(192, zoomed.centerZ());
    }

    @Test
    void changesIncreaseTheVersion() {
        SurveyMapData data = SurveyMapData.around(OVERWORLD, 0, 0, 0);
        int version = data.version();

        assertTrue(data.setColor(3, 4, (byte) 9));
        assertFalse(data.setColor(3, 4, (byte) 9));
        data.putMarker(new SurveyMarker("a", BlockPos.ZERO, ResourceLocation.withDefaultNamespace("player"), "A",
                ResourceLocation.withDefaultNamespace("test")));

        assertEquals(version + 2, data.version());
        assertTrue(data.removeMarker("a"));
        assertFalse(data.removeMarker("a"));
    }

    @Test
    void lockedCopyKeepsThePixels() {
        SurveyMapData data = SurveyMapData.around(OVERWORLD, 0, 0, 1);
        data.setColor(1, 1, (byte) 5);

        SurveyMapData copy = data.lockedCopy();

        assertTrue(copy.locked());
        assertEquals(5, copy.colors()[1 + 128]);
        assertEquals(data.scale(), copy.scale());
    }

    @Test
    void pixelsStayTheSameInThePayload() {
        byte[] colors = new byte[128 * 128];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = (byte) (i % 7 == 0 ? i : 0);
        }

        byte[] read = SurveyMapPayloadAccess.roundTrip(colors);

        assertArrayEquals(colors, read);
        assertEquals(List.of(), new SurveyMapPayload(1, 1, OVERWORLD, 0, 0, 0, false, colors, List.of()).markers());
    }
}
