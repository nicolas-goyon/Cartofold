package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.network.ExplorationRequestPayload;
import java.util.ArrayList;
import java.util.HashMap;
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
    private static List<RegionPos> lastRegions = List.of();
    private static int ticks;
    /** The regions of the open views, by view. */
    private static final Map<Object, View> VIEWS = new HashMap<>();

    /** The regions of a view, with the time of the last update. */
    private record View(ResourceKey<Level> dimension, List<RegionPos> regions, long time) {
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
        VIEWS.put(owner, new View(dimension, List.copyOf(regions), Util.getMillis()));
    }

    /** Removes the regions of a view, when the view closes. */
    public static void close(Object owner) {
        VIEWS.remove(owner);
    }

    /** The regions of the open views of a dimension. */
    private static List<RegionPos> viewRegions(ResourceKey<Level> dimension) {
        long now = Util.getMillis();
        VIEWS.values().removeIf(view -> now - view.time() > VIEW_MILLIS);
        List<RegionPos> regions = new ArrayList<>();
        VIEWS.values().stream().filter(view -> view.dimension().equals(dimension))
                .forEach(view -> regions.addAll(view.regions()));
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
        List<RegionPos> regions = ExplorationRequestPayload.combine(near,
                viewRegions(dimension));
        if (!dimension.equals(lastDimension) || !regions.equals(lastRegions)) {
            lastDimension = dimension;
            lastRegions = regions;
            PacketDistributor.sendToServer(new ExplorationRequestPayload(dimension, regions));
            ClientExploration.get().retain(dimension, regions);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        lastDimension = null;
        lastRegions = List.of();
        VIEWS.clear();
        ClientExploration.get().clear();
        ClientWaypoints.get().set(List.of());
        ClientLayers.get().clear();
    }
}
