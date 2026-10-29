package io.github.nicolasgoyon.cartofold;

import com.mojang.logging.LogUtils;
import io.github.nicolasgoyon.cartofold.api.PaperMapMarkers;
import io.github.nicolasgoyon.cartofold.api.PaperMarkerSource;
import io.github.nicolasgoyon.cartofold.client.CartofoldClientConfig;
import io.github.nicolasgoyon.cartofold.network.CartofoldNetwork;
import io.github.nicolasgoyon.cartofold.paper.CartofoldItems;
import io.github.nicolasgoyon.cartofold.paper.CartofoldMapDecorations;
import io.github.nicolasgoyon.cartofold.paper.MapUpdates;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapSync;
import io.github.nicolasgoyon.cartofold.server.ExplorationService;
import io.github.nicolasgoyon.cartofold.server.LayerService;
import io.github.nicolasgoyon.cartofold.server.WaypointService;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** The Cartofold mod: one map engine for the minimap, the world map, the paper maps and the maps of the other mods. */
@Mod(Cartofold.MOD_ID)
public final class Cartofold {
    /** The id of the mod. */
    public static final String MOD_ID = "cartofold";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Cartofold(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, CartofoldClientConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(ExplorationService::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ExplorationService::onServerStopping);
        NeoForge.EVENT_BUS.addListener(ExplorationService::onLevelSave);
        NeoForge.EVENT_BUS.addListener(ExplorationService::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ExplorationService::onServerTick);
        NeoForge.EVENT_BUS.addListener(ExplorationService::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(ExplorationService::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(WaypointService::onServerStarted);
        NeoForge.EVENT_BUS.addListener(WaypointService::onServerStopping);
        NeoForge.EVENT_BUS.addListener(WaypointService::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(WaypointService::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(WaypointService::onDeath);
        NeoForge.EVENT_BUS.addListener(LayerService::onServerStarted);
        NeoForge.EVENT_BUS.addListener(LayerService::onServerStopping);
        NeoForge.EVENT_BUS.addListener(LayerService::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(LayerService::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(SurveyMapSync::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(SurveyMapSync::onPlayerLoggedOut);
        CartofoldItems.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(MapUpdates::onServerTick);
        NeoForge.EVENT_BUS.addListener(MapUpdates::onServerStopped);
        CartofoldMapDecorations.register(modEventBus);
        PaperMapMarkers.register(PaperMarkerSource.of(ResourceLocation.fromNamespaceAndPath(MOD_ID, "lodestone"),
                state -> state.is(Blocks.LODESTONE), CartofoldMapDecorations.WAYPOINT,
                (level, pos) -> Optional.of(Blocks.LODESTONE.getName())));
        PaperMapMarkers.register(new PaperMarkerSource(ResourceLocation.fromNamespaceAndPath(MOD_ID, "banner"),
                state -> state.is(BlockTags.BANNERS), (level, pos) -> Optional.ofNullable(MapBanner.fromWorld(level,
                        pos)).map(banner -> new PaperMarkerSource.Marker(banner.getDecoration(),
                                banner.name().orElse(Component.empty())))));
        modEventBus.addListener(CartofoldNetwork::register);
    }
}
