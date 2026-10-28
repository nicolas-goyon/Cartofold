package io.github.nicolasgoyon.cartofold.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.view.MinimapLayout;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** The registration of the parts of the client: the layer of the minimap and the keys. */
@EventBusSubscriber(modid = Cartofold.MOD_ID, value = Dist.CLIENT)
public final class CartofoldClient {
    /** The category of the keys of Cartofold. */
    public static final String KEY_CATEGORY = "key.categories.cartofold";

    public static final KeyMapping TOGGLE_MINIMAP = key("toggle_minimap", GLFW.GLFW_KEY_Y);
    public static final KeyMapping ZOOM_IN = key("zoom_in", GLFW.GLFW_KEY_RIGHT_BRACKET);
    public static final KeyMapping ZOOM_OUT = key("zoom_out", GLFW.GLFW_KEY_LEFT_BRACKET);
    public static final KeyMapping OPEN_MAP = key("open_map", GLFW.GLFW_KEY_M);
    public static final KeyMapping ADD_WAYPOINT = key("add_waypoint", GLFW.GLFW_KEY_B);

    private CartofoldClient() {
    }

    private static KeyMapping key(String name, int key) {
        return new KeyMapping("key.cartofold." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, key,
                KEY_CATEGORY);
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_MINIMAP);
        event.register(ZOOM_IN);
        event.register(ZOOM_OUT);
        event.register(OPEN_MAP);
        event.register(ADD_WAYPOINT);
    }

    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "minimap"),
                Minimap::render);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        while (OPEN_MAP.consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(new WorldMapScreen(minecraft.player));
            }
        }
        while (TOGGLE_MINIMAP.consumeClick()) {
            CartofoldClientConfig.MINIMAP.set(!CartofoldClientConfig.MINIMAP.get());
            CartofoldClientConfig.SPEC.save();
        }
        while (ADD_WAYPOINT.consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(WaypointScreen.add(null, minecraft.player.level().dimension(),
                        minecraft.player.blockPosition()));
            }
        }
        while (ZOOM_IN.consumeClick()) {
            zoom(1);
        }
        while (ZOOM_OUT.consumeClick()) {
            zoom(-1);
        }
    }

    private static void zoom(int step) {
        int zoom = Math.clamp(CartofoldClientConfig.MINIMAP_ZOOM.get() + step, 0, MinimapLayout.zoomLevels() - 1);
        CartofoldClientConfig.MINIMAP_ZOOM.set(zoom);
        CartofoldClientConfig.SPEC.save();
    }
}
