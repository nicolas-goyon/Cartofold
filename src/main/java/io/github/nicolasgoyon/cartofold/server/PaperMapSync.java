package io.github.nicolasgoyon.cartofold.server;

import io.github.nicolasgoyon.cartofold.network.PaperMapInfoPayload;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends the place of the vanilla maps to the clients (see {@link PaperMapInfoPayload}): once for each map and each
 * player, with the first vanilla packet of the map to the player. The server thread calls this class.
 */
public final class PaperMapSync {
    private static final Map<UUID, IntSet> SENT = new HashMap<>();

    private PaperMapSync() {
    }

    /**
     * The game sends the pixels of a map to a player: the player gets the place of the map, if it does not have it.
     *
     * @param player the player
     * @param id     the id of the map
     * @param data   the data of the map
     */
    public static void onUpdatePacket(ServerPlayer player, MapId id, MapItemSavedData data) {
        if (player instanceof FakePlayer || !player.connection.hasChannel(PaperMapInfoPayload.TYPE)) {
            return;
        }
        if (SENT.computeIfAbsent(player.getUUID(), key -> new IntOpenHashSet()).add(id.id())) {
            PacketDistributor.sendToPlayer(player, new PaperMapInfoPayload(id.id(), data.centerX, data.centerZ,
                    data.scale, data.dimension));
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }
}
