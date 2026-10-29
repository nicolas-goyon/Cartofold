package io.github.nicolasgoyon.cartofold.paper;

import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.server.ExplorationService;
import io.github.nicolasgoyon.cartofold.tile.TileImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.MapColor;

/**
 * The exploration of a survey map in the hand of a player.
 *
 * <ul>
 *   <li>Every {@link #UPDATE_TICKS} ticks, the pixels of the map within {@link #RADIUS} blocks of the player get the
 *       colors of the exploration data of Cartofold (see {@link ExplorationService}). A spyglass in the inventory
 *       makes the radius {@link SpyglassMaps#FACTOR} times larger.</li>
 *   <li>A pixel shows the column at the middle of its cell, with the shades of the vanilla maps (see
 *       {@link TileImage}).</li>
 *   <li>A chunk with no data stays unexplored. A loaded chunk with no data goes to the queue of the samples of the
 *       exploration: the next updates give its pixels. The update never loads or generates a chunk.</li>
 *   <li>A locked map, or a map of another dimension, does not change.</li>
 * </ul>
 */
public final class SurveyMapExploration {
    /** The radius of the exploration with no spyglass, in blocks, as the vanilla maps. */
    public static final int RADIUS = 128;
    /** The time between two updates of a map in the hand, in ticks. */
    public static final int UPDATE_TICKS = 10;
    private static final int SIZE = PaperMapGeometry.SIZE;

    private SurveyMapExploration() {
    }

    /**
     * Updates a map in the hand of a player.
     *
     * @param player the player
     * @param data   the data of the map
     */
    public static void update(ServerPlayer player, SurveyMapData data) {
        ServerLevel level = player.serverLevel();
        if (data.locked() || !level.dimension().equals(data.dimension())) {
            return;
        }
        ExplorationService service = ExplorationService.get().orElse(null);
        if (service == null) {
            return;
        }
        PaperMapGeometry geometry = data.geometry();
        int factor = geometry.factor();
        int radius = SpyglassMaps.radius(RADIUS, player) / factor;
        int playerX = geometry.pixelX(player.getBlockX());
        int playerZ = geometry.pixelZ(player.getBlockZ());
        long now = level.getServer().overworld().getGameTime();
        Map<RegionPos, MapRegion> regions = new HashMap<>();
        for (int pz = Math.max(0, playerZ - radius); pz <= Math.min(SIZE - 1, playerZ + radius); pz++) {
            for (int px = Math.max(0, playerX - radius); px <= Math.min(SIZE - 1, playerX + radius); px++) {
                int dx = px - playerX;
                int dz = pz - playerZ;
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                int blockX = geometry.blockX(px) + factor / 2;
                int blockZ = geometry.blockZ(pz) + factor / 2;
                MapRegion region = regions.computeIfAbsent(RegionPos.ofBlock(blockX, blockZ),
                        pos -> service.region(level.dimension(), pos, now));
                if (region.sampled(blockX >> 4, blockZ >> 4) == MapRegion.NEVER) {
                    if (level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4) != null) {
                        service.requestSample(level.dimension(), new ChunkPos(blockX >> 4, blockZ >> 4));
                    }
                    continue;
                }
                byte color = color(region, regions, service, level, now, blockX, blockZ, factor, px, pz);
                data.setColor(px, pz, color);
            }
        }
    }

    /** The packed color of a pixel, from the column at the middle of its cell. */
    private static byte color(MapRegion region, Map<RegionPos, MapRegion> regions, ExplorationService service,
            ServerLevel level, long now, int blockX, int blockZ, int factor, int px, int pz) {
        int id = region.color(blockX, blockZ);
        if (id == 0) {
            return 0;
        }
        int checker = (px + pz) & 1;
        MapColor.Brightness brightness;
        if (id == MapColor.WATER.id) {
            brightness = TileImage.waterBrightness(region.depth(blockX, blockZ), checker);
        } else {
            int height = region.height(blockX, blockZ);
            int northZ = blockZ - factor;
            MapRegion north = regions.computeIfAbsent(RegionPos.ofBlock(blockX, northZ),
                    pos -> service.region(level.dimension(), pos, now));
            int northHeight = north.sampled(blockX >> 4, northZ >> 4) != MapRegion.NEVER
                    ? north.height(blockX, northZ) : height;
            brightness = TileImage.landBrightness(height - northHeight, factor, checker);
        }
        return MapColor.byId(id).getPackedId(brightness);
    }
}
