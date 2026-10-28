package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.network.WaypointActionPayload;
import io.github.nicolasgoyon.cartofold.waypoint.Waypoint;
import io.github.nicolasgoyon.cartofold.waypoint.Waypoints;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/** Adds or changes a waypoint: its name, its position and its color. A waypoint that exists can also be removed. */
public final class WaypointScreen extends Screen {
    private static final int FIELD_WIDTH = 200;
    private static final int COORDINATE_WIDTH = 64;
    private static final int ROW = 24;

    @Nullable
    private final Screen parent;
    private final Waypoint original;
    private final boolean exists;
    private int color;
    @Nullable
    private EditBox name;
    @Nullable
    private EditBox x;
    @Nullable
    private EditBox y;
    @Nullable
    private EditBox z;

    private WaypointScreen(@Nullable Screen parent, Waypoint original, boolean exists) {
        super(Component.translatable(exists ? "cartofold.waypoint.edit" : "cartofold.waypoint.add"));
        this.parent = parent;
        this.original = original;
        this.exists = exists;
        this.color = original.color();
    }

    /** A screen to add a waypoint at a position, with a random color. */
    public static WaypointScreen add(@Nullable Screen parent, ResourceKey<Level> dimension, BlockPos pos) {
        DyeColor[] colors = DyeColor.values();
        int color = colors[ThreadLocalRandom.current().nextInt(colors.length)].getTextColor();
        Waypoint waypoint = new Waypoint(UUID.randomUUID(),
                Component.translatable("cartofold.waypoint").getString(), color, dimension, pos, false);
        return new WaypointScreen(parent, waypoint, false);
    }

    /** A screen to change or to remove a waypoint. */
    public static WaypointScreen edit(@Nullable Screen parent, Waypoint waypoint) {
        return new WaypointScreen(parent, waypoint, true);
    }

    @Override
    protected void init() {
        int left = width / 2 - FIELD_WIDTH / 2;
        int top = height / 2 - ROW * 3;
        name = addRenderableWidget(new EditBox(font, left, top, FIELD_WIDTH, 20,
                Component.translatable("cartofold.waypoint.name")));
        name.setMaxLength(Waypoints.MAX_NAME);
        name.setValue(original.name().isEmpty() ? WaypointMarkers.name(original).getString() : original.name());
        int gap = (FIELD_WIDTH - COORDINATE_WIDTH * 3) / 2;
        x = coordinate(left, top + ROW, original.pos().getX(), "X");
        y = coordinate(left + COORDINATE_WIDTH + gap, top + ROW, original.pos().getY(), "Y");
        z = coordinate(left + (COORDINATE_WIDTH + gap) * 2, top + ROW, original.pos().getZ(), "Z");
        addRenderableWidget(Button.builder(Component.translatable("cartofold.waypoint.color"), button -> nextColor())
                .bounds(left, top + ROW * 2, FIELD_WIDTH, 20).build());
        int buttonWidth = exists ? (FIELD_WIDTH - 8) / 3 : (FIELD_WIDTH - 4) / 2;
        int buttonY = top + ROW * 3 + 8;
        addRenderableWidget(Button.builder(Component.translatable("cartofold.waypoint.save"), button -> save())
                .bounds(left, buttonY, buttonWidth, 20).build());
        int next = left + buttonWidth + 4;
        if (exists) {
            addRenderableWidget(Button.builder(Component.translatable("cartofold.waypoint.remove"),
                    button -> send(original, true)).bounds(next, buttonY, buttonWidth, 20).build());
            next += buttonWidth + 4;
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(next, buttonY, buttonWidth, 20).build());
        setInitialFocus(name);
    }

    private EditBox coordinate(int left, int top, int value, String label) {
        EditBox box = addRenderableWidget(new EditBox(font, left, top, COORDINATE_WIDTH, 20, Component.literal(label)));
        box.setMaxLength(9);
        box.setFilter(text -> text.matches("-?\\d*"));
        box.setValue(Integer.toString(value));
        return box;
    }

    private void nextColor() {
        DyeColor[] colors = DyeColor.values();
        int index = 0;
        for (int i = 0; i < colors.length; i++) {
            if (colors[i].getTextColor() == color) {
                index = (i + 1) % colors.length;
                break;
            }
        }
        color = colors[index].getTextColor();
    }

    private static int parse(@Nullable EditBox box, int fallback) {
        try {
            return box == null ? fallback : Integer.parseInt(box.getValue());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void save() {
        BlockPos pos = new BlockPos(parse(x, original.pos().getX()), parse(y, original.pos().getY()),
                parse(z, original.pos().getZ()));
        String text = name == null ? original.name() : name.getValue();
        boolean death = original.death() && text.equals(WaypointMarkers.name(original).getString());
        send(new Waypoint(original.id(), death ? "" : text, color, original.dimension(), pos, death), false);
    }

    private void send(Waypoint waypoint, boolean remove) {
        PacketDistributor.sendToServer(new WaypointActionPayload(waypoint, remove));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int left = width / 2 - FIELD_WIDTH / 2;
        int top = height / 2 - ROW * 3;
        graphics.drawCenteredString(font, title, width / 2, top - 20, 0xFFFFFFFF);
        graphics.fill(left + FIELD_WIDTH + 6, top + ROW * 2, left + FIELD_WIDTH + 26, top + ROW * 2 + 20,
                0xFF000000 | color);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
