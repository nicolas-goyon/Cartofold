package io.github.nicolasgoyon.cartofold.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.cartofold.data.RegionPos;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExplorationRequestPayloadTest {
    @Test
    void combineKeepsTheFirstRegionsAndRemovesTheDuplicates() {
        List<RegionPos> regions = ExplorationRequestPayload.combine(
                List.of(new RegionPos(0, 0), new RegionPos(1, 0)), List.of(new RegionPos(1, 0), new RegionPos(5, 5)));

        assertEquals(List.of(new RegionPos(0, 0), new RegionPos(1, 0), new RegionPos(5, 5)), regions);
    }

    @Test
    void combineKeepsTheRegionsNearTheCenterOfTheView() {
        List<RegionPos> view = new ArrayList<>();
        for (int z = -10; z <= 10; z++) {
            for (int x = -10; x <= 10; x++) {
                view.add(new RegionPos(x + 100, z));
            }
        }

        List<RegionPos> regions = ExplorationRequestPayload.combine(List.of(new RegionPos(0, 0)), view);

        assertEquals(ExplorationRequestPayload.MAX_REGIONS, regions.size());
        assertEquals(new RegionPos(0, 0), regions.get(0));
        assertTrue(regions.contains(new RegionPos(100, 0)));
        assertFalse(regions.contains(new RegionPos(90, -10)));
    }
}
