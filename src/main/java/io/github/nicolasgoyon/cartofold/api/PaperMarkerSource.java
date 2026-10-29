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
 * A kind of block that marks a waypoint on the paper maps, as the banners of the game: a player uses a filled map on
 * the block, and the map gets a decoration at the block. A second use removes it.
 *
 * @param id     the identifier of the source, in the namespace of its mod
 * @param blocks the blocks of the source
 * @param type   the decoration on the map
 * @param name   the name of the waypoint of a block. An empty name tells that the block does not mark a waypoint now
 *               (for example a village that is not active): the use does nothing, and the map loses the waypoint.
 */
public record PaperMarkerSource(ResourceLocation id, Predicate<BlockState> blocks, Holder<MapDecorationType> type,
        NameFunction name) {
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
}
