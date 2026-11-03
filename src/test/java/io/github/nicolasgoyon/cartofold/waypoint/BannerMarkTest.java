package io.github.nicolasgoyon.cartofold.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.nicolasgoyon.cartofold.api.MapMarker;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class BannerMarkTest {
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("the_nether"));
    private static final BlockPos POS = new BlockPos(1, 64, 2);

    @Test
    void markerHasTheIconOfTheColorAndTheNameAsATooltip() {
        Component name = Component.literal("Home");

        MapMarker marker = new BannerMark(OVERWORLD, POS, DyeColor.LIGHT_BLUE, Optional.of(name)).marker();

        assertEquals(ResourceLocation.withDefaultNamespace("textures/map/decorations/light_blue_banner.png"),
                marker.icon().orElseThrow());
        assertEquals(Optional.of(name), marker.tooltip());
        assertEquals(name, marker.name());
    }

    @Test
    void bannerWithNoNameHasNoTooltipAndAnEmptyName() {
        MapMarker marker = new BannerMark(OVERWORLD, POS, DyeColor.RED, Optional.empty()).marker();

        assertTrue(marker.tooltip().isEmpty());
        assertEquals("", marker.name().getString());
    }

    @Test
    void setReplacesTheMarkAtThePosition() {
        BannerMark red = new BannerMark(OVERWORLD, POS, DyeColor.RED, Optional.empty());
        BannerMark blue = new BannerMark(OVERWORLD, POS, DyeColor.BLUE, Optional.empty());

        assertEquals(List.of(blue), BannerMark.set(List.of(red), blue));
    }

    @Test
    void removeKeepsTheSamePositionOfAnotherDimension() {
        BannerMark overworld = new BannerMark(OVERWORLD, POS, DyeColor.RED, Optional.empty());
        BannerMark nether = new BannerMark(NETHER, POS, DyeColor.RED, Optional.empty());

        assertEquals(List.of(nether), BannerMark.remove(List.of(overworld, nether), OVERWORLD, POS));
        assertFalse(overworld.id().equals(nether.id()));
    }

    @Test
    void codecKeepsTheMark() {
        List<BannerMark> marks = List.of(new BannerMark(OVERWORLD, POS, DyeColor.RED, Optional.empty()),
                new BannerMark(NETHER, POS, DyeColor.BLUE, Optional.of(Component.literal("Home"))));

        JsonElement json = BannerMark.LIST_CODEC.encodeStart(JsonOps.INSTANCE, marks).getOrThrow();

        assertEquals(marks, BannerMark.LIST_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }
}
