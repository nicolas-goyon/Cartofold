package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.data.ExplorationKey;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.MapSlice;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.network.ExplorationRequestPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tells the server the regions near the player and the regions of the open views (the map of the world, the map
 * frames): the server
 * then sends their explored chunks (see {@link ClientExploration}). The client sends a new request when these regions
 * change.
 */
@EventBusSubscriber(modid = Cartofold.MOD_ID, value = Dist.CLIENT)
public final class ExplorationRequests {
    /** The client requests the regions within this distance of the player, in blocks. */
    public static final int RADIUS = MapRegion.SIZE;
    /** The client checks the position of the player every this number of ticks. */
    public static final int CHECK_TICKS = 20;
    /** The regions of a view with no update for this number of milliseconds go out of the requests. */
    public static final long VIEW_MILLIS = 2000;

    @Nullable
    private static ResourceKey<Level> lastDimension;
    private static List<ExplorationRequestPayload.SliceRegions> lastSlices = List.of();
    private static int ticks;
    /** The regions of the open views, by view. */
    private static final Map<Object, View> VIEWS = new HashMap<>();

    /** The regions of a view, with the time of the last update. */
    private record View(ExplorationKey key, List<RegionPos> regions, long time) {
    }

    private ExplorationRequests() {
    }

    /** The regions within {@link #RADIUS} blocks of a position. */
    public static List<RegionPos> regionsNear(int blockX, int blockZ) {
        RegionPos min = RegionPos.ofBlock(blockX - RADIUS, blockZ - RADIUS);
        RegionPos max = RegionPos.ofBlock(blockX + RADIUS, blockZ + RADIUS);
        List<RegionPos> regions = new ArrayList<>();
        for (int z = min.z(); z <= max.z(); z++) {
            for (int x = min.x(); x <= max.x(); x++) {
                regions.add(new RegionPos(x, z));
            }
        }
        return regions;
    }

    /**
     * Sets the regions that a view shows, in addition to the regions near the player. A view calls this method at each
     * draw: the regions of a view with no update for {@link #VIEW_MILLIS} milliseconds go out of the requests.
     *
     * @param owner     the view
     * @param dimension the dimension of the view
     * @param regions   the regions of the view
     */
    public static void view(Object owner, ResourceKey<Level> dimension, List<RegionPos> regions) {
        view(owner, ExplorationKey.surface(dimension), regions);
    }

    /**
     * Sets the regions of a slice that a view shows (see {@link #view(Object, ResourceKey, List)}).
     *
     * @param owner   the view
     * @param key     the dimension and the slice of the view
     * @param regions the regions of the view
     */
    public static void view(Object owner, ExplorationKey key, List<RegionPos> regions) {
        VIEWS.put(owner, new View(key, List.copyOf(regions), Util.getMillis()));
    }

    /** Removes the regions of a view, when the view closes. */
    public static void close(Object owner) {
        VIEWS.remove(owner);
    }

    /** The regions of the open views of a dimension, for each slice: the surface first. */
    private static Map<MapSlice, List<RegionPos>> viewRegions(ResourceKey<Level> dimension) {
        long now = Util.getMillis();
        VIEWS.values().removeIf(view -> now - view.time() > VIEW_MILLIS);
        Map<MapSlice, List<RegionPos>> regions = new LinkedHashMap<>();
        regions.put(MapSlice.SURFACE, new ArrayList<>());
        VIEWS.values().stream().filter(view -> view.key().dimension().equals(dimension))
                .forEach(view -> regions.computeIfAbsent(view.key().slice(), slice -> new ArrayList<>())
                        .addAll(view.regions()));
        return regions;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (++ticks % CHECK_TICKS != 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientPacketListener connection = minecraft.getConnection();
        if (player == null || connection == null || !connection.hasChannel(ExplorationRequestPayload.TYPE)) {
            return;
        }
        ResourceKey<Level> dimension = player.level().dimension();
        List<RegionPos> near = regionsNear(player.getBlockX(), player.getBlockZ());
        List<ExplorationRequestPayload.SliceRegions> slices = new ArrayList<>();
        Map<ExplorationKey, List<RegionPos>> requested = new HashMap<>();
        for (Map.Entry<MapSlice, List<RegionPos>> entry : viewRegions(dimension).entrySet()) {
            if (slices.size() >= ExplorationRequestPayload.MAX_SLICES) {
                break;
            }
            List<RegionPos> regions = ExplorationRequestPayload.combine(
                    entry.getKey().surface() ? near : List.of(), entry.getValue());
            slices.add(new ExplorationRequestPayload.SliceRegions(entry.getKey(), regions));
            requested.put(new ExplorationKey(dimension, entry.getKey()), regions);
        }
        if (!dimension.equals(lastDimension) || !slices.equals(lastSlices)) {
            lastDimension = dimension;
            lastSlices = slices;
            PacketDistributor.sendToServer(new ExplorationRequestPayload(dimension, slices));
            ClientExploration.get().retain(requested);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        lastDimension = null;
        lastSlices = List.of();
        VIEWS.clear();
        ClientExploration.get().clear();
        ClientWaypoints.get().set(List.of());
        ClientLayers.get().clear();
    }
}
