package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.api.MapViewKind;
import io.github.nicolasgoyon.cartofold.view.MarkerSizes;
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

    /** The views that show the markers of the banners. */
    public enum BannerViews implements TranslatableEnum {
        MINIMAP,
        WORLD_MAP,
        BOTH;

        /**
         * True if a kind of view shows the markers of the banners. The markers in the world do not use this setting:
         * the setting {@code hud.hiddenLayers} hides them.
         */
        public boolean shows(MapViewKind kind) {
            return this == BOTH || kind == MapViewKind.HUD || (this == MINIMAP && kind == MapViewKind.MINIMAP)
                    || (this == WORLD_MAP && kind == MapViewKind.WORLD_MAP);
        }

        @Override
        public Component getTranslatedName() {
            return Component.translatable("cartofold.configuration.layers.banner_views." + name().toLowerCase(
                    Locale.ROOT));
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
    public static final ModConfigSpec.BooleanValue CAVES;
    public static final ModConfigSpec.BooleanValue WORLD_MAP_WAYPOINTS;
    public static final ModConfigSpec.BooleanValue WORLD_MAP_WAYPOINT_NAMES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> HIDDEN_LAYERS;
    public static final ModConfigSpec.EnumValue<BannerViews> BANNER_VIEWS;
    public static final ModConfigSpec.DoubleValue MARKER_SCALE;
    public static final ModConfigSpec.BooleanValue HUD;
    public static final ModConfigSpec.BooleanValue HUD_WAYPOINTS;
    public static final ModConfigSpec.IntValue HUD_MIN_DISTANCE;
    public static final ModConfigSpec.IntValue HUD_MAX_DISTANCE;
    public static final ModConfigSpec.IntValue HUD_OPACITY;
    public static final ModConfigSpec.DoubleValue HUD_HEIGHT;
    public static final ModConfigSpec.DoubleValue HUD_SCALE;
    public static final ModConfigSpec.BooleanValue HUD_NAMES;
    public static final ModConfigSpec.BooleanValue HUD_DISTANCES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> HUD_HIDDEN_LAYERS;

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
        CAVES = builder.comment("In a cave, the minimap shows the caves at the height of the player.")
                .translation("cartofold.configuration.minimap.caves").define("caves", true);
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
        BANNER_VIEWS = builder.comment("The views that show the markers of the banners.")
                .translation("cartofold.configuration.layers.banner_views")
                .defineEnum("bannerViews", BannerViews.BOTH);
        builder.pop();
        builder.translation("cartofold.configuration.markers").push("markers");
        MARKER_SCALE = builder.comment("The scale of the markers, of their names and of the arrow of the player.")
                .translation("cartofold.configuration.markers.scale")
                .defineInRange("scale", 1.0, MarkerSizes.MIN_SCALE, MarkerSizes.MAX_SCALE);
        builder.pop();
        builder.translation("cartofold.configuration.hud").push("hud");
        HUD = builder.comment("Show the waypoints and the markers in the world.")
                .translation("cartofold.configuration.hud.enabled").define("enabled", true);
        HUD_WAYPOINTS = builder.comment("Show the waypoints in the world.")
                .translation("cartofold.configuration.hud.waypoints").define("waypoints", true);
        HUD_MIN_DISTANCE = builder.comment("Hide the markers nearer than this distance, in blocks.")
                .translation("cartofold.configuration.hud.min_distance")
                .defineInRange("minDistance", 50, 0, 100_000);
        HUD_MAX_DISTANCE = builder.comment("Hide the markers farther than this distance, in blocks. 0 shows all the "
                + "far markers.").translation("cartofold.configuration.hud.max_distance")
                .defineInRange("maxDistance", 1000, 0, 100_000);
        HUD_OPACITY = builder.comment("The opacity of the markers, in percent.")
                .translation("cartofold.configuration.hud.opacity").defineInRange("opacity", 80, 10, 100);
        HUD_HEIGHT = builder.comment("The height of a marker above its position, in blocks.")
                .translation("cartofold.configuration.hud.height").defineInRange("height", 1.0, 0.0, 64.0);
        HUD_SCALE = builder.comment("The size of the markers: 1 is the normal size.")
                .translation("cartofold.configuration.hud.scale").defineInRange("scale", 1.0, 0.5, 4.0);
        HUD_NAMES = builder.comment("Show the names of the markers.")
                .translation("cartofold.configuration.hud.names").define("names", true);
        HUD_DISTANCES = builder.comment("Show the distances of the markers.")
                .translation("cartofold.configuration.hud.distances").define("distances", true);
        HUD_HIDDEN_LAYERS = builder.comment("The identifiers of the layers that do not show in the world.")
                .translation("cartofold.configuration.hud.hidden_layers")
                .defineListAllowEmpty("hiddenLayers", List.of(), () -> "", value -> value instanceof String);
        builder.pop();
        SPEC = builder.build();
    }

    private CartofoldClientConfig() {
    }

    /** The sizes of the markers, with the scale of the setting. */
    public static MarkerSizes markerSizes() {
        return new MarkerSizes(MARKER_SCALE.get());
    }
}
