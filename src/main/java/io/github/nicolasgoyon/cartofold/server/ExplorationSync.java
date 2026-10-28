package io.github.nicolasgoyon.cartofold.server;

import io.github.nicolasgoyon.cartofold.data.ExploredMask;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.network.ExplorationChunksPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
 *   <li>A client tells the regions that it shows (see
 *       {@link io.github.nicolasgoyon.cartofold.network.ExplorationRequestPayload}). The server then sends the
 *       chunks of these regions that the player explored and that have a sample.</li>
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
    private final Map<UUID, Subscription> subscriptions = new HashMap<>();

    /** The regions that a client shows, and the chunks to send to it. */
    private static final class Subscription {
        final ResourceKey<Level> dimension;
        final Set<RegionPos> regions = new LinkedHashSet<>();
        final LinkedHashSet<ChunkPos> pending = new LinkedHashSet<>();

        Subscription(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }
    }

    ExplorationSync(ExplorationService service) {
        this.service = service;
    }

    /**
     * Sets the regions that a client shows. The chunks of the new regions go to the queue of the player. The chunks of
     * the regions that the client does not show go out of the queue.
     *
     * @param player    the UUID of the player
     * @param dimension the dimension of the regions
     * @param regions   the regions that the client shows
     * @param now       the game time
     */
    public void request(UUID player, ResourceKey<Level> dimension, List<RegionPos> regions, long now) {
        Subscription subscription = subscriptions.get(player);
        if (subscription == null || !subscription.dimension.equals(dimension)) {
            subscription = new Subscription(dimension);
            subscriptions.put(player, subscription);
        }
        Set<RegionPos> wanted = new LinkedHashSet<>(regions);
        subscription.regions.retainAll(wanted);
        subscription.pending.removeIf(chunk -> !wanted.contains(RegionPos.ofChunk(chunk.x, chunk.z)));
        for (RegionPos pos : wanted) {
            if (subscription.regions.add(pos)) {
                queueRegion(player, subscription, pos, now);
            }
        }
    }

    private void queueRegion(UUID player, Subscription subscription, RegionPos pos, long now) {
        MapRegion region = service.region(subscription.dimension, pos, now);
        ExploredMask mask = service.mask(subscription.dimension, player, pos, now);
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
     * @param dimension the dimension of the chunk
     * @param chunk     the chunk
     * @param now       the game time
     */
    void changed(ResourceKey<Level> dimension, ChunkPos chunk, long now) {
        RegionPos pos = RegionPos.ofChunk(chunk.x, chunk.z);
        for (Map.Entry<UUID, Subscription> entry : subscriptions.entrySet()) {
            Subscription subscription = entry.getValue();
            if (subscription.dimension.equals(dimension) && subscription.regions.contains(pos)
                    && service.mask(dimension, entry.getKey(), pos, now).isChunkExplored(chunk.x, chunk.z)) {
                subscription.pending.add(chunk);
            }
        }
    }

    /**
     * A player explores a chunk: the chunk goes to the queue of the player if the client shows its region and if the
     * chunk has a sample.
     *
     * @param player    the UUID of the player
     * @param dimension the dimension of the chunk
     * @param chunk     the chunk
     * @param now       the game time
     */
    void explored(UUID player, ResourceKey<Level> dimension, ChunkPos chunk, long now) {
        Subscription subscription = subscriptions.get(player);
        RegionPos pos = RegionPos.ofChunk(chunk.x, chunk.z);
        if (subscription != null && subscription.dimension.equals(dimension) && subscription.regions.contains(pos)
                && service.region(dimension, pos, now).sampled(chunk.x, chunk.z) != MapRegion.NEVER) {
            subscription.pending.add(chunk);
        }
    }

    /** The number of chunks in the queue of a player. */
    public int pending(UUID player) {
        Subscription subscription = subscriptions.get(player);
        return subscription == null ? 0 : subscription.pending.size();
    }

    /**
     * Takes the next chunks of the queue of a player.
     *
     * @param player the UUID of the player
     * @param max    the largest number of chunks
     * @param now    the game time
     * @return the chunks with their map data, or null if the queue is empty
     */
    @Nullable
    public ExplorationChunksPayload poll(UUID player, int max, long now) {
        Subscription subscription = subscriptions.get(player);
        if (subscription == null || subscription.pending.isEmpty()) {
            return null;
        }
        List<ExplorationChunksPayload.Chunk> chunks = new ArrayList<>();
        Iterator<ChunkPos> queue = subscription.pending.iterator();
        while (chunks.size() < Math.min(max, ExplorationChunksPayload.MAX_CHUNKS) && queue.hasNext()) {
            ChunkPos chunk = queue.next();
            queue.remove();
            MapRegion region = service.region(subscription.dimension, RegionPos.ofChunk(chunk.x, chunk.z), now);
            chunks.add(new ExplorationChunksPayload.Chunk(chunk.x, chunk.z, region.columns(chunk.x, chunk.z)));
        }
        return new ExplorationChunksPayload(subscription.dimension, chunks);
    }

    /** The players with a subscription. */
    Set<UUID> players() {
        return subscriptions.keySet();
    }
}
