package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.view.LabelLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The names of the markers of one draw of a map. The views add the names, then {@link #draw} shows them above all the
 * markers. A name does not cover a name that the map already drew. The selected marker, the marker under the mouse and
 * the waypoint under the mouse always show their names. The other names go in the inverse order of their additions:
 * the highest layer, and the waypoints, come first.
 */
public final class MapLabels {
    private static final int COLOR = 0xFFFFFFFF;

    /** A marker of a layer. */
    public record Key(ResourceLocation layer, String marker) {
    }

    private record Label(Component name, double x, double y) {
    }

    @Nullable
    private final Key hovered;
    @Nullable
    private final Key selected;
    private final List<Label> priority = new ArrayList<>();
    private final List<Label> others = new ArrayList<>();

    /**
     * Makes the names of one draw.
     *
     * @param hovered  the marker under the mouse, or null
     * @param selected the selected marker, or null
     */
    public MapLabels(@Nullable Key hovered, @Nullable Key selected) {
        this.hovered = hovered;
        this.selected = selected;
    }

    /** True if a marker is the selected marker. */
    public boolean selected(ResourceLocation layer, String marker) {
        return selected != null && selected.equals(new Key(layer, marker));
    }

    /** Adds the name of a marker of a layer, with its center at the top of the point. */
    public void add(ResourceLocation layer, String marker, Component name, double x, double y) {
        Key key = new Key(layer, marker);
        add(name, x, y, Objects.equals(key, hovered) || Objects.equals(key, selected));
    }

    /**
     * Adds a name, with its center at the top of the point.
     *
     * @param priority true if the name always shows
     */
    public void add(Component name, double x, double y, boolean priority) {
        if (!name.getString().isEmpty()) {
            (priority ? this.priority : others).add(new Label(name, x, y));
        }
    }

    /** Draws the names. */
    public void draw(GuiGraphics graphics, Font font) {
        double scale = CartofoldClientConfig.markerSizes().name();
        double height = font.lineHeight * scale;
        LabelLayout layout = new LabelLayout();
        for (Label label : priority) {
            double half = font.width(label.name()) * scale / 2;
            layout.force(label.x() - half, label.y(), label.x() + half, label.y() + height);
            drawName(graphics, font, label, scale);
        }
        for (Label label : others.reversed()) {
            double half = font.width(label.name()) * scale / 2;
            if (layout.place(label.x() - half, label.y(), label.x() + half, label.y() + height)) {
                drawName(graphics, font, label, scale);
            }
        }
    }

    /** Draws a name at its point, with no rounding: the name stays under its marker when the map moves. */
    private static void drawName(GuiGraphics graphics, Font font, Label label, double scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(label.x(), label.y(), 0);
        graphics.pose().scale((float) scale, (float) scale, 1);
        graphics.drawCenteredString(font, label.name(), 0, 0, COLOR);
        graphics.pose().popPose();
    }
}
