package io.github.nicolasgoyon.cartofold.server;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.data.ChunkColumns;
import io.github.nicolasgoyon.cartofold.data.ExplorationKey;
import io.github.nicolasgoyon.cartofold.data.ExploredMask;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.MapSlice;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.network.ExplorationChunksPayload;
import io.github.nicolasgoyon.cartofold.network.ExplorationRequestPayload;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

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
 *   <li>A player in a cave (see {@link #underground}: under the top of its column, in the dark) also explores the band of caves of its height (see
 *       {@link MapSlice}), within {@link #CAVE_RADIUS} chunks.</li>
 *   <li>The service sends the explored chunks to the clients (see {@link ExplorationSync}).</li>
 * </ul>
 */
public final class ExplorationService {
    /** The service checks the chunks near each player every this number of ticks. */
    public static final int CHECK_TICKS = 20;
    /** The largest radius of the exploration of the caves around a player in a cave, in chunks. */
    public static final int CAVE_RADIUS = 6;
    /** A player this number of blocks or more under the top of its column can be in a cave. */
    public static final int UNDERGROUND_DEPTH = 5;
    /** A player with this sky light or less at its eyes can be in a cave. */
    public static final int CAVE_SKY_LIGHT = 3;
    /** The largest radius of the exploration around a player, in chunks. */
    public static final int MAX_RADIUS = 12;
    /** The permission level of a player that can see all the chunks with a sample (see {@link ExplorationSync}). */
    public static final int ALL_CHUNKS_PERMISSION = 2;
    /** A chunk gets a new sample after this number of ticks. */
    public static final long RESAMPLE_TICKS = 20L * 60L * 2L;
    /** The largest number of samples each tick. */
    public static final int SAMPLES_PER_TICK = 8;
    /** A region or a mask with no use for this number of ticks goes out of memory. */
    public static final long UNLOAD_TICKS = 20L * 60L * 5L;
    /** The server applies one request of the regions of a player every this number of ticks at most. */
    public static final int REQUEST_TICKS = 10;

    @Nullable
    private static ExplorationService instance;

    private final MinecraftServer server;
    private final ExplorationStorage storage;
    private final Map<ExplorationKey, LevelData> levels = new HashMap<>();
    private final ExplorationSync sync = new ExplorationSync(this);
    /** The last request of each player that waits for {@link #applyRequests}. */
    private final Map<UUID, ExplorationRequestPayload> requests = new HashMap<>();
    /** The game time of the last applied request of each player. */
    private final Map<UUID, Long> lastRequests = new HashMap<>();
    /** The last write of each file that did not end: the next write of the file waits for it. */
    private final Map<Path, CompletableFuture<Void>> fileWrites = new ConcurrentHashMap<>();

    /** The data of one slice of a dimension. */
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
            instance.applyRequests();
            instance.send();
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (instance != null) {
            instance.sync.remove(event.getEntity().getUUID());
            instance.requests.remove(event.getEntity().getUUID());
            instance.lastRequests.remove(event.getEntity().getUUID());
        }
    }

    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (instance != null) {
            instance.sync.remove(event.getEntity().getUUID());
            instance.requests.remove(event.getEntity().getUUID());
            instance.lastRequests.remove(event.getEntity().getUUID());
        }
    }

    /**
     * Receives the regions that a client shows (see {@link ExplorationSync#request}).
     *
     * @param payload the request
     * @param context the context of the payload
     */
    public static void onRequest(ExplorationRequestPayload payload, IPayloadContext context) {
        if (instance != null && context.player() instanceof ServerPlayer player) {
            // The last request waits for the next tick of the requests: a flood of requests costs one request.
            instance.requests.put(player.getUUID(), payload);
        }
    }

    /** Applies the waiting requests, one for each player every {@link #REQUEST_TICKS} ticks at most. */
    private void applyRequests() {
        long now = now();
        Iterator<Map.Entry<UUID, ExplorationRequestPayload>> iterator = requests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ExplorationRequestPayload> entry = iterator.next();
            if (now - lastRequests.getOrDefault(entry.getKey(), Long.MIN_VALUE / 2) < REQUEST_TICKS) {
                continue;
            }
            iterator.remove();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            ServerLevel level = server.getLevel(entry.getValue().dimension());
            // The server refuses the dimensions that it does not have: no file path and no data come from them.
            if (player == null || level == null) {
                continue;
            }
            lastRequests.put(entry.getKey(), now);
            List<ExplorationRequestPayload.SliceRegions> slices = entry.getValue().slices().stream()
                    .filter(slice -> validSlice(level, slice.slice())).toList();
            sync.request(player.getUUID(), level.dimension(), slices, now,
                    entry.getValue().allChunks() && player.hasPermissions(ALL_CHUNKS_PERMISSION));
        }
    }

    /** True if a slice is the surface, or a band of caves in the height of a level. */
    public static boolean validSlice(Level level, MapSlice slice) {
        return slice.surface() || (slice.minY() >= level.getMinBuildHeight() && slice.maxY() <= level.getMaxBuildHeight());
    }

    /** The data that the server sends to the clients. */
    public ExplorationSync sync() {
        return sync;
    }

    /** Sends the next chunks of the queues to the players. */
    private void send() {
        long now = now();
        for (UUID uuid : List.copyOf(sync.players())) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                sync.remove(uuid);
                continue;
            }
            if (sync.getsAllChunks(uuid) && !player.hasPermissions(ALL_CHUNKS_PERMISSION)) {
                sync.revokeAllChunks(uuid);
            }
            ExplorationChunksPayload payload = sync.poll(uuid, ExplorationSync.CHUNKS_PER_TICK, now);
            if (payload != null) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private LevelData data(ExplorationKey key) {
        return levels.computeIfAbsent(key, ignored -> new LevelData());
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
        int radius = Math.min(MAX_RADIUS, server.getPlayerList().getViewDistance());
        explore(player, ExplorationKey.surface(level.dimension()), radius);
        if (underground(player)) {
            explore(player, new ExplorationKey(level.dimension(), MapSlice.caveAt(player.getBlockY())),
                    Math.min(CAVE_RADIUS, radius));
        }
    }

    /**
     * True if a player is in a cave: in a dimension with no ceiling, {@link #UNDERGROUND_DEPTH} blocks or more under
     * the top of its column, with a sky light of {@link #CAVE_SKY_LIGHT} or less at its eyes. The client and the server
     * have the same answer: they have the same heightmap and the same light.
     */
    public static boolean underground(Player player) {
        Level level = player.level();
        return !level.dimensionType().hasCeiling()
                && level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ())
                        - player.getBlockY() >= UNDERGROUND_DEPTH
                && level.getBrightness(LightLayer.SKY, BlockPos.containing(player.getEyePosition())) <= CAVE_SKY_LIGHT;
    }

    /** Explores the loaded chunks of a slice within a radius of a player. */
    private void explore(ServerPlayer player, ExplorationKey key, int radius) {
        ServerLevel level = player.serverLevel();
        LevelData data = data(key);
        long now = now();
        ChunkPos center = player.chunkPosition();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                    continue;
                }
                RegionPos pos = RegionPos.ofChunk(chunkX, chunkZ);
                if (mask(key, player.getUUID(), pos, now).markChunk(chunkX, chunkZ)) {
                    sync.explored(player.getUUID(), key, new ChunkPos(chunkX, chunkZ), now);
                }
                long sampled = region(key, pos, now).sampled(chunkX, chunkZ);
                if (sampled == MapRegion.NEVER || now - sampled >= RESAMPLE_TICKS) {
                    data.queue.add(new ChunkPos(chunkX, chunkZ));
                }
            }
        }
    }

    /**
     * Puts a loaded chunk in the queue of the samples, for example a chunk of a survey map in the hand of a player.
     *
     * @param level the dimension of the chunk
     * @param chunk the chunk
     */
    public void requestSample(ResourceKey<Level> level, ChunkPos chunk) {
        data(ExplorationKey.surface(level)).queue.add(chunk);
    }

    /** Puts a loaded chunk of a slice in the queue of the samples, also with a recent sample. */
    public void requestSample(ExplorationKey key, ChunkPos chunk) {
        data(key).queue.add(chunk);
    }

    /** Reads the next chunks of the queues, then frees the regions and the masks with no recent use. */
    void tick() {
        int budget = SAMPLES_PER_TICK;
        long now = now();
        for (Map.Entry<ExplorationKey, LevelData> entry : levels.entrySet()) {
            ServerLevel level = server.getLevel(entry.getKey().dimension());
            Iterator<ChunkPos> queue = entry.getValue().queue.iterator();
            while (budget > 0 && queue.hasNext()) {
                ChunkPos chunk = queue.next();
                queue.remove();
                if (level != null && sample(level, entry.getKey(), chunk, now)) {
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
    boolean sample(ServerLevel level, ExplorationKey key, ChunkPos pos, long now) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null) {
            return false;
        }
        Optional<ChunkColumns> columns = ColumnSampler.sample(level, chunk, key.slice());
        if (columns.isPresent()) {
            MapRegion region = region(key, RegionPos.ofChunk(pos.x, pos.z), now);
            boolean first = region.sampled(pos.x, pos.z) == MapRegion.NEVER;
            if (region.put(pos.x, pos.z, columns.get(), now) || first) {
                sync.changed(key, pos, now);
            }
        }
        return true;
    }

    /** The map data of a region of the surface, from the memory or from the disk. A new region has no data. */
    public MapRegion region(ResourceKey<Level> level, RegionPos pos, long now) {
        return region(ExplorationKey.surface(level), pos, now);
    }

    /** The map data of a region of a slice, from the memory or from the disk. A new region has no data. */
    public MapRegion region(ExplorationKey key, RegionPos pos, long now) {
        Used<MapRegion> used = data(key).regions.computeIfAbsent(pos, region -> new Used<>(
                load(() -> storage.readRegion(key, region), () -> new MapRegion(region),
                        storage.regionFile(key, region)), now));
        used.lastUse = now;
        return used.value;
    }

    /**
     * The map data of a region, from the memory or from the disk, or null when the region has no file and is not in the
     * memory. This method creates no new region: a request of the client cannot fill the memory with empty regions.
     */
    @Nullable
    public MapRegion existingRegion(ExplorationKey key, RegionPos pos, long now) {
        LevelData data = levels.get(key);
        Used<MapRegion> used = data == null ? null : data.regions.get(pos);
        if (used == null) {
            Optional<MapRegion> stored = read(() -> storage.readRegion(key, pos), storage.regionFile(key, pos));
            if (stored.isEmpty()) {
                return null;
            }
            used = new Used<>(stored.get(), now);
            data(key).regions.put(pos, used);
        }
        used.lastUse = now;
        return used.value;
    }

    /**
     * The explored columns of a region for a player, from the memory or from the disk, or null when the player has no
     * explored column in this region. This method creates no new mask.
     */
    @Nullable
    public ExploredMask existingMask(ExplorationKey key, UUID player, RegionPos pos, long now) {
        LevelData data = levels.get(key);
        Map<RegionPos, Used<ExploredMask>> masks = data == null ? null : data.masks.get(player);
        Used<ExploredMask> used = masks == null ? null : masks.get(pos);
        if (used == null) {
            Optional<ExploredMask> stored = read(() -> storage.readMask(key, player, pos),
                    storage.maskFile(key, player, pos));
            if (stored.isEmpty()) {
                return null;
            }
            used = new Used<>(stored.get(), now);
            data(key).masks.computeIfAbsent(player, ignored -> new HashMap<>()).put(pos, used);
        }
        used.lastUse = now;
        return used.value.count() == 0 ? null : used.value;
    }

    /** The number of regions of a slice in the memory, for the tests and the diagnostics. */
    public int loadedRegions(ExplorationKey key) {
        LevelData data = levels.get(key);
        return data == null ? 0 : data.regions.size();
    }

    private static <T> Optional<T> read(Reader<T> reader, Path file) {
        try {
            return reader.read();
        } catch (IOException e) {
            Cartofold.LOGGER.error("The exploration data {} is not valid", file, e);
            ExplorationStorage.quarantine(file);
            return Optional.empty();
        }
    }

    /** The explored columns of a region of the surface for a player, from the memory or from the disk. */
    public ExploredMask mask(ResourceKey<Level> level, UUID player, RegionPos pos, long now) {
        return mask(ExplorationKey.surface(level), player, pos, now);
    }

    /** The explored columns of a region of a slice for a player, from the memory or from the disk. */
    public ExploredMask mask(ExplorationKey key, UUID player, RegionPos pos, long now) {
        Used<ExploredMask> used = data(key).masks.computeIfAbsent(player, ignored -> new HashMap<>())
                .computeIfAbsent(pos, region -> new Used<>(load(() -> storage.readMask(key, player, region),
                        () -> new ExploredMask(region), storage.maskFile(key, player, region)), now));
        used.lastUse = now;
        return used.value;
    }

    /** A source of a value from the disk. */
    @FunctionalInterface
    private interface Reader<T> {
        Optional<T> read() throws IOException;
    }

    private static <T> T load(Reader<T> reader, Supplier<T> fresh, Path file) {
        return read(reader, file).orElseGet(fresh);
    }

    /**
     * Writes the changed regions and masks.
     *
     * @param wait true to wait for the end of the writes (the stop of the server)
     */
    void saveAll(boolean wait) {
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (Map.Entry<ExplorationKey, LevelData> entry : levels.entrySet()) {
            ExplorationKey level = entry.getKey();
            for (Used<MapRegion> used : entry.getValue().regions.values()) {
                if (used.value.isDirty()) {
                    used.value.setSaved();
                    writes.add(write(storage.regionFile(level, used.value.pos()), () -> ExplorationStorage
                            .bytes(used.value), used.value::markDirty));
                }
            }
            for (Map.Entry<UUID, Map<RegionPos, Used<ExploredMask>>> player : entry.getValue().masks.entrySet()) {
                for (Used<ExploredMask> used : player.getValue().values()) {
                    if (used.value.isDirty()) {
                        used.value.setSaved();
                        writes.add(write(storage.maskFile(level, player.getKey(), used.value.pos()),
                                () -> ExplorationStorage.bytes(used.value), used.value::markDirty));
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

    /**
     * Takes the content on the server thread, then writes it on an IO thread. The writes of one file run in their
     * order, after the previous write of this file.
     *
     * @param failed runs on the server thread when the write fails: the data must be saved again
     */
    private CompletableFuture<Void> write(Path file, Content content, Runnable failed) {
        byte[] bytes;
        try {
            bytes = content.bytes();
        } catch (IOException e) {
            Cartofold.LOGGER.error("The exploration data {} cannot be written", file, e);
            failed.run();
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> next = fileWrites.getOrDefault(file, CompletableFuture.completedFuture(null))
                .thenRunAsync(() -> {
                    try {
                        ExplorationStorage.write(file, bytes);
                    } catch (IOException e) {
                        Cartofold.LOGGER.error("The exploration data {} cannot be written", file, e);
                        server.execute(failed);
                    }
                }, Util.ioPool());
        fileWrites.put(file, next);
        return next.whenComplete((result, error) -> fileWrites.remove(file, next));
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
