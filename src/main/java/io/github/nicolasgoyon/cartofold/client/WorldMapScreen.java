package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.api.MapViewKind;
import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.network.MarkerClickPayload;
import io.github.nicolasgoyon.cartofold.view.MapProjection;
import io.github.nicolasgoyon.cartofold.view.MinimapLayout;
import io.github.nicolasgoyon.cartofold.view.WorldMapState;
import io.github.nicolasgoyon.cartofold.waypoint.Waypoint;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The map of the explored world. The mouse moves the map, the wheel changes the zoom, the arrow keys move the map and
 * the space key puts the player at the center. The right button of the mouse adds a waypoint, or changes the waypoint
 * under the mouse. The left button on a marker of a layer sends the click to the server, and the "Layers" button shows
 * and hides the layers.
 */
public final class WorldMapScreen extends Screen {
    private static final int BACKGROUND = 0xFF101010;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int HINT = 0xFFA0A0A0;
    /** An arrow key moves the map by this part of the screen. */
    private static final double KEY_MOVE = 1.0 / 8;
    private static final int SETTINGS_WIDTH = 80;
    /** The state of the last map: the next map opens at the same scale. */
    @Nullable
    private static WorldMapState last;

    private final WorldMapState state;
    private boolean dragging;

    public WorldMapScreen(LocalPlayer player) {
        super(Component.translatable("cartofold.worldmap.title"));
        double scale = last == null ? 1 : last.scale();
        this.state = new WorldMapState(player.getX(), player.getZ(), scale);
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("cartofold.worldmap.settings"), button -> {
            if (minecraft != null) {
                ModList.get().getModContainerById(Cartofold.MOD_ID).ifPresent(container ->
                        minecraft.setScreen(new ConfigurationScreen(container, this)));
            }
        }).bounds(width - SETTINGS_WIDTH - 6, 4, SETTINGS_WIDTH, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("cartofold.worldmap.layers"),
                button -> minecraft.setScreen(new LayersScreen(this)))
                .bounds(width - SETTINGS_WIDTH * 2 - 10, 4, SETTINGS_WIDTH, 20).build());
    }

    private MapProjection projection() {
        return state.projection(0, 0, width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        LocalPlayer player = minecraft == null ? null : minecraft.player;
        if (player == null) {
            onClose();
            return;
        }
        state.track(player.getX(), player.getZ());
        MapProjection projection = projection();
        ExplorationRequests.view(this, player.level().dimension(), projection.regions());
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.flush();
        MapView.render(graphics, player.level().dimension(), projection, MapViewKind.WORLD_MAP);
        if (CartofoldClientConfig.WORLD_MAP_WAYPOINTS.get()) {
            WaypointMarkers.renderWorldMap(graphics, font, player.level().dimension(), projection,
                    CartofoldClientConfig.WORLD_MAP_WAYPOINT_NAMES.get());
        }
        double[] playerPoint = projection.toScreen(player.getX(), player.getZ());
        MapShapes.drawArrow(graphics, (float) playerPoint[0], (float) playerPoint[1],
                (float) MinimapLayout.arrowRotation(false, player.getViewYRot(partialTick)));

        double[] block = projection.toBlock(mouseX, mouseY);
        graphics.drawString(font, Component.translatable("cartofold.worldmap.coordinates",
                (int) Math.floor(block[0]), (int) Math.floor(block[1])), 6, height - font.lineHeight - 4, TEXT);
        graphics.drawCenteredString(font, title, width / 2, 6, TEXT);
        Component hint = Component.translatable("cartofold.worldmap.hint");
        graphics.drawString(font, hint, width - font.width(hint) - 6, height - font.lineHeight - 4, HINT);
        super.render(graphics, mouseX, mouseY, partialTick);
        LayerRenderer.markerAt(player.level().dimension(), projection, MapViewKind.WORLD_MAP, layer -> true, mouseX, mouseY)
                .flatMap(hit -> hit.marker().tooltip())
                .ifPresent(tooltip -> graphics.renderTooltip(font, tooltip, mouseX, mouseY));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The map covers the whole screen.
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (minecraft != null && minecraft.player != null) {
                Optional<LayerRenderer.Hit> hit = LayerRenderer.markerAt(minecraft.player.level().dimension(),
                        projection(), MapViewKind.WORLD_MAP, layer -> true, mouseX, mouseY).filter(found -> found.marker()
                                .clickable());
                if (hit.isPresent()) {
                    PacketDistributor.sendToServer(new MarkerClickPayload(hit.get().layer(), hit.get().marker()
                            .id()));
                    return true;
                }
            }
            dragging = true;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && minecraft != null && minecraft.player != null) {
            ResourceKey<Level> dimension = minecraft.player.level().dimension();
            MapProjection projection = projection();
            Optional<Waypoint> waypoint = CartofoldClientConfig.WORLD_MAP_WAYPOINTS.get()
                    ? WaypointMarkers.at(dimension, projection, mouseX, mouseY) : Optional.empty();
            if (waypoint.isPresent()) {
                minecraft.setScreen(WaypointScreen.edit(this, waypoint.get()));
            } else {
                double[] block = projection.toBlock(mouseX, mouseY);
                int blockX = (int) Math.floor(block[0]);
                int blockZ = (int) Math.floor(block[1]);
                minecraft.setScreen(WaypointScreen.add(this, dimension,
                        new BlockPos(blockX, surface(dimension, blockX, blockZ), blockZ)));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            state.drag(dragX, dragY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        state.zoom(projection(), mouseX, mouseY, scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        double step = Math.min(width, height) * KEY_MOVE;
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> state.drag(step, 0);
            case GLFW.GLFW_KEY_RIGHT -> state.drag(-step, 0);
            case GLFW.GLFW_KEY_UP -> state.drag(0, step);
            case GLFW.GLFW_KEY_DOWN -> state.drag(0, -step);
            case GLFW.GLFW_KEY_SPACE -> {
                if (minecraft != null && minecraft.player != null) {
                    state.center(minecraft.player.getX(), minecraft.player.getZ());
                }
            }
            default -> {
                if (CartofoldClient.OPEN_MAP.matches(keyCode, scanCode)) {
                    onClose();
                    return true;
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    /** The Y of the top block of a column, from the exploration data, or the Y of the player. */
    private int surface(ResourceKey<Level> dimension, int blockX, int blockZ) {
        int fallback = minecraft == null || minecraft.player == null ? 64 : minecraft.player.getBlockY();
        return ClientExploration.get().region(dimension, RegionPos.ofBlock(blockX, blockZ))
                .filter(region -> region.sampled(blockX >> 4, blockZ >> 4) != MapRegion.NEVER)
                .map(region -> region.height(blockX, blockZ) + 1).orElse(fallback);
    }

    @Override
    public void removed() {
        last = state;
        ExplorationRequests.close(this);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
