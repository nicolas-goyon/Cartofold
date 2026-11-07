package io.github.nicolasgoyon.cartofold.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.api.LayerInfo;
import io.github.nicolasgoyon.cartofold.api.MapViewKind;
import io.github.nicolasgoyon.cartofold.waypoint.BannerMark;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The banners that each player marked on a vanilla map or on a survey map. The service shows them on the minimap and on
 * the map of the world of this player, in the layer {@link BannerMark#LAYER}.
 *
 * <ul>
 *   <li>The marks of a player are in memory while the player is on the server, and in the file
 *       {@code data/cartofold/banners/<uuid>.json} of the world.</li>
 *   <li>Every {@link #CHECK_TICKS} ticks, the service checks the marks in loaded chunks: a block that is not a banner
 *       removes the mark, and the color and the name follow the banner.</li>
 * </ul>
 */
public final class BannerService {
    /** The time between two checks of the marks of a player, in ticks. */
    public static final int CHECK_TICKS = 40;

    @Nullable
    private static BannerService instance;

    private final Path root;
    private final Map<UUID, List<BannerMark>> players = new HashMap<>();
    private CompletableFuture<Void> writes = CompletableFuture.completedFuture(null);

    BannerService(Path root) {
        this.root = root;
    }

    /** The service of the running server, or empty with no server. */
    public static Optional<BannerService> get() {
        return Optional.ofNullable(instance);
    }

    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        instance = new BannerService(server.getWorldPath(LevelResource.ROOT).resolve("data")
                .resolve(Cartofold.MOD_ID).resolve("banners"));
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        if (instance != null) {
            instance.writes.join();
            instance = null;
        }
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (instance != null && event.getEntity() instanceof ServerPlayer player) {
            instance.send(player);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (instance != null) {
            instance.players.remove(event.getEntity().getUUID());
        }
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (instance != null && event.getEntity() instanceof ServerPlayer player
                && !(player instanceof FakePlayer) && player.tickCount % CHECK_TICKS == 0) {
            instance.check(player);
        }
    }

    /**
     * A player used a vanilla map on a block: the mark of a banner follows the banners of the map.
     *
     * @param context the use of the map
     * @param result  the result of the use
     */
    public static void onVanillaMapUse(UseOnContext context, InteractionResult result) {
        if (instance == null || !result.consumesAction() || !(context.getLevel() instanceof ServerLevel level)
                || !(context.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos pos = context.getClickedPos();
        MapItemSavedData data = MapItem.getSavedData(context.getItemInHand(), level);
        if (data != null && level.getBlockState(pos).is(BlockTags.BANNERS)) {
            instance.mark(player, level, pos, data.getBanners().stream().anyMatch(banner -> banner.pos().equals(pos)));
        }
    }

    /**
     * Adds or removes the mark of a banner of a player.
     *
     * @param player the player
     * @param level  the level of the banner
     * @param pos    the position of the banner
     * @param marked true to add the mark, false to remove it
     */
    public void mark(ServerPlayer player, ServerLevel level, BlockPos pos, boolean marked) {
        List<BannerMark> marks = marks(player.getUUID());
        MapBanner banner = marked ? MapBanner.fromWorld(level, pos) : null;
        if (banner != null && marks.size() < LayerContent.MAX_ELEMENTS) {
            update(player, BannerMark.set(marks, BannerMark.of(level.dimension(), banner)));
        } else if (!marked) {
            update(player, BannerMark.remove(marks, level.dimension(), pos));
        }
    }

    /** Checks the marks of a player in loaded chunks: they follow their banners. */
    public void check(ServerPlayer player) {
        List<BannerMark> marks = marks(player.getUUID());
        List<BannerMark> next = new ArrayList<>(marks.size());
        for (BannerMark mark : marks) {
            ServerLevel level = player.server.getLevel(mark.dimension());
            if (level == null || !level.isLoaded(mark.pos())) {
                next.add(mark);
                continue;
            }
            MapBanner banner = MapBanner.fromWorld(level, mark.pos());
            if (banner != null) {
                next.add(BannerMark.of(mark.dimension(), banner));
            }
        }
        if (!next.equals(marks)) {
            update(player, next);
        }
    }

    /** The marks of a player, from the memory or from the disk. */
    public List<BannerMark> marks(UUID player) {
        return players.computeIfAbsent(player, this::read);
    }

    private Path file(UUID player) {
        return root.resolve(player + ".json");
    }

    private List<BannerMark> read(UUID player) {
        Path file = file(player);
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            return BannerMark.LIST_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(IOException::new);
        } catch (IOException | RuntimeException e) {
            Cartofold.LOGGER.error("The banners {} are not valid: the player has no marked banner", file, e);
            ExplorationStorage.quarantine(file);
            return List.of();
        }
    }

    private void update(ServerPlayer player, List<BannerMark> marks) {
        players.put(player.getUUID(), marks);
        Path file = file(player.getUUID());
        byte[] bytes = BannerMark.LIST_CODEC.encodeStart(JsonOps.INSTANCE, marks).getOrThrow().toString()
                .getBytes(StandardCharsets.UTF_8);
        writes = writes.thenRunAsync(() -> {
            try {
                ExplorationStorage.write(file, bytes);
            } catch (IOException e) {
                Cartofold.LOGGER.error("The banners {} cannot be written", file, e);
            }
        }, Util.ioPool());
        send(player);
    }

    private void send(ServerPlayer player) {
        LayerInfo info = new LayerInfo(BannerMark.LAYER, Component.translatable("cartofold.layer.banners"), 0,
                Set.of(MapViewKind.MINIMAP, MapViewKind.WORLD_MAP, MapViewKind.HUD), false);
        LayerContent content = new LayerContent(info, List.of(), List.of(),
                marks(player.getUUID()).stream().map(BannerMark::marker).toList());
        LayerService.get().ifPresent(service -> service.send(player, content));
    }
}
