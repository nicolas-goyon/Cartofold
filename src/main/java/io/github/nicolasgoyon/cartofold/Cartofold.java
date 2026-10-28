package io.github.nicolasgoyon.cartofold;

import com.mojang.logging.LogUtils;
import io.github.nicolasgoyon.cartofold.client.CartofoldClientConfig;
import io.github.nicolasgoyon.cartofold.network.CartofoldNetwork;
import io.github.nicolasgoyon.cartofold.server.ExplorationService;
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
        modEventBus.addListener(CartofoldNetwork::register);
    }
}
