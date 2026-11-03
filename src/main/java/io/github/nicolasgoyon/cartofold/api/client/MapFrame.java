package io.github.nicolasgoyon.cartofold.api.client;

import io.github.nicolasgoyon.cartofold.api.MapViewKind;
import io.github.nicolasgoyon.cartofold.client.ExplorationRequests;
import io.github.nicolasgoyon.cartofold.client.LayerRenderer;
import io.github.nicolasgoyon.cartofold.client.MapShapes;
import io.github.nicolasgoyon.cartofold.client.MapView;
import io.github.nicolasgoyon.cartofold.client.WaypointMarkers;
import io.github.nicolasgoyon.cartofold.data.ExplorationKey;
import io.github.nicolasgoyon.cartofold.data.MapSlice;
import io.github.nicolasgoyon.cartofold.network.MarkerClickPayload;
import io.github.nicolasgoyon.cartofold.view.MapProjection;
import io.github.nicolasgoyon.cartofold.view.MinimapLayout;
import io.github.nicolasgoyon.cartofold.view.WorldMapState;
import java.util.Optional;
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
import net.neoforged.neoforge.network.PacketDistributor;

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

    private final WorldMapState state;
    @Nullable
    private ResourceKey<Level> dimension;
    private Predicate<ResourceLocation> layers = layer -> true;
    private boolean interactive = true;
    private boolean showPlayer = true;
    private boolean showWaypoints = true;
    private boolean followPlayer;
    private MapSlice slice = MapSlice.SURFACE;
    private MarkerClickListener markerClick = (layer, marker) -> { };
    private Runnable emptyClick = () -> { };
    private Consumer<View> viewChange = view -> { };
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
        if (followPlayer) {
            state.center(player.getX(), player.getZ());
        }
        MapProjection projection = projection();
        ExplorationKey key = new ExplorationKey(shown, slice);
        ExplorationRequests.view(this, key, projection.regions());
        graphics.fill(getX() - 1, getY() - 1, getX() + width + 1, getY() + height + 1, BORDER);
        graphics.fill(getX(), getY(), getX() + width, getY() + height, BACKGROUND);
        graphics.flush();
        MapView.render(graphics, key, projection, MapViewKind.FRAME, layers);
        graphics.enableScissor(getX(), getY(), getX() + width, getY() + height);
        if (showWaypoints) {
            WaypointMarkers.renderWorldMap(graphics, Minecraft.getInstance().font, shown, projection, true);
        }
        if (showPlayer && player.level().dimension().equals(shown)) {
            double[] point = projection.toScreen(player.getX(), player.getZ());
            MapShapes.drawArrow(graphics, (float) point[0], (float) point[1],
                    (float) MinimapLayout.arrowRotation(false, player.getViewYRot(partialTick)));
        }
        graphics.disableScissor();
        if (isHovered()) {
            markerAt(mouseX, mouseY).flatMap(hit -> hit.marker().tooltip()).ifPresent(tooltip -> graphics
                    .renderTooltip(Minecraft.getInstance().font, tooltip, mouseX, mouseY));
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
        ResourceKey<Level> shown = shownDimension();
        if (shown != null) {
            LayerRenderer.clickableMarkerAt(shown, projection(), MapViewKind.FRAME, layers, mouseX, mouseY).ifPresent(
                    hit -> PacketDistributor.sendToServer(new MarkerClickPayload(hit.layer(), hit.marker().id())));
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        boolean click = pressed && Math.hypot(mouseX - pressX, mouseY - pressY) <= CLICK_DISTANCE;
        pressed = false;
        if (click) {
            markerAt(mouseX, mouseY).ifPresentOrElse(hit -> markerClick.clicked(hit.layer(), hit.marker().id()),
                    emptyClick);
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

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager handler) {
        // A map makes no sound.
    }
}
