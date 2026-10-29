package io.github.nicolasgoyon.cartofold.paper;

import io.github.nicolasgoyon.cartofold.api.PaperMarkerSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The waypoints of blocks on the paper maps, as the banners of the game (see
 * {@link io.github.nicolasgoyon.cartofold.api.PaperMapMarkers}).
 *
 * <ul>
 *   <li>A player uses a filled map on a block of a source, in the area of the map: the map gets a waypoint at the
 *       block, with the name of the source. A second use removes the waypoint.</li>
 *   <li>The server keeps the waypoints of each map (data {@code cartofold_paper_waypoints}). Every
 *       {@link #CHECK_TICKS} ticks, it gives the decorations to the maps in the hands of each player and in the item
 *       frames at {@link #FRAME_DISTANCE} blocks or less: the game does not save the decorations in the map.</li>
 *   <li>When the chunk of a waypoint is loaded, the server checks its block: a block that is not of the source any
 *       more, or a source with no name for the block, removes the waypoint. The name of a waypoint follows the name of
 *       the source.</li>
 * </ul>
 */
public final class PaperWaypoints extends SavedData {
    private static final String FILE_ID = "cartofold_paper_waypoints";
    private static final SavedData.Factory<PaperWaypoints> FACTORY =
            new SavedData.Factory<>(PaperWaypoints::new, PaperWaypoints::load, null);
    /** The id of a decoration of a waypoint starts with this text, then the packed position of the block. */
    static final String DECORATION_PREFIX = "cartofold_waypoint_";
    /** The server gives the waypoints to the maps near each player every this number of ticks. */
    public static final int CHECK_TICKS = 40;
    /** The item frames with a map at this distance or less from a player get the waypoints, in blocks. */
    public static final double FRAME_DISTANCE = 32.0;
    /** The decoration of a waypoint points down, as a banner. */
    private static final double ROTATION = 180.0;

    private static final Map<ResourceLocation, PaperMarkerSource> SOURCES = new ConcurrentHashMap<>();

    /**
     * A waypoint of a map.
     *
     * @param source the identifier of the source
     * @param name   the last name of the waypoint
     */
    record Waypoint(ResourceLocation source, String name) {
    }

    /** The waypoints of each map, by position of block. */
    private final Map<Integer, Map<BlockPos, Waypoint>> maps = new HashMap<>();

    PaperWaypoints() {
    }

    /** Adds a source of waypoints. */
    public static void register(PaperMarkerSource source) {
        SOURCES.put(source.id(), source);
    }

    /** The source of a block, or empty. */
    static Optional<PaperMarkerSource> source(BlockState state) {
        return SOURCES.values().stream().filter(source -> source.blocks().test(state)).findFirst();
    }

    /** The waypoints of a server. */
    public static PaperWaypoints get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                && use(player, level, event.getPos(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * A player uses a map on a block: the waypoint of the block goes on the map, or goes away.
     *
     * @return true if the block is a block of a source, with a name, in the area of the map
     */
    public static boolean use(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
        MapId id = stack.get(DataComponents.MAP_ID);
        MapItemSavedData data = id == null ? null : MapItem.getSavedData(id, level);
        if (data == null || data.dimension != level.dimension() || !inside(data, pos)) {
            return false;
        }
        Optional<PaperMarkerSource> source = source(level.getBlockState(pos));
        Optional<Component> name = source.flatMap(found -> found.name().name(level, pos));
        if (source.isEmpty() || name.isEmpty()) {
            return false;
        }
        PaperWaypoints waypoints = get(level);
        boolean added = waypoints.toggle(id.id(), pos.immutable(), new Waypoint(source.get().id(),
                name.get().getString()));
        if (!added) {
            data.removeDecoration(DECORATION_PREFIX + pos.asLong());
        }
        apply(level, id.id(), data);
        player.displayClientMessage(Component.translatable(added ? "cartofold.paper.waypoint.added"
                : "cartofold.paper.waypoint.removed", name.get()), true);
        return true;
    }

    /** True if a block is in the area of a map. */
    static boolean inside(MapItemSavedData data, BlockPos pos) {
        PaperMapGeometry geometry = new PaperMapGeometry(data.centerX, data.centerZ, data.scale);
        int px = geometry.pixelX(pos.getX());
        int pz = geometry.pixelZ(pos.getZ());
        return px >= 0 && pz >= 0 && px < PaperMapGeometry.SIZE && pz < PaperMapGeometry.SIZE;
    }

    /** Gives the waypoints to the maps of a player: in its hands, and in the item frames near it. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK_TICKS != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        List<ItemStack> stacks = new ArrayList<>();
        for (InteractionHand hand : InteractionHand.values()) {
            stacks.add(player.getItemInHand(hand));
        }
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class,
                player.getBoundingBox().inflate(FRAME_DISTANCE))) {
            stacks.add(frame.getItem());
        }
        for (ItemStack stack : stacks) {
            MapId id = stack.get(DataComponents.MAP_ID);
            MapItemSavedData data = id == null ? null : MapItem.getSavedData(id, level);
            if (data != null && data.dimension == level.dimension()) {
                apply(level, id.id(), data);
            }
        }
    }

    /**
     * Adds a waypoint to a map, or removes it.
     *
     * @return true if the map has the waypoint now
     */
    boolean toggle(int map, BlockPos pos, Waypoint waypoint) {
        Map<BlockPos, Waypoint> waypoints = maps.computeIfAbsent(map, key -> new LinkedHashMap<>());
        boolean added = waypoints.remove(pos) == null;
        if (added) {
            waypoints.put(pos, waypoint);
        } else if (waypoints.isEmpty()) {
            maps.remove(map);
        }
        setDirty();
        return added;
    }

    /**
     * Gives the decorations of the waypoints to a map. The waypoints in loaded chunks follow their blocks.
     *
     * @param level the level of the map
     * @param map   the id of the map
     * @param data  the data of the map
     */
    public static void apply(ServerLevel level, int map, MapItemSavedData data) {
        PaperWaypoints waypoints = get(level);
        Map<BlockPos, Waypoint> ofMap = waypoints.maps.get(map);
        if (ofMap == null) {
            return;
        }
        for (Map.Entry<BlockPos, Waypoint> entry : Map.copyOf(ofMap).entrySet()) {
            BlockPos pos = entry.getKey();
            Waypoint waypoint = entry.getValue();
            String decoration = DECORATION_PREFIX + pos.asLong();
            PaperMarkerSource source = SOURCES.get(waypoint.source());
            Optional<Component> name = Optional.of(Component.literal(waypoint.name()));
            if (level.isLoaded(pos)) {
                name = source != null && source.blocks().test(level.getBlockState(pos))
                        ? source.name().name(level, pos) : Optional.empty();
            }
            if (source == null || name.isEmpty()) {
                data.removeDecoration(decoration);
                waypoints.toggle(map, pos, waypoint);
                continue;
            }
            if (!name.get().getString().equals(waypoint.name())) {
                ofMap.put(pos, new Waypoint(waypoint.source(), name.get().getString()));
                waypoints.setDirty();
            }
            data.addDecoration(source.type(), level, decoration, pos.getX() + 0.5, pos.getZ() + 0.5, ROTATION,
                    name.get());
        }
    }

    /** The waypoints of a map, by position. */
    Map<BlockPos, Waypoint> waypoints(int map) {
        return maps.getOrDefault(map, Map.of());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        maps.forEach((map, waypoints) -> waypoints.forEach((pos, waypoint) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Map", map);
            entry.putLong("Pos", pos.asLong());
            entry.putString("Source", waypoint.source().toString());
            entry.putString("Name", waypoint.name());
            list.add(entry);
        }));
        tag.put("Waypoints", list);
        return tag;
    }

    private static PaperWaypoints load(CompoundTag tag, HolderLookup.Provider registries) {
        PaperWaypoints data = new PaperWaypoints();
        ListTag list = tag.getList("Waypoints", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation source = ResourceLocation.tryParse(entry.getString("Source"));
            if (source != null) {
                data.maps.computeIfAbsent(entry.getInt("Map"), key -> new LinkedHashMap<>())
                        .put(BlockPos.of(entry.getLong("Pos")), new Waypoint(source, entry.getString("Name")));
            }
        }
        return data;
    }
}
