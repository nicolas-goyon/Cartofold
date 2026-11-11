package io.github.nicolasgoyon.cartofold.api.client;

import io.github.nicolasgoyon.cartofold.api.MapViewKind;
import io.github.nicolasgoyon.cartofold.client.ExplorationRequests;
import io.github.nicolasgoyon.cartofold.client.LayerRenderer;
import io.github.nicolasgoyon.cartofold.client.MapLabels;
import io.github.nicolasgoyon.cartofold.client.MapShapes;
import io.github.nicolasgoyon.cartofold.client.MapView;
import io.github.nicolasgoyon.cartofold.client.WaypointMarkers;
import io.github.nicolasgoyon.cartofold.data.ExplorationKey;
import io.github.nicolasgoyon.cartofold.data.MapSlice;
import io.github.nicolasgoyon.cartofold.network.MarkerClickPayload;
import io.github.nicolasgoyon.cartofold.view.MapProjection;
import io.github.nicolasgoyon.cartofold.view.MinimapLayout;
import io.github.nicolasgoyon.cartofold.view.WorldMapState;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * A map frame for the screens of the other mods: a widget that shows the explored map, the layers of a selection and
 * the player. Add it to a screen with {@code addRenderableWidget}.
 *
 * <pre>{@code
 * MapFrame frame = new MapFrame(left, top, 200, 150)
 *         .center(villageX, villageZ)
 *         .scale(0.5)
 *         .layers(id -> id.getNamespace().equals("mymod"));
 * addRenderableWidget(frame);
 * }</pre>
 *
 * <p>The frame shows the layers with the kind {@link MapViewKind#FRAME}. When the frame is interactive, the mouse moves
 * the map, the wheel changes the zoom, and a click on a clickable marker sends a
 * {@link io.github.nicolasgoyon.cartofold.api.MarkerClickEvent} on the server. The screen can also get the clicks on
 * the client, with no message to the server (see {@link #onMarkerClick} and {@link #onEmptyClick}).
 */
public class MapFrame extends AbstractWidget {
    private static final int BACKGROUND = 0xFF101010;
    private static final int BORDER = 0xFF404040;
    /** A press and a release with a larger move than this distance, in units of the screen, are a drag. */
    private static final double CLICK_DISTANCE = 2;
    /** An arrow key moves the view by this part of the smallest side of the frame. */
    private static final double KEY_MOVE = 1.0 / 8;
    /** The free space around a box of {@link #showBox}, in units of the screen. */
    private static final double BOX_MARGIN = 8;

    /**
     * The view of a frame. A screen can keep it and give it again to a new frame.
     *
     * @param centerX the X of the block at the center
     * @param centerZ the Z of the block at the center
     * @param scale   the number of units of the screen for one block
     */
    public record View(double centerX, double centerZ, double scale) {
    }

    /** Gets the clicks on the markers of a frame, on the client. */
    @FunctionalInterface
    public interface MarkerClickListener {
        /**
         * The player clicked a marker.
         *
         * @param layer  the identifier of the layer of the marker
         * @param marker the identifier of the marker in its layer
         */
        void clicked(ResourceLocation layer, String marker);
    }

    /** Handles the clicks on the markers of a frame on the client, and can keep them from the server. */
    @FunctionalInterface
    public interface MarkerClickHandler {
        /**
         * The player clicked a marker.
         *
         * @param layer  the identifier of the layer of the marker
         * @param marker the identifier of the marker in its layer
         * @return true to keep the click on the client: the server gets no {@link
         *     io.github.nicolasgoyon.cartofold.api.MarkerClickEvent}
         */
        boolean clicked(ResourceLocation layer, String marker);
    }

    private final WorldMapState state;
    @Nullable
    private ResourceKey<Level> dimension;
    private Predicate<ResourceLocation> layers = layer -> true;
    private boolean interactive = true;
    private boolean showPlayer = true;
    private boolean showWaypoints = true;
    private boolean followPlayer;
    private boolean allChunks;
    private int backgroundColor = BACKGROUND;
    @Nullable
    private Consumer<LayerRenderContext> background;
    private MapSlice slice = MapSlice.SURFACE;
    private MarkerClickListener markerClick = (layer, marker) -> { };
    private MarkerClickHandler markerHandler = (layer, marker) -> false;
    private Runnable emptyClick = () -> { };
    private Consumer<View> viewChange = view -> { };
    private final Set<MapLabels.Key> selected = new LinkedHashSet<>();
    private boolean pressed;
    private double pressX;
    private double pressY;

    /** Makes a frame centered on the player, at one unit of the screen for one block. */
    public MapFrame(int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("cartofold.frame"));
        LocalPlayer player = Minecraft.getInstance().player;
        this.state = new WorldMapState(player == null ? 0 : player.getX(), player == null ? 0 : player.getZ(), 1);
    }

    /** Puts a block at the center. The frame stops to follow the player. */
    public MapFrame center(double blockX, double blockZ) {
        state.center(blockX, blockZ);
        followPlayer = false;
        return this;
    }

    /**
     * Sets the scale: the number of units of the screen for one block, from {@link WorldMapState#MIN_SCALE} to
     * {@link WorldMapState#MAX_SCALE}.
     */
    public MapFrame scale(double scale) {
        state.zoom(projection(), getX() + width / 2.0, getY() + height / 2.0,
                Math.log(scale / state.scale()) / Math.log(WorldMapState.ZOOM_FACTOR));
        return this;
    }

    /** Shows a dimension. By default, the frame shows the dimension of the player. */
    public MapFrame dimension(@Nullable ResourceKey<Level> dimension) {
        this.dimension = dimension;
        return this;
    }

    /** Shows a slice: the surface (by default) or a band of caves. */
    public MapFrame slice(MapSlice slice) {
        this.slice = slice;
        return this;
    }

    /**
     * Sets the color of the frame where the map has no chunk.
     *
     * @param color the color, in ARGB
     */
    public MapFrame backgroundColor(int color) {
        this.backgroundColor = color;
        return this;
    }

    /**
     * Sets a function that draws the background of the frame, below the chunks and below the layers. The frame calls it
     * in each draw, with a scissor on the frame. The context has the projection of the frame.
     */
    public MapFrame background(Consumer<LayerRenderContext> renderer) {
        this.background = renderer;
        return this;
    }

    /**
     * Shows all the chunks with a sample, also the chunks that the player did not explore. The server accepts this
     * request only from an operator (permission level 2): for other players, the frame shows the explored chunks. The
     * chunks that no player explored have no sample: the background shows there.
     */
    public MapFrame allChunks(boolean all) {
        this.allChunks = all;
        return this;
    }

    /** Shows only the layers that the filter accepts, among the layers that the player shows. */
    public MapFrame layers(Predicate<ResourceLocation> filter) {
        this.layers = filter;
        return this;
    }

    /** Allows or stops the moves, the zoom and the clicks of the mouse. By default, they are allowed. */
    public MapFrame interactive(boolean interactive) {
        this.interactive = interactive;
        return this;
    }

    /** Shows or hides the arrow of the player. By default, it shows. */
    public MapFrame showPlayer(boolean show) {
        this.showPlayer = show;
        return this;
    }

    /** Shows or hides the waypoints of the player. By default, they show. */
    public MapFrame showWaypoints(boolean show) {
        this.showWaypoints = show;
        return this;
    }

    /** Puts the player at the center in each frame, until a move of the mouse. */
    public MapFrame followPlayer(boolean follow) {
        this.followPlayer = follow;
        return this;
    }

    /**
     * Sets the function that gets the clicks on the markers of the frame, on the client. It gets the clicks on all the
     * markers, also on the markers that are not clickable. The frame calls it when the player releases the mouse, and
     * not after a drag of the map.
     */
    public MapFrame onMarkerClick(MarkerClickListener listener) {
        this.markerClick = listener;
        return this;
    }

    /**
     * Sets the function that handles the clicks on the markers of the frame, on the client. When it returns true, the
     * server gets no {@link io.github.nicolasgoyon.cartofold.api.MarkerClickEvent} for this click. The frame calls it
     * before the function of {@link #onMarkerClick}.
     */
    public MapFrame handleMarkerClick(MarkerClickHandler handler) {
        this.markerHandler = handler;
        return this;
    }

    /** The center and the scale of the frame now. */
    public View view() {
        return new View(state.centerX(), state.centerZ(), state.scale());
    }

    /** Sets the center and the scale. The frame stops to follow the player. */
    public MapFrame view(View view) {
        state.set(view.centerX(), view.centerZ(), view.scale());
        followPlayer = false;
        return this;
    }

    /**
     * Selects the center and the scale that show all of a box of blocks. The scale stays between
     * {@link WorldMapState#MIN_SCALE} and {@link WorldMapState#MAX_SCALE}. The frame stops to follow the player.
     *
     * @param minX the smallest X of the box
     * @param minZ the smallest Z of the box
     * @param maxX the largest X of the box
     * @param maxZ the largest Z of the box
     */
    public MapFrame showBox(double minX, double minZ, double maxX, double maxZ) {
        state.fit(minX, minZ, maxX, maxZ, width, height, BOX_MARGIN);
        followPlayer = false;
        return this;
    }

    /** Sets the function that gets the view of the frame when the player moves or zooms the frame. */
    public MapFrame onViewChange(Consumer<View> listener) {
        this.viewChange = listener;
        return this;
    }

    /** Sets the function that gets the clicks that hit no marker, on the client (see {@link #onMarkerClick}). */
    public MapFrame onEmptyClick(Runnable listener) {
        this.emptyClick = listener;
        return this;
    }

    /**
     * Shows a marker as selected: the marker has a mark, and its name always shows.
     *
     * @param layer  the identifier of the layer of the marker
     * @param marker the identifier of the marker in its layer
     */
    public MapFrame select(ResourceLocation layer, String marker) {
        selected.clear();
        selected.add(new MapLabels.Key(layer, marker));
        return this;
    }

    /**
     * Adds a marker to the selection: the other selected markers stay selected. For example, a screen marks the two ends
     * of a route.
     *
     * @param layer  the identifier of the layer of the marker
     * @param marker the identifier of the marker in its layer
     */
    public MapFrame addSelection(ResourceLocation layer, String marker) {
        selected.add(new MapLabels.Key(layer, marker));
        return this;
    }

    /** Removes a marker from the selection. */
    public MapFrame removeSelection(ResourceLocation layer, String marker) {
        selected.remove(new MapLabels.Key(layer, marker));
        return this;
    }

    /** True if a marker is in the selection. */
    public boolean isSelected(ResourceLocation layer, String marker) {
        return selected.contains(new MapLabels.Key(layer, marker));
    }

    /** Shows no marker as selected. */
    public MapFrame clearSelection() {
        selected.clear();
        return this;
    }

    /** The projection of the frame: the screen point of a block and the block of a screen point. */
    public MapProjection projection() {
        return state.projection(getX(), getY(), width, height);
    }

    /** The dimension that the frame shows, or null with no player. */
    @Nullable
    public ResourceKey<Level> shownDimension() {
        LocalPlayer player = Minecraft.getInstance().player;
        return dimension != null ? dimension : player == null ? null : player.level().dimension();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ResourceKey<Level> shown = shownDimension();
        LocalPlayer player = Minecraft.getInstance().player;
        if (shown == null || player == null) {
            return;
        }
        Vec3 position = player.getPosition(partialTick);
        if (followPlayer) {
            state.center(position.x, position.z);
        }
        MapProjection projection = projection();
        ExplorationKey key = new ExplorationKey(shown, slice);
        ExplorationRequests.view(this, key, projection.regions(), allChunks);
        graphics.fill(getX() - 1, getY() - 1, getX() + width + 1, getY() + height + 1, BORDER);
        graphics.fill(getX(), getY(), getX() + width, getY() + height, backgroundColor);
        if (background != null) {
            graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
            background.accept(new LayerRenderContext(graphics, projection, shown, MapViewKind.FRAME));
            graphics.flush();
            graphics.disableScissor();
        }
        graphics.flush();
        MapLabels labels = new MapLabels(isHovered() ? markerAt(mouseX, mouseY).map(LayerRenderer.Hit::key)
                .orElse(null) : null, selected);
        MapView.render(graphics, key, projection, MapViewKind.FRAME, layers, labels);
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
        if (showWaypoints) {
            WaypointMarkers.renderWorldMap(graphics, shown, projection, labels, mouseX, mouseY);
        }
        labels.draw(graphics, Minecraft.getInstance().font);
        if (showPlayer && player.level().dimension().equals(shown)) {
            double[] point = projection.toScreen(position.x, position.z);
            MapShapes.drawArrow(graphics, (float) point[0], (float) point[1],
                    (float) MinimapLayout.arrowRotation(false, player.getViewYRot(partialTick)));
        }
        graphics.disableScissor();
        if (isHovered()) {
            graphics.renderComponentTooltip(Minecraft.getInstance().font, LayerRenderer.tooltipAt(shown, projection,
                    MapViewKind.FRAME, layers, mouseX, mouseY), mouseX, mouseY);
        }
    }

    private Optional<LayerRenderer.Hit> markerAt(double mouseX, double mouseY) {
        ResourceKey<Level> shown = shownDimension();
        return shown == null ? Optional.empty()
                : LayerRenderer.markerAt(shown, projection(), MapViewKind.FRAME, layers, mouseX, mouseY);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        if (!interactive) {
            return;
        }
        pressed = true;
        pressX = mouseX;
        pressY = mouseY;
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        boolean click = pressed && Math.hypot(mouseX - pressX, mouseY - pressY) <= CLICK_DISTANCE;
        pressed = false;
        if (!click) {
            return;
        }
        Optional<LayerRenderer.Hit> hit = markerAt(mouseX, mouseY);
        if (hit.isEmpty()) {
            emptyClick.run();
            return;
        }
        boolean kept = markerHandler.clicked(hit.get().layer(), hit.get().marker().id());
        markerClick.clicked(hit.get().layer(), hit.get().marker().id());
        ResourceKey<Level> shown = shownDimension();
        if (!kept && shown != null) {
            // The nearest clickable marker: a marker with no click near the mouse does not take the click.
            LayerRenderer.clickableMarkerAt(shown, projection(), MapViewKind.FRAME, layers, mouseX, mouseY).ifPresent(
                    found -> PacketDistributor.sendToServer(new MarkerClickPayload(found.layer(), found.marker()
                            .id())));
        }
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (interactive) {
            pressed = pressed && Math.hypot(mouseX - pressX, mouseY - pressY) <= CLICK_DISTANCE;
            state.drag(dragX, dragY);
            followPlayer = false;
            viewChange.accept(view());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!interactive || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        state.zoom(projection(), mouseX, mouseY, scrollY);
        viewChange.accept(view());
        return true;
    }

    /**
     * When the frame has the focus, the arrow keys move the view by a part of the frame, and + and - zoom at the center.
     * A screen gives the focus to the frame with a click on the frame, or with Tab.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!interactive || !isFocused()) {
            return false;
        }
        double step = Math.min(width, height) * KEY_MOVE;
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> state.drag(step, 0);
            case GLFW.GLFW_KEY_RIGHT -> state.drag(-step, 0);
            case GLFW.GLFW_KEY_UP -> state.drag(0, step);
            case GLFW.GLFW_KEY_DOWN -> state.drag(0, -step);
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> state.zoom(projection(), getX() + width / 2.0,
                    getY() + height / 2.0, 1);
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> state.zoom(projection(), getX() + width / 2.0,
                    getY() + height / 2.0, -1);
            default -> {
                return false;
            }
        }
        followPlayer = false;
        viewChange.accept(view());
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
        // A map makes no sound.
    }
}
