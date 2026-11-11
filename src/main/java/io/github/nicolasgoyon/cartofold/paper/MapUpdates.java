package io.github.nicolasgoyon.cartofold.paper;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import io.github.nicolasgoyon.cartofold.Cartofold;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The update of a survey map at a distance, in the cartography table (see {@link MapMoves#UPDATE}).
 *
 * <ul>
 *   <li>The update reads the chunks of the area of the map that are already generated: it never generates a chunk. A
 *       chunk that is not loaded comes from the disk, with no wait on the server thread: the update keeps the chunk
 *       loaded with a ticket until it paints the chunk.</li>
 *   <li>An update reads {@link #LIMIT} chunks at most. The chunks with unexplored pixels come first, then the other
 *       chunks, each group from the middle of the map. Thus a large map needs more than one update.</li>
 *   <li>The server paints {@link #CHUNKS_PER_TICK} chunks at most each tick. The check of the chunks on the disk runs
 *       on the thread of the storage. Each update loads {@link #LOADS} chunks at most at the same time.</li>
 *   <li>The colors of the pixels follow the colors of the game: the most frequent color of the top blocks of the
 *       pixel, and a brightness from the height of the pixel to the north.</li>
 * </ul>
 */
public final class MapUpdates {
    /** The largest number of chunks of an update. */
    public static final int LIMIT = 1024;
    /** The largest number of chunks that the server reads each tick. */
    public static final int CHUNKS_PER_TICK = 4;
    /** The largest number of checks of the disk at the same time, for each update. */
    private static final int CHECKS = 32;
    /** The largest number of chunks that load at the same time, for each update. */
    public static final int LOADS = 8;
    /** The ticket that keeps a chunk loaded until the update paints it. */
    private static final TicketType<ChunkPos> TICKET = TicketType.create(Cartofold.MOD_ID + ":map_update",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final String FULL = "minecraft:full";
    private static final List<Job> JOBS = new ArrayList<>();

    private MapUpdates() {
    }

    /** An update in progress. */
    private static final class Job {
        final ServerLevel level;
        final SurveyMapId id;
        final Deque<ChunkPos> waiting;
        /** The chunks that are full on the disk or loaded, before their load. Other threads add to this queue. */
        final Queue<ChunkPos> ready = new ConcurrentLinkedQueue<>();
        /** The loaded chunks, before their paint. */
        final Queue<ChunkPos> loaded = new ArrayDeque<>();
        /** The chunks with a ticket of the update. */
        final Set<ChunkPos> tickets = new HashSet<>();
        final double[] heights = new double[MapMoves.SIZE * MapMoves.SIZE];
        int checking;
        int loading;

        Job(ServerLevel level, SurveyMapId id, List<ChunkPos> chunks) {
            this.level = level;
            this.id = id;
            this.waiting = new ArrayDeque<>(chunks);
            Arrays.fill(heights, Double.NaN);
        }

        boolean done() {
            return waiting.isEmpty() && checking == 0 && ready.isEmpty() && loading == 0 && loaded.isEmpty();
        }

        /** Removes the ticket of a chunk, if the update has one. */
        void release(ChunkPos chunk) {
            if (tickets.remove(chunk)) {
                level.getChunkSource().removeRegionTicket(TICKET, chunk, 0, chunk);
            }
        }

        /** Removes all the tickets of the update. */
        void releaseAll() {
            for (ChunkPos chunk : List.copyOf(tickets)) {
                release(chunk);
            }
        }
    }

    /**
     * Starts the update of a map.
     *
     * @param level the level of the map
     * @param id    the id of the map
     * @param data  the data of the map
     */
    public static void start(ServerLevel level, SurveyMapId id, SurveyMapData data) {
        JOBS.removeIf(job -> {
            boolean same = job.id.equals(id) && job.level == level;
            if (same) {
                job.releaseAll();
            }
            return same;
        });
        JOBS.add(new Job(level, id, chunks(data)));
    }

    /**
     * True if a map has an update in progress.
     *
     * @param id the id of the map
     * @return true during the update
     */
    public static boolean running(SurveyMapId id) {
        return JOBS.stream().anyMatch(job -> job.id.equals(id));
    }

    /** The dimension that the running update of a map reads, or empty with no update. */
    public static Optional<ResourceKey<Level>> dimension(SurveyMapId id) {
        return JOBS.stream().filter(job -> job.id.equals(id)).map(job -> job.level.dimension()).findFirst();
    }

    /** The chunks of an update: the chunks with unexplored pixels first, each group from the middle of the map. */
    public static List<ChunkPos> chunks(SurveyMapData data) {
        int factor = 1 << data.scale();
        int minX = blockX(data, 0);
        int minZ = blockZ(data, 0);
        int maxX = blockX(data, MapMoves.SIZE - 1) + factor - 1;
        int maxZ = blockZ(data, MapMoves.SIZE - 1) + factor - 1;
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = minX >> 4; x <= maxX >> 4; x++) {
            for (int z = minZ >> 4; z <= maxZ >> 4; z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        int middleX = data.centerX() >> 4;
        int middleZ = data.centerZ() >> 4;
        chunks.sort(Comparator.<ChunkPos, Boolean>comparing(chunk -> explored(data, chunk))
                .thenComparingInt(chunk -> Math.max(Math.abs(chunk.x - middleX), Math.abs(chunk.z - middleZ))));
        return chunks.subList(0, Math.min(LIMIT, chunks.size()));
    }

    /**
     * The number of chunks in the area of a map. An update reads {@link #LIMIT} of them at most.
     *
     * @param map the geometry of the map
     * @return the number of chunks
     */
    public static int chunkCount(PaperMapGeometry map) {
        int last = MapMoves.SIZE - 1;
        int columns = ((map.blockX(last) + map.factor() - 1) >> 4) - (map.blockX(0) >> 4) + 1;
        int rows = ((map.blockZ(last) + map.factor() - 1) >> 4) - (map.blockZ(0) >> 4) + 1;
        return columns * rows;
    }

    /** True if all the pixels of a chunk are explored. */
    private static boolean explored(SurveyMapData data, ChunkPos chunk) {
        int[] range = pixels(data, chunk);
        for (int px = range[0]; px <= range[1]; px++) {
            for (int pz = range[2]; pz <= range[3]; pz++) {
                if (data.colors()[px + pz * MapMoves.SIZE] == 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The X of the first block of a pixel, as in the game. */
    static int blockX(SurveyMapData data, int px) {
        int factor = 1 << data.scale();
        return (data.centerX() / factor + px - MapMoves.SIZE / 2) * factor;
    }

    /** The Z of the first block of a pixel, as in the game. */
    static int blockZ(SurveyMapData data, int pz) {
        int factor = 1 << data.scale();
        return (data.centerZ() / factor + pz - MapMoves.SIZE / 2) * factor;
    }

    /** The pixels of a chunk: {min X, max X, min Z, max Z}, in the map. An empty range has max smaller than min. */
    static int[] pixels(SurveyMapData data, ChunkPos chunk) {
        int[] range = {MapMoves.SIZE, -1, MapMoves.SIZE, -1};
        for (int p = 0; p < MapMoves.SIZE; p++) {
            if (blockX(data, p) >> 4 == chunk.x) {
                range[0] = Math.min(range[0], p);
                range[1] = Math.max(range[1], p);
            }
            if (blockZ(data, p) >> 4 == chunk.z) {
                range[2] = Math.min(range[2], p);
                range[3] = Math.max(range[3], p);
            }
        }
        return range;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (JOBS.isEmpty()) {
            return;
        }
        int budget = CHUNKS_PER_TICK;
        for (Iterator<Job> iterator = JOBS.iterator(); iterator.hasNext();) {
            Job job = iterator.next();
            SurveyMapData data = SurveyMapData.get(job.level.getServer(), job.id);
            if (data == null || data.locked()) {
                job.releaseAll();
                iterator.remove();
                continue;
            }
            check(job);
            load(job);
            ChunkPos chunk;
            while (budget > 0 && (chunk = job.loaded.poll()) != null) {
                LevelChunk loaded = job.level.getChunkSource().getChunkNow(chunk.x, chunk.z);
                if (loaded != null) {
                    paint(job, data, loaded);
                    budget--;
                } else if (!job.tickets.contains(chunk)) {
                    // The chunk was loaded at the check, but it unloaded before its paint: it loads again, with a ticket.
                    job.ready.add(chunk);
                }
                job.release(chunk);
            }
            if (job.done()) {
                iterator.remove();
            }
        }
    }

    /**
     * Starts the loads of the ready chunks. A loaded chunk goes to the paint now. Another chunk gets a ticket, then
     * loads on the threads of the game: the server thread does not wait for the disk.
     */
    private static void load(Job job) {
        ChunkPos chunk;
        while (job.loading < LOADS && (chunk = job.ready.poll()) != null) {
            ServerChunkCache chunks = job.level.getChunkSource();
            if (chunks.getChunkNow(chunk.x, chunk.z) != null) {
                job.loaded.add(chunk);
                continue;
            }
            ChunkPos pos = chunk;
            chunks.addRegionTicket(TICKET, pos, 0, pos);
            job.tickets.add(pos);
            job.loading++;
            // The load reports its end in the queues of the job: the future is not needed.
            var unused = chunks.getChunkFuture(pos.x, pos.z, ChunkStatus.FULL, true)
                    .whenComplete((result, error) -> job.level.getServer().execute(() -> {
                        job.loading--;
                        job.loaded.add(pos);
                    }));
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        JOBS.clear();
    }

    /** Starts the checks of the next chunks: a loaded chunk is ready, a chunk on the disk is ready when it is full. */
    private static void check(Job job) {
        while (job.checking < CHECKS && !job.waiting.isEmpty()) {
            ChunkPos chunk = job.waiting.poll();
            if (job.level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null) {
                job.ready.add(chunk);
                continue;
            }
            job.checking++;
            CollectFields status = new CollectFields(new FieldSelector(StringTag.TYPE, "Status"));
            // The check reports its result in the queues of the job: the future is not needed.
            var unused = job.level.getChunkSource().chunkMap.chunkScanner().scanChunk(chunk, status)
                    .whenComplete((result, error) -> {
                if (error == null && status.getResult() instanceof CompoundTag tag
                        && FULL.equals(tag.getString("Status"))) {
                    job.ready.add(chunk);
                }
                job.level.getServer().execute(() -> job.checking--);
            });
        }
    }

    /** Paints the pixels of a chunk on the map. */
    private static void paint(Job job, SurveyMapData data, LevelChunk chunk) {
        int factor = 1 << data.scale();
        int[] range = pixels(data, chunk.getPos());
        ServerLevel level = job.level;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (int px = range[0]; px <= range[1]; px++) {
            for (int pz = range[2]; pz <= range[3]; pz++) {
                int startX = blockX(data, px);
                int startZ = blockZ(data, pz);
                Multiset<MapColor> colors = LinkedHashMultiset.create();
                double height = 0.0;
                int depth = 0;
                for (int dx = 0; dx < factor; dx++) {
                    for (int dz = 0; dz < factor; dz++) {
                        pos.set(startX + dx, 0, startZ + dz);
                        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ()) + 1;
                        BlockState state;
                        if (y <= level.getMinBuildHeight() + 1) {
                            state = Blocks.BEDROCK.defaultBlockState();
                        } else {
                            do {
                                pos.setY(--y);
                                state = chunk.getBlockState(pos);
                            } while (state.getMapColor(level, pos) == MapColor.NONE && y > level.getMinBuildHeight());
                            if (y > level.getMinBuildHeight() && !state.getFluidState().isEmpty()) {
                                int bottom = y - 1;
                                below.set(pos);
                                BlockState under;
                                do {
                                    below.setY(bottom--);
                                    under = chunk.getBlockState(below);
                                    depth++;
                                } while (bottom > level.getMinBuildHeight() && !under.getFluidState().isEmpty());
                                if (!state.isFaceSturdy(level, pos, Direction.UP)) {
                                    state = state.getFluidState().createLegacyBlock();
                                }
                            }
                        }
                        height += (double) y / (factor * factor);
                        colors.add(state.getMapColor(level, pos));
                    }
                }
                depth /= factor * factor;
                MapColor color = Iterables.getFirst(Multisets.copyHighestCountFirst(colors), MapColor.NONE);
                int index = px + pz * MapMoves.SIZE;
                job.heights[index] = height;
                double north = pz > 0 && !Double.isNaN(job.heights[index - MapMoves.SIZE])
                        ? job.heights[index - MapMoves.SIZE] : height;
                MapColor.Brightness brightness = brightness(color, height, north, depth, px, pz, factor);
                data.setColor(px, pz, color.getPackedId(brightness));
            }
        }
    }

    /** The brightness of a pixel, as in the game. */
    static MapColor.Brightness brightness(MapColor color, double height, double north, int depth, int px, int pz,
            int factor) {
        int parity = (px + pz) & 1;
        if (color == MapColor.WATER) {
            double shade = depth * 0.1 + parity * 0.2;
            return shade < 0.5 ? MapColor.Brightness.HIGH
                    : shade > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
        }
        double shade = (height - north) * 4.0 / (factor + 4) + (parity - 0.5) * 0.4;
        return shade > 0.6 ? MapColor.Brightness.HIGH
                : shade < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
    }
}
