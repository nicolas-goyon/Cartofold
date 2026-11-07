package io.github.nicolasgoyon.cartofold.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.cartofold.paper.SurveyTableOutcome.Input;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class SurveyTableOutcomeTest {
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));

    @Test
    void paperFollowsTheButton() {
        assertEquals(SurveyTableOutcome.ZOOM, SurveyTableOutcome.of(Input.PAPER, false, 0, MapMoves.NONE));
        assertEquals(SurveyTableOutcome.MOVE_NORTH, SurveyTableOutcome.of(Input.PAPER, false, 0, MapMoves.NORTH));
        assertEquals(SurveyTableOutcome.MOVE_SOUTH, SurveyTableOutcome.of(Input.PAPER, false, 0, MapMoves.SOUTH));
        assertEquals(SurveyTableOutcome.MOVE_WEST, SurveyTableOutcome.of(Input.PAPER, false, 0, MapMoves.WEST));
        assertEquals(SurveyTableOutcome.MOVE_EAST, SurveyTableOutcome.of(Input.PAPER, false, 0, MapMoves.EAST));
        assertEquals(SurveyTableOutcome.UPDATE, SurveyTableOutcome.of(Input.PAPER, false, 0, MapMoves.UPDATE));
        assertEquals(SurveyMapAction.MOVE_EAST, SurveyTableOutcome.MOVE_EAST.action());
    }

    /** The largest scale does not zoom out, but it moves and updates. */
    @Test
    void largestScaleDoesNotZoom() {
        int max = SurveyMapData.MAX_SCALE;
        assertEquals(SurveyTableOutcome.MAX_SCALE, SurveyTableOutcome.of(Input.PAPER, false, max, MapMoves.NONE));
        assertEquals(SurveyTableOutcome.MOVE_WEST, SurveyTableOutcome.of(Input.PAPER, false, max, MapMoves.WEST));
        assertEquals(SurveyTableOutcome.UPDATE, SurveyTableOutcome.of(Input.PAPER, false, max, MapMoves.UPDATE));
        assertFalse(SurveyTableOutcome.MAX_SCALE.hasResult());
    }

    /** A locked map does not zoom, move, update or lock again, but it gives copies. */
    @Test
    void lockedMapOnlyCopies() {
        assertEquals(SurveyTableOutcome.LOCKED, SurveyTableOutcome.of(Input.PAPER, true, 0, MapMoves.NONE));
        assertEquals(SurveyTableOutcome.LOCKED, SurveyTableOutcome.of(Input.PAPER, true, 0, MapMoves.EAST));
        assertEquals(SurveyTableOutcome.LOCKED, SurveyTableOutcome.of(Input.PAPER, true, 0, MapMoves.UPDATE));
        assertEquals(SurveyTableOutcome.LOCKED, SurveyTableOutcome.of(Input.GLASS_PANE, true, 0, MapMoves.NONE));
        assertEquals(SurveyTableOutcome.COPY, SurveyTableOutcome.of(Input.EMPTY_SURVEY_MAP, true, 0,
                MapMoves.NONE));
        assertFalse(SurveyTableOutcome.LOCKED.hasResult());
    }

    @Test
    void otherItemsIgnoreTheButton() {
        assertEquals(SurveyTableOutcome.LOCK, SurveyTableOutcome.of(Input.GLASS_PANE, false, 0, MapMoves.EAST));
        assertEquals(SurveyTableOutcome.COPY, SurveyTableOutcome.of(Input.EMPTY_SURVEY_MAP, false, 0,
                MapMoves.UPDATE));
        assertEquals(SurveyTableOutcome.NO_INPUT, SurveyTableOutcome.of(Input.NONE, false, 0, MapMoves.NONE));
        assertEquals(SurveyTableOutcome.WRONG_INPUT, SurveyTableOutcome.of(Input.OTHER, false, 0, MapMoves.NONE));
        assertTrue(SurveyTableOutcome.COPY.hasResult());
        assertNull(SurveyTableOutcome.COPY.action());
    }

    /** A zoom puts the old map in a quarter of the new map, on the grid of the game. */
    @Test
    void zoomPutsTheMapInAQuarter() {
        PaperMapGeometry map = SurveyMapData.around(OVERWORLD, 0, 0, 0).geometry();
        PaperMapGeometry zoomed = SurveyMapTable.zoomed(map);
        assertEquals(1, zoomed.scale());
        assertEquals(0, zoomed.pixelX(map.blockX(0)));
        assertEquals(0, zoomed.pixelZ(map.blockZ(0)));

        PaperMapGeometry east = SurveyMapData.around(OVERWORLD, 100, 0, 0).geometry();
        assertEquals(64, SurveyMapTable.zoomed(east).pixelX(east.blockX(0)));
        PaperMapGeometry max = SurveyMapData.around(OVERWORLD, 0, 0, SurveyMapData.MAX_SCALE).geometry();
        assertEquals(SurveyMapData.MAX_SCALE, SurveyMapTable.zoomed(max).scale());
    }

    /** The number of chunks of a map, and the limit of an update. */
    @Test
    void chunksOfAnUpdate() {
        SurveyMapData small = SurveyMapData.around(OVERWORLD, 0, 0, 0);
        assertEquals(64, MapUpdates.chunkCount(small.geometry()));
        assertEquals(64, MapUpdates.chunks(small).size());
        SurveyMapData large = SurveyMapData.around(OVERWORLD, 0, 0, SurveyMapData.MAX_SCALE);
        assertEquals(128 * 128, MapUpdates.chunkCount(large.geometry()));
        assertEquals(MapUpdates.LIMIT, MapUpdates.chunks(large).size());
    }
}
