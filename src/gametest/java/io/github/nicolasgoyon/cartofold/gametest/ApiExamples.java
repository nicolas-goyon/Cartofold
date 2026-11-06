package io.github.nicolasgoyon.cartofold.gametest;

import io.github.nicolasgoyon.cartofold.api.CartofoldLayers;
import io.github.nicolasgoyon.cartofold.api.ChunkArea;
import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.api.LayerInfo;
import io.github.nicolasgoyon.cartofold.api.LayerUpdate;
import io.github.nicolasgoyon.cartofold.api.MapLine;
import io.github.nicolasgoyon.cartofold.api.MapMarker;
import io.github.nicolasgoyon.cartofold.api.MapViewKind;
import io.github.nicolasgoyon.cartofold.api.MarkerClickEvent;
import io.github.nicolasgoyon.cartofold.api.MarkerShapes;
import io.github.nicolasgoyon.cartofold.api.PaperMapMarkers;
import io.github.nicolasgoyon.cartofold.api.PaperMarkerSource;
import io.github.nicolasgoyon.cartofold.api.client.CartofoldClientLayers;
import io.github.nicolasgoyon.cartofold.api.client.CartofoldWorldMap;
import io.github.nicolasgoyon.cartofold.api.client.LayerRenderContext;
import io.github.nicolasgoyon.cartofold.api.client.MapFrame;
import io.github.nicolasgoyon.cartofold.api.client.MapLayerRenderer;
import io.github.nicolasgoyon.cartofold.paper.CartofoldMapDecorations;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The examples of the documentation site (docs/api-examples.md). The site includes the sections between the marks. The
 * build compiles this class, so the examples follow the API. No test calls it.
 */
public final class ApiExamples {
    // --8<-- [start:constants]
    private static final ResourceLocation TERRITORIES = ResourceLocation.fromNamespaceAndPath("mymod", "territories");
    private static final ResourceLocation CARAVANS = ResourceLocation.fromNamespaceAndPath("mymod", "caravans");
    private static final int FACTION_COLOR = 0xFF4040;
    // --8<-- [end:constants]

    private ApiExamples() {
    }

    // --8<-- [start:territories]
    /** A layer for all the players: one territory, one road and one village. */
    public static void showTerritories(BlockPos heart, BlockPos gate) {
        LayerInfo info = LayerInfo.of(TERRITORIES, Component.literal("Territories"), 10, true);

        ChunkArea territory = ChunkArea.of("north_village", Level.OVERWORLD,
                List.of(new ChunkPos(heart), new ChunkPos(heart.east(16))),
                0x40FF4040, // the fill, in ARGB
                0xFFFF4040, // the border, in ARGB
                Component.literal("North village"),
                List.of(Component.literal("North village"),
                        Component.literal("Red faction").withColor(FACTION_COLOR)));

        MapLine road = new MapLine("north_road", Level.OVERWORLD, List.of(heart, gate), 0xFFE0C080, 2);

        MapMarker village = MapMarker.tinted("north_village", Level.OVERWORLD, heart, FACTION_COLOR,
                MarkerShapes.SQUARE, Component.literal("North village"));

        CartofoldLayers.setForAll(new LayerContent(info, List.of(territory), List.of(road), List.of(village)));
    }
    // --8<-- [end:territories]

    // --8<-- [start:player-layer]
    /** A layer for one player, with a clickable marker. Only the frames and the map of the world show it. */
    public static void showQuest(ServerPlayer player, BlockPos target) {
        ResourceLocation layer = ResourceLocation.fromNamespaceAndPath("mymod", "quest");
        LayerInfo info = new LayerInfo(layer, Component.literal("Quest"), 20,
                Set.of(MapViewKind.WORLD_MAP, MapViewKind.FRAME), false);

        MapMarker marker = MapMarker.tinted("target", player.level().dimension(), target, 0xFFD020,
                MarkerShapes.CROSS, Component.literal("Target"))
                .withTooltip(List.of(Component.literal("Find the lost map"),
                        Component.literal("Click to open the quest").withStyle(ChatFormatting.GRAY)))
                .withClick(true);

        CartofoldLayers.set(player, new LayerContent(info, List.of(), List.of(), List.of(marker)));
    }
    // --8<-- [end:player-layer]

    // --8<-- [start:clicks]
    /** A click on a clickable marker comes on the server. Call this method once, in the setup of the mod. */
    public static void listenToClicks() {
        NeoForge.EVENT_BUS.addListener(MarkerClickEvent.class, event -> {
            if (event.getMarker().id().equals("target")) {
                event.getPlayer().sendSystemMessage(Component.literal("Quest: " + event.getLayer()));
            }
        });
    }
    // --8<-- [end:clicks]

