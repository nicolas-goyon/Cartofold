package io.github.nicolasgoyon.cartofold.api;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;

/**
 * A kind of block that marks a waypoint on the survey maps, as the banners on the vanilla maps: a player uses a survey
 * map on the block, and the map gets a marker at the block. A second use removes it.
 *
 * @param id     the identifier of the source, in the namespace of its mod
 * @param blocks the blocks of the source
 * @param marker the marker of a block. An empty marker tells that the block does not mark a waypoint now (for example
 *               a village that is not active): the use does nothing, and the maps lose the marker.
 */
public record PaperMarkerSource(ResourceLocation id, Predicate<BlockState> blocks, MarkerFunction marker) {
    /**
     * A marker on a survey map.
     *
     * @param type the map decoration type of the icon
     * @param name the name under the icon, or an empty text for no name
     */
    public record Marker(Holder<MapDecorationType> type, Component name) {
    }

    /** The marker of a block. The server calls it on the server thread, with the chunk loaded. */
    @FunctionalInterface
    public interface MarkerFunction {
        /**
         * The marker of a block.
         *
         * @param level the level of the block
         * @param pos   the position of the block
         * @return the marker, or empty if the block does not mark a waypoint now
         */
        Optional<Marker> marker(ServerLevel level, BlockPos pos);
    }

    /** The name of the waypoint of a block. The server calls it on the server thread, with the chunk loaded. */
    @FunctionalInterface
    public interface NameFunction {
        /**
         * The name of the waypoint of a block.
         *
         * @param level the level of the block
         * @param pos   the position of the block
         * @return the name, or empty if the block does not mark a waypoint now
         */
        Optional<Component> name(ServerLevel level, BlockPos pos);
    }

    /** A source with one icon for all its markers, and a name for each block. */
    public static PaperMarkerSource of(ResourceLocation id, Predicate<BlockState> blocks,
            Holder<MapDecorationType> type, NameFunction name) {
        return new PaperMarkerSource(id, blocks,
                (level, pos) -> name.name(level, pos).map(found -> new Marker(type, found)));
    }
}
