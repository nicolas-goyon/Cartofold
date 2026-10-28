package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.data.MapRegion;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import io.github.nicolasgoyon.cartofold.view.MapProjection;
import io.github.nicolasgoyon.cartofold.view.MinimapLayout;
import io.github.nicolasgoyon.cartofold.view.WorldMapState;
import io.github.nicolasgoyon.cartofold.waypoint.Waypoint;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

/**
 * The map of the explored world. The mouse moves the map, the wheel changes the zoom, the arrow keys move the map and
 * the space key puts the player at the center. The right button of the mouse adds a waypoint, or changes the waypoint
 * under the mouse.
 */
public final class WorldMapScreen extends Screen {
    private static final int BACKGROUND = 0xFF101010;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int HINT = 0xFFA0A0A0;
    /** An arrow key moves the map by this part of the screen. */
    private static final double KEY_MOVE = 1.0 / 8;
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
        ExplorationRequests.view(player.level().dimension(), projection.regions());
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.flush();
        MapView.render(graphics, player.level().dimension(), projection);
        WaypointMarkers.renderWorldMap(graphics, font, player.level().dimension(), projection);
        double[] playerPoint = projection.toScreen(player.getX(), player.getZ());
        MapShapes.drawArrow(graphics, (float) playerPoint[0], (float) playerPoint[1],
                (float) MinimapLayout.arrowRotation(false, player.getViewYRot(partialTick)));

        double[] block = projection.toBlock(mouseX, mouseY);
        graphics.drawString(font, Component.translatable("cartofold.worldmap.coordinates",
                (int) Math.floor(block[0]), (int) Math.floor(block[1])), 6, height - font.lineHeight - 4, TEXT);
        graphics.drawCenteredString(font, title, width / 2, 6, TEXT);
        Component hint = Component.translatable("cartofold.worldmap.hint");
        graphics.drawString(font, hint, width - font.width(hint) - 6, height - font.lineHeight - 4, HINT);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The map covers the whole screen.
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = true;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && minecraft != null && minecraft.player != null) {
            ResourceKey<Level> dimension = minecraft.player.level().dimension();
            MapProjection projection = projection();
            Optional<Waypoint> waypoint = WaypointMarkers.at(dimension, projection, mouseX, mouseY);
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
        return super.mouseClicked(mouseX, mouseY, button);
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
        ExplorationRequests.view(null, List.of());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