    // --8<-- [start:updates]
    /** Moves one marker. The server sends only this marker, and the other elements of the layer stay. */
    public static void moveCaravan(String id, BlockPos pos) {
        MapMarker caravan = MapMarker.tinted(id, Level.OVERWORLD, pos, FACTION_COLOR, MarkerShapes.DIAMOND,
                Component.literal("Caravan"));
        CartofoldLayers.updateForAll(CARAVANS, LayerUpdate.marker(caravan));
    }

    /** Removes one marker of the layer. */
    public static void removeCaravan(String id) {
        CartofoldLayers.updateForAll(CARAVANS, LayerUpdate.removeMarker(id));
    }

    /** Moves many markers and removes others in one message. */
    public static void changeCaravans(List<MapMarker> moved, List<String> arrived) {
        CartofoldLayers.updateForAll(CARAVANS,
                new LayerUpdate(List.of(), List.of(), moved, List.of(), List.of(), arrived));
    }
    // --8<-- [end:updates]

    // --8<-- [start:paper-source]
    /** A block of the mod marks the survey maps. Call this method once, in the setup of the mod. */
    public static void registerPaperMarkers() {
        PaperMapMarkers.register(PaperMarkerSource.of(
                ResourceLocation.fromNamespaceAndPath("mymod", "beacon"),
                state -> state.is(Blocks.BEACON),
                CartofoldMapDecorations.WAYPOINT,
                (level, pos) -> Optional.of(Component.literal("Beacon"))));
    }
    // --8<-- [end:paper-source]

    // --8<-- [start:client-layer]
    /** A layer that the client draws itself. Call this method once, in the setup of the client. */
    public static void registerWeatherLayer(double stormX, double stormZ) {
        CartofoldClientLayers.register(new MapLayerRenderer() {
            @Override
            public LayerInfo info() {
                return LayerInfo.of(ResourceLocation.fromNamespaceAndPath("mymod", "weather"),
                        Component.literal("Weather"), 20, false);
            }

            @Override
            public void render(LayerRenderContext context) {
                double[] point = context.projection().toScreen(stormX, stormZ);
                context.graphics().fill((int) point[0] - 2, (int) point[1] - 2, (int) point[0] + 2,
                        (int) point[1] + 2, 0xFF4060FF);
            }
        });
    }
    // --8<-- [end:client-layer]

    // --8<-- [start:journal]
    /** A journal: a map with a selection, a saved view and buttons. */
    public static final class JournalScreen extends Screen {
        /** The view stays between two openings of the journal. */
        @Nullable
        private static MapFrame.View savedView;

        @Nullable
        private String selected;
        @Nullable
        private MapFrame frame;
        @Nullable
        private Button worldMap;

        public JournalScreen() {
            super(Component.literal("Journal"));
        }

        @Override
        protected void init() {
            frame = addRenderableWidget(new MapFrame(20, 30, 240, 160)
                    .layers(layer -> layer.getNamespace().equals("mymod"))
                    .showWaypoints(false)
                    .onViewChange(view -> savedView = view)
                    .onMarkerClick((layer, marker) -> select(layer, marker))
                    .onEmptyClick(this::clearSelection));
            if (savedView != null) {
                frame.view(savedView);
            }
            addRenderableWidget(Button.builder(Component.literal("All territories"),
                    button -> frame.showBox(-1000, -1000, 1000, 1000)).bounds(270, 30, 100, 20).build());
            worldMap = addRenderableWidget(Button.builder(Component.literal("World map"), button -> openWorldMap())
                    .bounds(270, 54, 100, 20).build());
        }

        private void select(ResourceLocation layer, String marker) {
            selected = marker;
            if (frame != null) {
                frame.select(layer, marker);
            }
            if (worldMap != null) {
                worldMap.active = true;
            }
        }

        private void clearSelection() {
            selected = null;
            if (frame != null) {
                frame.clearSelection();
            }
            if (worldMap != null) {
                worldMap.active = false;
            }
        }

        /** Escape on the map of the world shows this screen again. */
        private void openWorldMap() {
            if (frame != null && selected != null) {
                CartofoldWorldMap.at(Level.OVERWORLD, frame.view().centerX(), frame.view().centerZ())
                        .scale(2)
                        .parent(this)
                        .select(TERRITORIES, selected)
                        .open();
            }
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
    // --8<-- [end:journal]

    // --8<-- [start:admin-frame]
    /** A frame of an operator: the chunks of all the players, on a background of the mod. */
    public static MapFrame adminFrame(int x, int y, int width, int height) {
        return new MapFrame(x, y, width, height)
                .allChunks(true) // The server accepts this for an operator only.
                .backgroundColor(0xFF2A3A2A)
                .background(context -> {
                    // Draw the relief of the mod here, with context.projection().
                    double[] origin = context.projection().toScreen(0, 0);
                    context.graphics().fill((int) origin[0] - 1, (int) origin[1] - 1, (int) origin[0] + 1,
                            (int) origin[1] + 1, 0xFFFFFFFF);
                })
                .center(0, 0)
                .scale(0.25);
    }
    // --8<-- [end:admin-frame]
}
