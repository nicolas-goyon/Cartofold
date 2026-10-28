package io.github.nicolasgoyon.cartofold.server;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.data.ChunkColumns;
import io.github.nicolasgoyon.cartofold.data.ExploredMask;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The exploration of the players, on the server.
 *
 * <ul>
 *   <li>Every {@link #CHECK_TICKS} ticks, the chunks near each player (the view distance of the server, at most
 *       {@link #MAX_RADIUS} chunks) are explored by the player: its mask gets their columns. Only the loaded chunks
 *       count: the exploration never loads or generates a chunk.</li>
 *   <li>An explored chunk with no sample, or with a sample older than {@link #RESAMPLE_TICKS} ticks, goes to the queue
 *       of the samples. The server reads {@link #SAMPLES_PER_TICK} chunks of the queue at most each tick (see
 *       {@link ColumnSampler}).</li>
 *   <li>The regions and the masks are in memory while the players use them. A region or a mask with no use for
 *       {@link #UNLOAD_TICKS} ticks goes out of memory, after its save.</li>
 *   <li>The save of the world writes the changed regions and masks on the IO threads of the game. The stop of the
 *       server writes them before the end.</li>
 * </ul>
 */
public final class ExplorationService {
    /** The service checks the chunks near each player every this number of ticks. */
    public static final int CHECK_TICKS = 20;
    /** The largest radius of the exploration around a player, in chunks. */
    public static final int MAX_RADIUS = 12;
    /** A chunk gets a new sample after this number of ticks. */
    public static final long RESAMPLE_TICKS = 20L * 60L * 2L;
    /** The largest number of samples each tick. */
    public static final int SAMPLES_PER_TICK = 8;
    /** A region or a mask with no use for this number of ticks goes out of memory. */
    public static final long UNLOAD_TICKS = 20L * 60L * 5L;

    @Nullable
    private static ExplorationService instance;

    private final MinecraftServer server;
    private final ExplorationStorage storage;
    private final Map<ResourceKey<Level>, LevelData> levels = new HashMap<>();

    /** The data of one level. */
    private static final class LevelData {
        final Map<RegionPos, Used<MapRegion>> regions = new HashMap<>();
        final Map<UUID, Map<RegionPos, Used<ExploredMask>>> masks = new HashMap<>();
        final LinkedHashSet<ChunkPos> queue = new LinkedHashSet<>();
    }

    /** A value with the game time of its last use. */
    private static final class Used<T> {
        final T value;
        long lastUse;

        Used(T value, long lastUse) {
            this.value = value;
            this.lastUse = lastUse;
        }
    }

    ExplorationService(MinecraftServer server, Path root) {
        this.server = server;
        this.storage = new ExplorationStorage(root);
    }

    /** The service of the running server, or empty with no server. */
    public static Optional<ExplorationService> get() {
        return Optional.ofNullable(instance);
    }

    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        instance = new ExplorationService(server, server.getWorldPath(LevelResource.ROOT).resolve("data")
                .resolve(Cartofold.MOD_ID));
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        if (instance != null) {
            instance.saveAll(true);
            instance = null;
        }
    }

    public static void onLevelSave(LevelEvent.Save event) {
        if (instance != null && event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            instance.saveAll(false);
        }
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (instance != null && event.getEntity() instanceof ServerPlayer player
                && player.tickCount % CHECK_TICKS == 0) {
            instance.explore(player);
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (instance != null) {
            instance.tick();
        }
    }

    private LevelData data(ResourceKey<Level> level) {
        return levels.computeIfAbsent(level, key -> new LevelData());
    }

    private long now() {
        return server.overworld().getGameTime();
    }

    /**
     * Explores the loaded chunks near a player: its mask gets their columns, and the chunks with an old sample go to
     * the queue.
     *
     * @param player the player
     */
    public void explore(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (level.dimensionType().hasCeiling()) {
            return;
        }
        LevelData data = data(level.dimension());
        long now = now();
        int radius = Math.min(MAX_RADIUS, server.getPlayerList().getViewDistance());
        ChunkPos center = player.chunkPosition();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                    continue;
                }
                RegionPos pos = RegionPos.ofChunk(chunkX, chunkZ);
                mask(level.dimension(), player.getUUID(), pos, now).markChunk(chunkX, chunkZ);
                long sampled = region(level.dimension(), pos, now).sampled(chunkX, chunkZ);
                if (sampled == MapRegion.NEVER || now - sampled >= RESAMPLE_TICKS) {
                    data.queue.add(new ChunkPos(chunkX, chunkZ));
                }
            }
        }
    }

    /** Reads the next chunks of the queues, then frees the regions and the masks with no recent use. */
    void tick() {
        int budget = SAMPLES_PER_TICK;
        long now = now();
        for (Map.Entry<ResourceKey<Level>, LevelData> entry : levels.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey());
            Iterator<ChunkPos> queue = entry.getValue().queue.iterator();
            while (budget > 0 && queue.hasNext()) {
                ChunkPos chunk = queue.next();
                queue.remove();
                if (level != null && sample(level, chunk, now)) {
                    budget--;
                }
            }
        }
        if (now % UNLOAD_TICKS == 0) {
            unload(now);
        }
    }

    /**
     * Reads the columns of a loaded chunk into its region.
     *
     * @return true if the chunk was loaded
     */
    boolean sample(ServerLevel level, ChunkPos pos, long now) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null) {
            return false;
        }
        Optional<ChunkColumns> columns = ColumnSampler.sample(level, chunk);
        columns.ifPresent(found -> region(level.dimension(), RegionPos.ofChunk(pos.x, pos.z), now)
                .put(pos.x, pos.z, found, now));
        return true;
    }

    /** The map data of a region, from the memory or from the disk. A new region has no data. */
    public MapRegion region(ResourceKey<Level> level, RegionPos pos, long now) {
        Used<MapRegion> used = data(level).regions.computeIfAbsent(pos, key -> new Used<>(
                load(() -> storage.readRegion(level, key), () -> new MapRegion(key), key), now));
        used.lastUse = now;
        return used.value;
    }

    /** The explored columns of a region for a player, from the memory or from the disk. */
    public ExploredMask mask(ResourceKey<Level> level, UUID player, RegionPos pos, long now) {
        Used<ExploredMask> used = data(level).masks.computeIfAbsent(player, key -> new HashMap<>())
                .computeIfAbsent(pos, key -> new Used<>(load(() -> storage.readMask(level, player, key),
                        () -> new ExploredMask(key), key), now));
        used.lastUse = now;
        return used.value;
    }

    /** A source of a value from the disk. */
    @FunctionalInterface
    private interface Reader<T> {
        Optional<T> read() throws IOException;
    }

    private static <T> T load(Reader<T> reader, Supplier<T> fresh, RegionPos pos) {
        try {
            return reader.read().orElseGet(fresh);
        } catch (IOException e) {
            Cartofold.LOGGER.error("The exploration data of the region {} is not valid: it starts again", pos, e);
            return fresh.get();
        }
    }

    /**
     * Writes the changed regions and masks.
     *
     * @param wait true to wait for the end of the writes (the stop of the server)
     */
    void saveAll(boolean wait) {
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (Map.Entry<ResourceKey<Level>, LevelData> entry : levels.entrySet()) {
            ResourceKey<Level> level = entry.getKey();
            for (Used<MapRegion> used : entry.getValue().regions.values()) {
                if (used.value.isDirty()) {
                    writes.add(write(storage.regionFile(level, used.value.pos()), () -> ExplorationStorage
                            .bytes(used.value)));
                    used.value.setSaved();
                }
            }
            for (Map.Entry<UUID, Map<RegionPos, Used<ExploredMask>>> player : entry.getValue().masks.entrySet()) {
                for (Used<ExploredMask> used : player.getValue().values()) {
                    if (used.value.isDirty()) {
                        writes.add(write(storage.maskFile(level, player.getKey(), used.value.pos()),
                                () -> ExplorationStorage.bytes(used.value)));
                        used.value.setSaved();
                    }
                }
            }
        }
        if (wait) {
            CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();
        }
    }

    /** The content of a file, from the server thread. */
    @FunctionalInterface
    private interface Content {
        byte[] bytes() throws IOException;
    }

    /** Takes the content on the server thread, then writes it on an IO thread. */
    private static CompletableFuture<Void> write(Path file, Content content) {
        byte[] bytes;
        try {
            bytes = content.bytes();
        } catch (IOException e) {
            Cartofold.LOGGER.error("The exploration data {} cannot be written", file, e);
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            try {
                ExplorationStorage.write(file, bytes);
            } catch (IOException e) {
                Cartofold.LOGGER.error("The exploration data {} cannot be written", file, e);
            }
        }, Util.ioPool());
    }

    /** Saves, then frees the regions and the masks with no use for {@link #UNLOAD_TICKS} ticks. */
    private void unload(long now) {
        saveAll(false);
        for (LevelData data : levels.values()) {
            data.regions.values().removeIf(used -> now - used.lastUse >= UNLOAD_TICKS && !used.value.isDirty());
            for (Map<RegionPos, Used<ExploredMask>> masks : data.masks.values()) {
                masks.values().removeIf(used -> now - used.lastUse >= UNLOAD_TICKS && !used.value.isDirty());
            }
            data.masks.values().removeIf(Map::isEmpty);
        }
    }
}
