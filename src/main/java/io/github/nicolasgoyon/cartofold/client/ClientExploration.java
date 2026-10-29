package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.data.ExplorationKey;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.network.ExplorationChunksPayload;
import io.github.nicolasgoyon.cartofold.network.ExplorationRequestPayload;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    /**
     * The largest number of regions in the store, when the regions of the last request are fewer. A region is about 1
     * MiB in memory.
     */
    public static final int MAX_REGIONS = ExplorationRequestPayload.MAX_REGIONS + 32;

    private static final ClientExploration INSTANCE = new ClientExploration();

    private final Map<ExplorationKey, Map<RegionPos, Versioned>> dimensions = new HashMap<>();
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
        Map<RegionPos, Versioned> regions = dimensions.computeIfAbsent(payload.key(), key -> new HashMap<>());
        version++;
        for (ExplorationChunksPayload.Chunk chunk : payload.chunks()) {
            RegionPos pos = RegionPos.ofChunk(chunk.x(), chunk.z());
            Versioned versioned = regions.computeIfAbsent(pos, key -> new Versioned(new MapRegion(key)));
            versioned.region.put(chunk.x(), chunk.z(), chunk.columns(), version);
            versioned.version = version;
        }
    }

    /** The data of a region of the surface, or empty if the client has no chunk of it. */
    public Optional<MapRegion> region(ResourceKey<Level> dimension, RegionPos pos) {
        return region(ExplorationKey.surface(dimension), pos);
    }

    /** The data of a region of a slice, or empty if the client has no chunk of it. */
    public synchronized Optional<MapRegion> region(ExplorationKey key, RegionPos pos) {
        return Optional.ofNullable(dimensions.getOrDefault(key, Map.of()).get(pos)).map(used -> used.region);
    }

    /** The version of a region of the surface (see {@link #version(ExplorationKey, RegionPos)}). */
    public long version(ResourceKey<Level> dimension, RegionPos pos) {
        return version(ExplorationKey.surface(dimension), pos);
    }

    /** The version of a region: it changes with each change of the region. 0 for a region with no data. */
    public synchronized long version(ExplorationKey key, RegionPos pos) {
        Versioned versioned = dimensions.getOrDefault(key, Map.of()).get(pos);
        return versioned == null ? 0 : versioned.version;
    }

    /** The number of regions in the store, in all the dimensions. */
    public synchronized int size() {
        return dimensions.values().stream().mapToInt(Map::size).sum();
    }

    /**
     * Frees regions when the store has more than {@link #MAX_REGIONS} regions. The regions of the last request stay.
     * The other regions go out, the oldest change first. The server sends a region again when a request has it again.
     *
     * @param requested the regions of each slice of the last request
     */
    public synchronized void retain(Map<ExplorationKey, ? extends Collection<RegionPos>> requested) {
        int excess = size() - MAX_REGIONS;
        if (excess <= 0) {
            return;
        }
        record Candidate(ExplorationKey dimension, RegionPos pos, long version) {
        }
        List<Candidate> candidates = new ArrayList<>();
        dimensions.forEach((key, regions) -> {
            Collection<RegionPos> requestedRegions = requested.get(key);
            Set<RegionPos> kept = requestedRegions == null ? Set.of() : new HashSet<>(requestedRegions);
            regions.forEach((pos, versioned) -> {
                if (!kept.contains(pos)) {
                    candidates.add(new Candidate(key, pos, versioned.version));
                }
            });
        });
        candidates.sort(Comparator.comparingLong(Candidate::version));
        for (Candidate candidate : candidates.subList(0, Math.min(excess, candidates.size()))) {
            dimensions.get(candidate.dimension()).remove(candidate.pos());
        }
        dimensions.values().removeIf(Map::isEmpty);
        version++;
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
