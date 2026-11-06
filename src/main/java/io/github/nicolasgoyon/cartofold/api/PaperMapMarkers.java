package io.github.nicolasgoyon.cartofold.api;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.paper.PaperWaypoints;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The entry point of the waypoints of blocks on the survey maps (the paper maps of Cartofold).
 *
 * <pre>{@code
 * PaperMapMarkers.register(PaperMarkerSource.of(
 *         ResourceLocation.fromNamespaceAndPath("mymod", "village_heart"),
 *         state -> state.is(MyBlocks.VILLAGE_HEART.get()),
 *         MyMapDecorations.VILLAGE,
 *         (level, pos) -> villageAt(level, pos).map(village -> Component.literal(village.name()))));
 * }</pre>
 *
 * <p>Cartofold has a source for the banners (the icons and the names of the vanilla banners) and for the lodestone
 * (the decoration type {@code cartofold:waypoint}). Other mods can use this type for their own sources.
 */
public final class PaperMapMarkers {
    /**
     * The items of the survey maps. A block of a mod with its own use action does not need this tag: Cartofold gives the
     * use of a survey map on a block of a source to the map, before the block (see {@link #register}).
     */
    public static final TagKey<Item> SURVEY_MAPS =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "survey_maps"));

    private PaperMapMarkers() {
    }

    /** True if an item is a survey map (the tag {@link #SURVEY_MAPS}). */
    public static boolean isSurveyMap(ItemStack stack) {
        return stack.is(SURVEY_MAPS);
    }

    /**
     * Adds a source of waypoints. Call this method once, for example in the common setup. A source with the identifier
     * of another source replaces it. A survey map used on a block of a source marks the map, also when the block has
     * its own use action (for example a block that opens a screen): the block does not get this use.
     */
    public static void register(PaperMarkerSource source) {
        PaperWaypoints.register(source);
    }
}
