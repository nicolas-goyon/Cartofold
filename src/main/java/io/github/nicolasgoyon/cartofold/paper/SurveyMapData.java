package io.github.nicolasgoyon.cartofold.paper;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The data of a survey map, on the server: its place, its scale, its pixels and its markers. The storage of the
 * Overworld keeps it (key {@link SurveyMapId#key()}).
 *
 * <ul>
 *   <li>The map has 128 x 128 pixels around its center, with {@code 2^scale} blocks for each pixel, as a vanilla
 *       map (see {@link PaperMapGeometry}).</li>
 *   <li>A pixel is a packed map color ({@code id << 2 | brightness}), or 0 for a pixel that is not explored.</li>
 *   <li>Each change increases the version of the map: the server sends a map to a client when its version
 *       changes.</li>
 * </ul>
 */
public final class SurveyMapData extends SavedData {
    /** The largest scale. */
    public static final int MAX_SCALE = 4;
    private static final int SIZE = PaperMapGeometry.SIZE;

    private final ResourceKey<Level> dimension;
    private final int centerX;
    private final int centerZ;
    private final int scale;
    private boolean locked;
    private final byte[] colors;
    private final List<SurveyMarker> markers = new ArrayList<>();
    private int version = 1;

    /**
     * Makes the data of a map.
     *
     * @param dimension the dimension
     * @param centerX   the X of the center
     * @param centerZ   the Z of the center
     * @param scale     the scale, from 0 to {@link #MAX_SCALE}
     * @param locked    true for a map that does not change any more
     * @param colors    the pixels, at the index {@code x + z * 128}
     */
    public SurveyMapData(ResourceKey<Level> dimension, int centerX, int centerZ, int scale, boolean locked,
            byte[] colors) {
        if (colors.length != SIZE * SIZE) {
            throw new IllegalArgumentException("A map has " + SIZE * SIZE + " pixels");
        }
        this.dimension = dimension;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.scale = Mth.clamp(scale, 0, MAX_SCALE);
        this.locked = locked;
        this.colors = colors;
    }

    /**
     * The data of a new map around a position, with the center on the grid of the game.
     *
     * @param dimension the dimension
     * @param x         the X of the position
     * @param z         the Z of the position
     * @param scale     the scale
     */
    public static SurveyMapData around(ResourceKey<Level> dimension, int x, int z, int scale) {
        int side = SIZE * (1 << scale);
        int centerX = Mth.floor((x + 64.0) / side) * side + side / 2 - 64;
        int centerZ = Mth.floor((z + 64.0) / side) * side + side / 2 - 64;
        return new SurveyMapData(dimension, centerX, centerZ, scale, false, new byte[SIZE * SIZE]);
    }

    /** The data of a map, or null if the map does not exist. */
    @Nullable
    public static SurveyMapData get(MinecraftServer server, SurveyMapId id) {
        return server.overworld().getDataStorage().get(factory(), id.key());
    }

    /** Saves the data of a new map, and gives its id. */
    public static SurveyMapId create(MinecraftServer server, SurveyMapData data) {
        SurveyMapId id = SurveyMapCounter.get(server).next();
        server.overworld().getDataStorage().set(id.key(), data);
        data.setDirty();
        return id;
    }

    static SavedData.Factory<SurveyMapData> factory() {
        return new SavedData.Factory<>(() -> around(Level.OVERWORLD, 0, 0, 0), SurveyMapData::load, null);
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    public int scale() {
        return scale;
    }

    public boolean locked() {
        return locked;
    }

    /** The geometry of the map. */
    public PaperMapGeometry geometry() {
        return new PaperMapGeometry(centerX, centerZ, scale);
    }

    /** The pixels of the map. Do not change the array: use {@link #setColor}. */
    public byte[] colors() {
        return colors;
    }

    /** The markers of the map. */
    public List<SurveyMarker> markers() {
        return List.copyOf(markers);
    }

    /** The version of the map: it changes with each change. */
    public int version() {
        return version;
    }

    /**
     * Sets a pixel.
     *
     * @return true if the pixel changed
     */
    public boolean setColor(int px, int pz, byte color) {
        int index = px + pz * SIZE;
        if (colors[index] == color) {
            return false;
        }
        colors[index] = color;
        changed();
        return true;
    }

    /** Locks the map: its pixels do not change any more. */
    public void lock() {
        locked = true;
        changed();
    }

    /** Adds a marker, or replaces the marker with the same key. */
    public void putMarker(SurveyMarker marker) {
        markers.removeIf(old -> old.key().equals(marker.key()));
        markers.add(marker);
        changed();
    }

    /**
     * Removes the marker with a key.
     *
     * @return true if the map had the marker
     */
    public boolean removeMarker(String key) {
        boolean removed = markers.removeIf(marker -> marker.key().equals(key));
        if (removed) {
            changed();
        }
        return removed;
    }

    /** A copy of the data at another place, with no pixel and no marker, for a zoom or a move. */
    public SurveyMapData moved(int newCenterX, int newCenterZ, int newScale) {
        return new SurveyMapData(dimension, newCenterX, newCenterZ, newScale, false, new byte[SIZE * SIZE]);
    }

    /** A locked copy of the data. */
    public SurveyMapData lockedCopy() {
        SurveyMapData copy = new SurveyMapData(dimension, centerX, centerZ, scale, true, colors.clone());
        copy.markers.addAll(markers);
        return copy;
    }

    private void changed() {
        version++;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("Dimension", dimension.location().toString());
        tag.putInt("CenterX", centerX);
        tag.putInt("CenterZ", centerZ);
        tag.putInt("Scale", scale);
        tag.putBoolean("Locked", locked);
        tag.putByteArray("Colors", colors);
        ListTag list = new ListTag();
        for (SurveyMarker marker : markers) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Key", marker.key());
            entry.putLong("Pos", marker.pos().asLong());
            entry.putString("Type", marker.type().toString());
            entry.putString("Name", marker.name());
            entry.putString("Source", marker.source().toString());
            list.add(entry);
        }
        tag.put("Markers", list);
        return tag;
    }

    private static SurveyMapData load(CompoundTag tag, HolderLookup.Provider registries) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
        byte[] colors = tag.getByteArray("Colors");
        SurveyMapData data = new SurveyMapData(
                ResourceKey.create(Registries.DIMENSION, dimension == null ? Level.OVERWORLD.location() : dimension),
                tag.getInt("CenterX"), tag.getInt("CenterZ"), tag.getInt("Scale"), tag.getBoolean("Locked"),
                colors.length == SIZE * SIZE ? colors : new byte[SIZE * SIZE]);
        ListTag list = tag.getList("Markers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation type = ResourceLocation.tryParse(entry.getString("Type"));
            ResourceLocation source = ResourceLocation.tryParse(entry.getString("Source"));
            if (type != null && source != null) {
                data.markers.add(new SurveyMarker(entry.getString("Key"), BlockPos.of(entry.getLong("Pos")), type,
                        entry.getString("Name"), source));
            }
        }
        return data;
    }
}
