package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.network.ExplorationRequestPayload;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
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
 * Tells the server the regions near the player: the server then sends their explored chunks (see
 * {@link ClientExploration}). The client sends a new request when the player goes into other regions or into another
 * dimension.
 */
@EventBusSubscriber(modid = Cartofold.MOD_ID, value = Dist.CLIENT)
public final class ExplorationRequests {
    /** The client requests the regions within this distance of the player, in blocks. */
    public static final int RADIUS = MapRegion.SIZE;
    /** The client checks the position of the player every this number of ticks. */
    public static final int CHECK_TICKS = 20;

    @Nullable
    private static ResourceKey<Level> lastDimension;
    private static List<RegionPos> lastRegions = List.of();
    private static int ticks;

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
        List<RegionPos> regions = regionsNear(player.getBlockX(), player.getBlockZ());
        if (!dimension.equals(lastDimension) || !regions.equals(lastRegions)) {
            lastDimension = dimension;
            lastRegions = regions;
            PacketDistributor.sendToServer(new ExplorationRequestPayload(dimension, regions));
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        lastDimension = null;
        lastRegions = List.of();
        ClientExploration.get().clear();
    }
}
