package io.github.nicolasgoyon.cartofold.server;

import io.github.nicolasgoyon.cartofold.data.ExplorationKey;
import io.github.nicolasgoyon.cartofold.data.ExploredMask;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.MapSlice;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.network.ExplorationChunksPayload;
import io.github.nicolasgoyon.cartofold.network.ExplorationRequestPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * The exploration data that the server sends to each client.
 *
 * <ul>
 *   <li>A client tells the regions that it shows, for each slice (the surface, and a band of caves, see
 *       {@link io.github.nicolasgoyon.cartofold.data.MapSlice}) of a dimension (see {@link ExplorationRequestPayload}).
 *       The server then sends the chunks of these regions that the player explored and that have a sample.</li>
 *   <li>When a chunk of these regions gets a new sample, or when the player explores a chunk with a sample, the
 *       server sends the chunk again.</li>
 *   <li>The server sends {@link #CHUNKS_PER_TICK} chunks at most each tick to each player.</li>
 * </ul>
 *
 * <p>A player only gets the chunks that it explored: the map data of the other chunks stays on the server.
 */
public final class ExplorationSync {
    /** The largest number of chunks that the server sends to a player each tick. */
    public static final int CHUNKS_PER_TICK = 32;

    private final ExplorationService service;
    private final Map<UUID, Map<ExplorationKey, Subscription>> subscriptions = new HashMap<>();

    /** The regions of a slice that a client shows, and the chunks to send to it. */
    private static final class Subscription {
        final Set<RegionPos> regions = new LinkedHashSet<>();
        final LinkedHashSet<ChunkPos> pending = new LinkedHashSet<>();
    }

    ExplorationSync(ExplorationService service) {
        this.service = service;
    }

    /**
     * Sets the regions that a client shows, in place of the last request. The chunks of the new regions go to the queue
     * of the player. The chunks of the regions that the client does not show go out of the queue.
     *
     * @param player    the UUID of the player
     * @param dimension the dimension of the regions
     * @param slices    the regions of each slice
     * @param now       the game time
     */
    public void request(UUID player, ResourceKey<Level> dimension, List<ExplorationRequestPayload.SliceRegions> slices,
            long now) {
        Map<ExplorationKey, Subscription> old = subscriptions.getOrDefault(player, Map.of());
        Map<ExplorationKey, Subscription> next = new LinkedHashMap<>();
        for (ExplorationRequestPayload.SliceRegions slice : slices) {
            ExplorationKey key = new ExplorationKey(dimension, slice.slice());
            Subscription subscription = old.containsKey(key) ? old.get(key) : new Subscription();
            Set<RegionPos> wanted = new LinkedHashSet<>(slice.regions());
            subscription.regions.retainAll(wanted);
            subscription.pending.removeIf(chunk -> !wanted.contains(RegionPos.ofChunk(chunk.x, chunk.z)));
            for (RegionPos pos : wanted) {
                if (subscription.regions.add(pos)) {
                    queueRegion(player, key, subscription, pos, now);
                }
            }
            next.put(key, subscription);
        }
        subscriptions.put(player, next);
    }

    /** Sets the regions of the surface of a dimension that a client shows (see {@link #request}). */
    public void requestSurface(UUID player, ResourceKey<Level> dimension, List<RegionPos> regions, long now) {
        request(player, dimension, List.of(new ExplorationRequestPayload.SliceRegions(
                MapSlice.SURFACE, regions)), now);
    }

    private void queueRegion(UUID player, ExplorationKey key, Subscription subscription, RegionPos pos, long now) {
        MapRegion region = service.region(key, pos, now);
        ExploredMask mask = service.mask(key, player, pos, now);
        int minX = pos.x() << MapRegion.CHUNK_SHIFT;
        int minZ = pos.z() << MapRegion.CHUNK_SHIFT;
        for (int z = minZ; z < minZ + MapRegion.CHUNKS; z++) {
            for (int x = minX; x < minX + MapRegion.CHUNKS; x++) {
                if (region.sampled(x, z) != MapRegion.NEVER && mask.isChunkExplored(x, z)) {
                    subscription.pending.add(new ChunkPos(x, z));
                }
            }
        }
    }

    /** Stops the sends to a player. */
    public void remove(UUID player) {
        subscriptions.remove(player);
    }

    /**
     * A chunk has new map data: it goes to the queue of the players that show its region and that explored it.
     *
     * @param key   the dimension and the slice of the chunk
     * @param chunk the chunk
     * @param now   the game time
     */
    void changed(ExplorationKey key, ChunkPos chunk, long now) {
        RegionPos pos = RegionPos.ofChunk(chunk.x, chunk.z);
        for (Map.Entry<UUID, Map<ExplorationKey, Subscription>> entry : subscriptions.entrySet()) {
            Subscription subscription = entry.getValue().get(key);
            if (subscription != null && subscription.regions.contains(pos)
                    && service.mask(key, entry.getKey(), pos, now).isChunkExplored(chunk.x, chunk.z)) {
                subscription.pending.add(chunk);
            }
        }
    }

    /**
     * A player explores a chunk: the chunk goes to the queue of the player if the client shows its region and if the
     * chunk has a sample.
     *
     * @param player the UUID of the player
     * @param key    the dimension and the slice of the chunk
     * @param chunk  the chunk
     * @param now    the game time
     */
    void explored(UUID player, ExplorationKey key, ChunkPos chunk, long now) {
        Subscription subscription = subscriptions.getOrDefault(player, Map.of()).get(key);
        RegionPos pos = RegionPos.ofChunk(chunk.x, chunk.z);
        if (subscription != null && subscription.regions.contains(pos)
                && service.region(key, pos, now).sampled(chunk.x, chunk.z) != MapRegion.NEVER) {
            subscription.pending.add(chunk);
        }
    }

    /** The number of chunks in the queues of a player. */
    public int pending(UUID player) {
        return subscriptions.getOrDefault(player, Map.of()).values().stream()
                .mapToInt(subscription -> subscription.pending.size()).sum();
    }

    /**
     * Takes the next chunks of the queues of a player, all of one slice.
     *
     * @param player the UUID of the player
     * @param max    the largest number of chunks
     * @param now    the game time
     * @return the chunks with their map data, or null if the queues are empty
     */
    @Nullable
    public ExplorationChunksPayload poll(UUID player, int max, long now) {
        for (Map.Entry<ExplorationKey, Subscription> entry : subscriptions.getOrDefault(player, Map.of())
                .entrySet()) {
            Subscription subscription = entry.getValue();
            if (subscription.pending.isEmpty()) {
                continue;
            }
            List<ExplorationChunksPayload.Chunk> chunks = new ArrayList<>();
            Iterator<ChunkPos> queue = subscription.pending.iterator();
            while (chunks.size() < Math.min(max, ExplorationChunksPayload.MAX_CHUNKS) && queue.hasNext()) {
                ChunkPos chunk = queue.next();
                queue.remove();
                MapRegion region = service.region(entry.getKey(), RegionPos.ofChunk(chunk.x, chunk.z), now);
                chunks.add(new ExplorationChunksPayload.Chunk(chunk.x, chunk.z, region.columns(chunk.x, chunk.z)));
            }
            return new ExplorationChunksPayload(entry.getKey(), chunks);
        }
        return null;
    }

    /** The players with a subscription. */
    Set<UUID> players() {
        return subscriptions.keySet();
    }
}
