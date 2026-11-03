package io.github.nicolasgoyon.cartofold.paper;

import io.github.nicolasgoyon.cartofold.Cartofold;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The map decoration types of Cartofold. The icon of a type is the sprite {@code textures/map/decorations/<name>.png}
 * (see {@code tools/textures/map_decorations.py}).
 */
public final class CartofoldMapDecorations {
    private static final DeferredRegister<MapDecorationType> TYPES =
            DeferredRegister.create(Registries.MAP_DECORATION_TYPE, Cartofold.MOD_ID);

    /** A waypoint on a paper map: a pin. Other mods can use it for their own blocks. */
    public static final DeferredHolder<MapDecorationType, MapDecorationType> WAYPOINT = TYPES.register("waypoint",
            () -> new MapDecorationType(ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "waypoint"), true,
                    -1, false, false));

    private CartofoldMapDecorations() {
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }
}
