package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.network.ExplorationChunksPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The exploration data that the client received from the server. The views of the maps read it.
 *
 * <p>In a region, a chunk with no data has the sample time {@link MapRegion#NEVER}. Each change of a region increases
 * the version of the region and the version of the store: a view makes its image again when a version changes.
 *
 * <p>This class uses no class of the client: the payload handler on the common side can call it.
 */
public final class ClientExploration {
    private static final ClientExploration INSTANCE = new ClientExploration();

    private final Map<ResourceKey<Level>, Map<RegionPos, Versioned>> dimensions = new HashMap<>();
    private long version;

    /** A region with the number of its changes. */
    private static final class Versioned {
        final MapRegion region;
        long version;

        Versioned(MapRegion region) {
            this.region = region;
        }
    }

    /** Makes an empty store. The game uses {@link #get()}. */
    public ClientExploration() {
    }

    /** The store of the client. */
    public static ClientExploration get() {
        return INSTANCE;
    }

    /**
     * Puts the chunks of a payload in the store.
     *
     * @param payload the chunks from the server
     */
    public synchronized void accept(ExplorationChunksPayload payload) {
        Map<RegionPos, Versioned> regions = dimensions.computeIfAbsent(payload.dimension(), key -> new HashMap<>());
        version++;
        for (ExplorationChunksPayload.Chunk chunk : payload.chunks()) {
            RegionPos pos = RegionPos.ofChunk(chunk.x(), chunk.z());
            Versioned versioned = regions.computeIfAbsent(pos, key -> new Versioned(new MapRegion(key)));
            versioned.region.put(chunk.x(), chunk.z(), chunk.columns(), version);
            versioned.version = version;
        }
    }

    /** The data of a region, or empty if the client has no chunk of it. */
    public synchronized Optional<MapRegion> region(ResourceKey<Level> dimension, RegionPos pos) {
        return Optional.ofNullable(dimensions.getOrDefault(dimension, Map.of()).get(pos)).map(used -> used.region);
    }

    /** The version of a region: it changes with each change of the region. 0 for a region with no data. */
    public synchronized long version(ResourceKey<Level> dimension, RegionPos pos) {
        Versioned versioned = dimensions.getOrDefault(dimension, Map.of()).get(pos);
        return versioned == null ? 0 : versioned.version;
    }

    /** The version of the store: it changes with each change of a region. */
    public synchronized long version() {
        return version;
    }

    /** Removes all the data, when the client leaves a world. */
    public synchronized void clear() {
        dimensions.clear();
        version++;
    }
}
