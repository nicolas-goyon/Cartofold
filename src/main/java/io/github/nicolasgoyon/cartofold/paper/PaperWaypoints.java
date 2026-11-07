package io.github.nicolasgoyon.cartofold.paper;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.api.PaperMapMarkers;
import io.github.nicolasgoyon.cartofold.api.PaperMarkerSource;
import io.github.nicolasgoyon.cartofold.server.BannerService;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The waypoints of blocks on the survey maps, as the banners on the vanilla maps (see
 * {@link io.github.nicolasgoyon.cartofold.api.PaperMapMarkers}).
 *
 * <ul>
 *   <li>A player uses a survey map on a block of a source, in the area of the map: the map gets a marker at the block.
 *       A second use removes the marker. A locked map does not change.</li>
 *   <li>When the server sends a map and the chunk of a marker is loaded, it checks the block of the marker: a block
 *       that is not of the source any more, or a source with no marker for the block, removes the marker. The icon and
 *       the name follow the source.</li>
 * </ul>
 */
public final class PaperWaypoints {
    private static final Map<ResourceLocation, PaperMarkerSource> SOURCES = new ConcurrentHashMap<>();

    private PaperWaypoints() {
    }

    /** Adds a source of waypoints. */
    public static void register(PaperMarkerSource source) {
        SOURCES.put(source.id(), source);
    }

    /** True if a block is a block of a source. The client calls it, with no data of the sources. */
    public static boolean sourceBlock(BlockState state) {
        return source(state).isPresent();
    }

    /**
     * A player uses an item on a block: a survey map on a block of a source goes to the map, before the use action of
     * the block. The event comes on the client and on the server, so the two sides stay the same.
     */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (PaperMapMarkers.isSurveyMap(event.getItemStack())
                && sourceBlock(event.getLevel().getBlockState(event.getPos()))) {
            event.setUseBlock(TriState.FALSE);
        }
    }

    /** The source of a block, or empty. */
    static Optional<PaperMarkerSource> source(BlockState state) {
        return SOURCES.values().stream().filter(source -> source.blocks().test(state)).findFirst();
    }

    private static String key(BlockPos pos) {
        return Long.toString(pos.asLong());
    }

    private static SurveyMarker marker(BlockPos pos, ResourceLocation source, PaperMarkerSource.Marker marker) {
        ResourceLocation type = marker.type().unwrapKey().map(ResourceKey::location)
                .orElseThrow(() -> new IllegalStateException("A map decoration type of a marker has no id"));
        return new SurveyMarker(key(pos), pos.immutable(), type, marker.name().getString(), source);
    }

    /**
     * A player uses a survey map on a block: the marker of the block goes on the map, or goes away.
     *
     * @return true if the block is a block of a source with a marker, in the area of the map
     */
    public static boolean use(ServerPlayer player, ServerLevel level, BlockPos pos, SurveyMapData data) {
        if (data.locked() || !data.dimension().equals(level.dimension()) || !inside(data, pos)) {
            return false;
        }
        Optional<PaperMarkerSource> source = source(level.getBlockState(pos));
        Optional<PaperMarkerSource.Marker> marker = source.flatMap(found -> found.marker().marker(level, pos));
        if (source.isEmpty() || marker.isEmpty()) {
            return false;
        }
        boolean removed = data.removeMarker(key(pos));
        if (!removed && data.markers().size() >= SurveyMapData.MAX_MARKERS) {
            player.displayClientMessage(Component.translatable("cartofold.paper.waypoint.full",
                    SurveyMapData.MAX_MARKERS), true);
            return true;
        }
        if (!removed) {
            data.putMarker(marker(pos, source.get().id(), marker.get()));
        }
        if (source.get().id().equals(Cartofold.BANNER_SOURCE)) {
            BannerService.get().ifPresent(service -> service.mark(player, level, pos, !removed));
        }
        Component name = marker.get().name().getString().isEmpty() ? level.getBlockState(pos).getBlock().getName()
                : marker.get().name();
        player.displayClientMessage(Component.translatable(removed ? "cartofold.paper.waypoint.removed"
                : "cartofold.paper.waypoint.added", name), true);
        return true;
    }

    /** True if a block is in the area of a map. */
    static boolean inside(SurveyMapData data, BlockPos pos) {
        PaperMapGeometry geometry = data.geometry();
        int px = geometry.pixelX(pos.getX());
        int pz = geometry.pixelZ(pos.getZ());
        return px >= 0 && pz >= 0 && px < PaperMapGeometry.SIZE && pz < PaperMapGeometry.SIZE;
    }

    /**
     * Checks the markers of a map in loaded chunks: they follow their blocks.
     *
     * @param level the level of the map
     * @param data  the data of the map
     */
    public static void check(ServerLevel level, SurveyMapData data) {
        if (data.locked() || !data.dimension().equals(level.dimension())) {
            return;
        }
        for (SurveyMarker marker : data.markers()) {
            if (!level.isLoaded(marker.pos())) {
                continue;
            }
            PaperMarkerSource source = SOURCES.get(marker.source());
            Optional<PaperMarkerSource.Marker> current = source != null
                    && source.blocks().test(level.getBlockState(marker.pos()))
                    ? source.marker().marker(level, marker.pos()) : Optional.empty();
            if (current.isEmpty()) {
                data.removeMarker(marker.key());
                continue;
            }
            SurveyMarker updated = marker(marker.pos(), marker.source(), current.get());
            if (!updated.equals(marker)) {
                data.putMarker(updated);
            }
        }
    }
}
