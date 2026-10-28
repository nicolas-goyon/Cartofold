package io.github.nicolasgoyon.cartofold;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/** The Cartofold mod: one map engine for the minimap, the world map, the paper maps and the maps of the other mods. */
@Mod(Cartofold.MOD_ID)
public final class Cartofold {
    /** The id of the mod. */
    public static final String MOD_ID = "cartofold";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Cartofold(IEventBus modEventBus) {
        LOGGER.debug("Cartofold loads on the bus {}", modEventBus);
    }
}
