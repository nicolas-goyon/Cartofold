package io.github.nicolasgoyon.cartofold.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.tile.TileImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
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
 *   <li>A texture with no use for {@link #IDLE_MILLIS} milliseconds is freed. When there are more than
 *       {@link #MAX_TILES} textures, the textures with the oldest use are freed.</li>
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
    /** The largest number of textures in memory. */
    public static final int MAX_TILES = 256;

    private static final MapTiles INSTANCE = new MapTiles();

    private final Map<Key, Tile> tiles = new HashMap<>();
    private int updates;
    private int nextId;

    private record Key(ResourceKey<Level> dimension, RegionPos pos, int lod) {
    }

    /** A texture with the versions of the data that it shows. */
    private static final class Tile {
        final ResourceLocation location;
        final DynamicTexture texture;
        long version = -1;
        long northVersion = -1;
        long lastUse;

        Tile(ResourceLocation location, DynamicTexture texture) {
            this.location = location;
            this.texture = texture;
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
                        new DynamicTexture(size, size, true));
                Minecraft.getInstance().getTextureManager().register(tile.location, tile.texture);
                tiles.put(key, tile);
            }
            draw(tile, region.get(), exploration.region(dimension, northPos).orElse(null), lod);
            tile.version = version;
            tile.northVersion = northVersion;
        }
        if (tile == null) {
            return Optional.empty();
        }
        tile.lastUse = Util.getMillis();
        return Optional.of(tile.location);
    }

    private static void draw(Tile tile, MapRegion region, @Nullable MapRegion north, int lod) {
        int size = TileImage.size(lod);
        int[] pixels = TileImage.render(region, north, lod);
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

    /** Frees the textures with no recent use, and the oldest textures over {@link #MAX_TILES}. */
    private void evict(long now) {
        tiles.values().removeIf(tile -> {
            boolean idle = now - tile.lastUse >= IDLE_MILLIS;
            if (idle) {
                release(tile);
            }
            return idle;
        });
        if (tiles.size() > MAX_TILES) {
            List<Map.Entry<Key, Tile>> entries = new ArrayList<>(tiles.entrySet());
            entries.sort(Comparator.comparingLong(entry -> entry.getValue().lastUse));
            for (Map.Entry<Key, Tile> entry : entries.subList(0, tiles.size() - MAX_TILES)) {
                release(entry.getValue());
                tiles.remove(entry.getKey());
            }
        }
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
