package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.view.MinimapLayout;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;

/** The settings of the client, in the file {@code config/cartofold-client.toml}. */
public final class CartofoldClientConfig {
    /** The shape of the minimap. */
    public enum Shape implements TranslatableEnum {
        SQUARE,
        ROUND;

        @Override
        public Component getTranslatedName() {
            return Component.translatable("cartofold.configuration.minimap.shape." + name().toLowerCase(Locale.ROOT));
        }
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue MINIMAP;
    public static final ModConfigSpec.EnumValue<Shape> MINIMAP_SHAPE;
    public static final ModConfigSpec.EnumValue<MinimapLayout.Corner> MINIMAP_CORNER;
    public static final ModConfigSpec.IntValue MINIMAP_SIZE;
    public static final ModConfigSpec.IntValue MINIMAP_ZOOM;
    public static final ModConfigSpec.BooleanValue MINIMAP_ROTATE;
    public static final ModConfigSpec.BooleanValue MINIMAP_COORDINATES;
    public static final ModConfigSpec.BooleanValue MINIMAP_WAYPOINTS;
    public static final ModConfigSpec.BooleanValue WORLD_MAP_WAYPOINTS;
    public static final ModConfigSpec.BooleanValue WORLD_MAP_WAYPOINT_NAMES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> HIDDEN_LAYERS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.translation("cartofold.configuration.minimap").push("minimap");
        MINIMAP = builder.comment("Show the minimap.")
                .translation("cartofold.configuration.minimap.enabled").define("enabled", true);
        MINIMAP_SHAPE = builder.comment("The shape of the minimap.")
                .translation("cartofold.configuration.minimap.shape").defineEnum("shape", Shape.SQUARE);
        MINIMAP_CORNER = builder.comment("The corner of the screen of the minimap.")
                .translation("cartofold.configuration.minimap.corner")
                .defineEnum("corner", MinimapLayout.Corner.TOP_LEFT);
        MINIMAP_SIZE = builder.comment("The size of the minimap, in units of the screen.")
                .translation("cartofold.configuration.minimap.size").defineInRange("size", 100, 48, 256);
        MINIMAP_ZOOM = builder.comment("The zoom level of the minimap, from 0 (far) to 4 (near).")
                .translation("cartofold.configuration.minimap.zoom")
                .defineInRange("zoom", 2, 0, MinimapLayout.zoomLevels() - 1);
        MINIMAP_ROTATE = builder.comment("Turn the minimap with the player. If false, the north is at the top.")
                .translation("cartofold.configuration.minimap.rotate").define("rotate", false);
        MINIMAP_COORDINATES = builder.comment("Show the coordinates of the player under the minimap.")
                .translation("cartofold.configuration.minimap.coordinates").define("coordinates", true);
        MINIMAP_WAYPOINTS = builder.comment("Show the waypoints on the minimap.")
                .translation("cartofold.configuration.minimap.waypoints").define("waypoints", true);
        builder.pop();
        builder.translation("cartofold.configuration.worldmap").push("worldmap");
        WORLD_MAP_WAYPOINTS = builder.comment("Show the waypoints on the map of the world.")
                .translation("cartofold.configuration.worldmap.waypoints").define("waypoints", true);
        WORLD_MAP_WAYPOINT_NAMES = builder.comment("Show the names of the waypoints on the map of the world.")
                .translation("cartofold.configuration.worldmap.waypoint_names").define("waypointNames", true);
        builder.pop();
        builder.translation("cartofold.configuration.layers").push("layers");
        HIDDEN_LAYERS = builder.comment("The identifiers of the layers that the views do not show.")
                .translation("cartofold.configuration.layers.hidden")
                .defineListAllowEmpty("hidden", List.of(), () -> "", value -> value instanceof String);
        builder.pop();
        SPEC = builder.build();
    }

    private CartofoldClientConfig() {
    }
}
