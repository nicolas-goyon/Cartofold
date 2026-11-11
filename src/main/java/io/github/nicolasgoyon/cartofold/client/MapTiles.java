package io.github.nicolasgoyon.cartofold.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.data.ExplorationKey;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.tile.TileBudget;
import io.github.nicolasgoyon.cartofold.tile.TileImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * The textures of the regions, for the map views. A view asks for the texture of a region at a level of detail (see
 * {@link TileImage}) in each frame.
 *
 * <ul>
 *   <li>A texture is made again when its region, or the region to the north, changes (see the versions of
 *       {@link ClientExploration}). {@link #UPDATES_PER_FRAME} textures at most are made each frame: a view shows the
 *       old texture until its update.</li>
 *   <li>A texture with no use for {@link #IDLE_MILLIS} milliseconds is freed. A texture of a region that the client
 *       no longer has is freed. When the textures use more than {@link #BUDGET} bytes, the textures with the oldest
 *       use are freed, but not a texture with a use in the last {@link #IN_USE_MILLIS} milliseconds (see
 *       {@link TileBudget}).</li>
 * </ul>
 *
 * <p>Use this class on the render thread only.
 */
@EventBusSubscriber(modid = Cartofold.MOD_ID, value = Dist.CLIENT)
public final class MapTiles {
    /** The largest number of textures that are made each frame. */
    public static final int UPDATES_PER_FRAME = 4;
    /** A texture with no use for this number of milliseconds is freed. */
    public static final long IDLE_MILLIS = 30_000L;
    /** The largest memory of the textures, in bytes. A texture at the level of detail 0 uses 1 MiB. */
    public static final long BUDGET = 64L * 1024 * 1024;
    /** A texture with a use in this number of milliseconds stays, also over the budget. */
    public static final long IN_USE_MILLIS = 1_000L;
    /** The bytes of a pixel of a texture. */
    private static final int PIXEL_BYTES = 4;

    private static final MapTiles INSTANCE = new MapTiles();

    private final Map<Key, Tile> tiles = new HashMap<>();
    /** The pixels of the texture to make: one array for all the textures. */
    private final int[] pixels = new int[MapRegion.SIZE * MapRegion.SIZE];
    private int updates;
    private int nextId;

    private record Key(ExplorationKey slice, RegionPos pos, int lod) {
    }

    /** A texture with the versions of the data that it shows. */
    private static final class Tile {
        final ResourceLocation location;
        final DynamicTexture texture;
        final long bytes;
        long version = -1;
        long northVersion = -1;
        long lastUse;

        Tile(ResourceLocation location, DynamicTexture texture, long bytes) {
            this.location = location;
            this.texture = texture;
            this.bytes = bytes;
        }
    }

    private MapTiles() {
    }

    /** The textures of the client. */
    public static MapTiles get() {
        return INSTANCE;
    }

    /**
     * The texture of a region at a level of detail.
     *
     * @param dimension the dimension
     * @param pos       the region
     * @param lod       the level of detail, from 0 to {@link TileImage#LODS} - 1
     * @return the location of the texture, or empty if the client has no data for the region
     */
    public Optional<ResourceLocation> tile(ResourceKey<Level> dimension, RegionPos pos, int lod) {
        return tile(ExplorationKey.surface(dimension), pos, lod);
    }

    /**
     * The texture of a region of a slice at a level of detail.
     *
     * @param dimension the dimension and the slice
     * @param pos       the region
     * @param lod       the level of detail, from 0 to {@link TileImage#LODS} - 1
     * @return the location of the texture, or empty if the client has no data for the region
     */
    public Optional<ResourceLocation> tile(ExplorationKey dimension, RegionPos pos, int lod) {
        ClientExploration exploration = ClientExploration.get();
        Optional<MapRegion> region = exploration.region(dimension, pos);
        if (region.isEmpty()) {
            return Optional.empty();
        }
        Key key = new Key(dimension, pos, lod);
        Tile tile = tiles.get(key);
        long version = exploration.version(dimension, pos);
        RegionPos northPos = new RegionPos(pos.x(), pos.z() - 1);
        long northVersion = exploration.version(dimension, northPos);
        if ((tile == null || tile.version != version || tile.northVersion != northVersion)
                && updates < UPDATES_PER_FRAME) {
            updates++;
            if (tile == null) {
                int size = TileImage.size(lod);
                tile = new Tile(ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "tile/" + nextId++),
                        new DynamicTexture(size, size, true), (long) size * size * PIXEL_BYTES);
                Minecraft.getInstance().getTextureManager().register(tile.location, tile.texture);
                tiles.put(key, tile);
            }
            draw(tile, TileImage.render(region.get(), exploration.region(dimension, northPos).orElse(null), lod,
                    pixels), lod);
            tile.version = version;
            tile.northVersion = northVersion;
        }
        if (tile == null) {
            return Optional.empty();
        }
        tile.lastUse = Util.getMillis();
        return Optional.of(tile.location);
    }

    private static void draw(Tile tile, int[] pixels, int lod) {
        int size = TileImage.size(lod);
        NativeImage image = tile.texture.getPixels();
        if (image == null) {
            return;
        }
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                image.setPixelRGBA(x, z, pixels[x + z * size]);
            }
        }
        tile.texture.upload();
    }

    /**
     * Frees the textures with no recent use, the textures of the regions that the client no longer has, and the oldest
     * textures over {@link #BUDGET}.
     */
    private void evict(long now) {
        ClientExploration exploration = ClientExploration.get();
        tiles.entrySet().removeIf(entry -> {
            Tile tile = entry.getValue();
            boolean free = now - tile.lastUse >= IDLE_MILLIS
                    || exploration.version(entry.getKey().slice(), entry.getKey().pos()) == 0;
            if (free) {
                release(tile);
            }
            return free;
        });
        List<Map.Entry<Key, Tile>> over = TileBudget.over(new ArrayList<>(tiles.entrySet()),
                entry -> entry.getValue().bytes, entry -> entry.getValue().lastUse, BUDGET, now, IN_USE_MILLIS);
        for (Map.Entry<Key, Tile> entry : over) {
            release(entry.getValue());
            tiles.remove(entry.getKey());
        }
    }

    /** The memory of the textures, in bytes. */
    public long bytes() {
        return tiles.values().stream().mapToLong(tile -> tile.bytes).sum();
    }

    private static void release(Tile tile) {
        Minecraft.getInstance().getTextureManager().release(tile.location);
    }

    /** The number of textures in memory. */
    public int count() {
        return tiles.size();
    }

    /** Frees all the textures. */
    public void clear() {
        tiles.values().forEach(MapTiles::release);
        tiles.clear();
    }

    @SubscribeEvent
    public static void onRenderFrame(RenderFrameEvent.Pre event) {
        INSTANCE.updates = 0;
        INSTANCE.evict(Util.getMillis());
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        INSTANCE.clear();
    }
}
