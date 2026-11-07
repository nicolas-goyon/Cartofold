package io.github.nicolasgoyon.cartofold.client;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.network.SurveyMapPayload;
import io.github.nicolasgoyon.cartofold.paper.CartofoldItems;
import io.github.nicolasgoyon.cartofold.paper.MapMoves;
import io.github.nicolasgoyon.cartofold.paper.MapUpdates;
import io.github.nicolasgoyon.cartofold.paper.PaperMapGeometry;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapAction;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapData;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapId;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapItem;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapLookup;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapTable;
import io.github.nicolasgoyon.cartofold.paper.SurveyTableOutcome;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The screen of the cartography table. With a vanilla map or with no map, it is the vanilla screen. With a survey map
 * (see {@link SurveyMapTable}), it shows a screen for the survey map:
 *
 * <ul>
 *   <li>the buttons of the moves (north, south, west, east) between the slots, and the buttons of the zoom and of the
 *       update under the preview. A button sends its id to the menu of the server (see {@link MapMoves}). The
 *       selected button is yellow. The same button again selects the zoom;</li>
 *   <li>a panel at the right of the slots: the name of the action, a large preview of the result, the cost (the item
 *       that the table uses) and a line with details or with the reason for no result.</li>
 * </ul>
 *
 * <p>The game opens the vanilla screen. This class takes its place when the screen opens, only if the screen is the
 * vanilla screen: the screen of another mod stays.
 */
@EventBusSubscriber(modid = Cartofold.MOD_ID, value = Dist.CLIENT)
public final class SurveyTableScreen extends CartographyTableScreen {
    private static final ResourceLocation MAP_BACKGROUND =
            ResourceLocation.withDefaultNamespace("textures/map/map_background.png");
    private static final ResourceLocation ERROR = sprite("error");
    private static final ResourceLocation LOCKED = sprite("locked");
    private static final int FULL_LIGHT = 15728880;
    /** The id of the texture of the preview of a move: no survey map has a negative id. */
    private static final int PREVIEW_ID = -1;

    private static final int VANILLA_WIDTH = 176;
    private static final int SURVEY_WIDTH = 304;
    /** The left side and the width of the content of the panel, from the left side of the screen. */
    private static final int PANEL_LEFT = 172;
    private static final int PANEL_WIDTH = 128;
    private static final int MAP_SIZE = 88;
    private static final int MAP_X = PANEL_LEFT + (PANEL_WIDTH - MAP_SIZE) / 2;
    private static final int MAP_Y = 19;
    /** The paper around the map, as the background of a map in the hand. */
    private static final int MAP_BORDER = 5;
    private static final int COST_Y = 116;
    private static final int DETAIL_Y = 134;
    private static final int BUTTON_Y = 145;
    private static final int BUTTON_HEIGHT = 16;
    /** The cells of the buttons of the moves: three columns and three rows between the slots. */
    private static final int CELL_X = 35;
    private static final int CELL_Y = 15;
    private static final int CELL_WIDTH = 33;
    private static final int CELL_HEIGHT = 16;
    private static final int CELL_GAP = 2;

    private static final int TEXT = 0x404040;
    private static final int ERROR_TEXT = 0xAA0000;
    private static final int FILL = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int DARK = 0xFF373737;
    private static final int SLOT = 0xFF8B8B8B;
    private static final int SELECTED = 0xFFFFFF55;

    private final List<ActionButton> buttons = new ArrayList<>();
    /** The cost row: the items and their places, for their tooltips. */
    private final List<ItemStack> costItems = new ArrayList<>();
    private int costX;
    private boolean survey;
    /** The selected button, as in the menu of the server. */
    private int selected = MapMoves.NONE;
    @Nullable
    private SurveyMapPayload previewSource;
    @Nullable
    private SurveyTableOutcome previewOutcome;
    @Nullable
    private SurveyMapPayload preview;

    /** A button of an action, with its id of the menu ({@link MapMoves#NONE} for the zoom). */
    private record ActionButton(int id, Button button) {
    }

    public SurveyTableScreen(CartographyTableMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    private static ResourceLocation sprite(String name) {
        return ResourceLocation.withDefaultNamespace("container/cartography_table/" + name);
    }

    /** Puts this screen in place of the vanilla screen of the table. */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() != null && event.getNewScreen().getClass() == CartographyTableScreen.class) {
            CartographyTableScreen screen = (CartographyTableScreen) event.getNewScreen();
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                event.setNewScreen(new SurveyTableScreen(screen.getMenu(), player.getInventory(), screen.getTitle()));
            }
        }
    }

    private boolean hasSurveyMap() {
        return menu.getSlot(CartographyTableMenu.MAP_SLOT).getItem().getItem() instanceof SurveyMapItem;
    }

    @Override
    protected void init() {
        survey = hasSurveyMap();
        imageWidth = survey ? SURVEY_WIDTH : VANILLA_WIDTH;
        super.init();
        buttons.clear();
        if (!survey) {
            return;
        }
        // The slots stay at the place of the vanilla screen when the panel fits in the window.
        leftPos = Math.max(0, Math.min((width - VANILLA_WIDTH) / 2, width - SURVEY_WIDTH));
        int column = CELL_WIDTH + CELL_GAP;
        int row = CELL_HEIGHT + CELL_GAP;
        add(MapMoves.NORTH, "north", CELL_X + column, CELL_Y, CELL_WIDTH, CELL_HEIGHT);
        add(MapMoves.WEST, "west", CELL_X, CELL_Y + row, CELL_WIDTH, CELL_HEIGHT);
        add(MapMoves.EAST, "east", CELL_X + column * 2, CELL_Y + row, CELL_WIDTH, CELL_HEIGHT);
        add(MapMoves.SOUTH, "south", CELL_X + column, CELL_Y + row * 2, CELL_WIDTH, CELL_HEIGHT);
        int half = (PANEL_WIDTH - CELL_GAP) / 2;
        add(MapMoves.NONE, "zoom", PANEL_LEFT, BUTTON_Y, half, BUTTON_HEIGHT);
        add(MapMoves.UPDATE, "update", PANEL_LEFT + PANEL_WIDTH - half, BUTTON_Y, half, BUTTON_HEIGHT);
        updateButtons();
    }

    private void add(int id, String name, int x, int y, int width, int height) {
        Button button = Button.builder(Component.translatable("cartofold.cartography.button." + name),
                pressed -> press(id))
                .bounds(leftPos + x, topPos + y, width, height)
                .tooltip(Tooltip.create(Component.translatable("cartofold.cartography.tooltip." + name)))
                .build();
        buttons.add(new ActionButton(id, button));
        addRenderableWidget(button);
    }

    /**
     * Sends a button to the server. The menu of the server selects the button, or the zoom if the button is already
     * selected: the zoom button sends the selected button again.
     */
    private void press(int id) {
        if (minecraft == null || minecraft.gameMode == null) {
            return;
        }
        int sent = id == MapMoves.NONE ? selected : id;
        if (sent == MapMoves.NONE) {
            return;
        }
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, sent);
        selected = sent == selected ? MapMoves.NONE : sent;
        updateButtons();
    }

    /** Shows the selected button in yellow. A locked map has no action. */
    private void updateButtons() {
        SurveyMapPayload map = map();
        boolean locked = map != null && map.locked();
        for (ActionButton action : buttons) {
            Component label = Component.translatable("cartofold.cartography.button." + name(action.id()));
            action.button().setMessage(action.id() == selected
                    ? label.copy().withStyle(ChatFormatting.YELLOW) : label);
            action.button().active = !locked;
        }
    }

    private static String name(int id) {
        return switch (id) {
            case MapMoves.NORTH -> "north";
            case MapMoves.SOUTH -> "south";
            case MapMoves.WEST -> "west";
            case MapMoves.EAST -> "east";
            case MapMoves.UPDATE -> "update";
            default -> "zoom";
        };
    }

    @Nullable
    private SurveyMapPayload map() {
        SurveyMapId id = SurveyMapItem.id(menu.getSlot(CartographyTableMenu.MAP_SLOT).getItem());
        return id == null ? null : SurveyMapLookup.client(id);
    }

    private SurveyTableOutcome outcome(@Nullable SurveyMapPayload map) {
        ItemStack input = menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem();
        return SurveyTableOutcome.of(SurveyTableOutcome.Input.of(input), map != null && map.locked(),
                map == null ? 0 : map.scale(), selected);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (survey != hasSurveyMap()) {
            rebuildWidgets();
        }
        if (survey) {
            updateButtons();
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (survey && menu.getCarried().isEmpty()) {
            for (int i = 0; i < costItems.size(); i++) {
                int x = leftPos + costX + i * 18;
                int y = topPos + COST_Y;
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                    graphics.renderTooltip(font, costItems.get(i), mouseX, mouseY);
                }
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (!survey) {
            super.renderBg(graphics, partialTick, mouseX, mouseY);
            return;
        }
        int left = leftPos;
        int top = topPos;
        panel(graphics, left, top, imageWidth, imageHeight);
        for (Slot slot : menu.slots) {
            if (slot.index == CartographyTableMenu.RESULT_SLOT) {
                sunken(graphics, left + slot.x - 5, top + slot.y - 5, 26, 26);
            } else {
                sunken(graphics, left + slot.x - 1, top + slot.y - 1, 18, 18);
            }
        }
        // The line between the slots and the panel.
        graphics.fill(left + PANEL_LEFT - 3, top + 6, left + PANEL_LEFT - 2, top + imageHeight - 6, SLOT);
        graphics.fill(left + PANEL_LEFT - 2, top + 6, left + PANEL_LEFT - 1, top + imageHeight - 6, LIGHT);
        // The compass between the buttons of the moves.
        int column = CELL_WIDTH + CELL_GAP;
        int row = CELL_HEIGHT + CELL_GAP;
        graphics.renderItem(new ItemStack(Items.COMPASS), left + CELL_X + column + (CELL_WIDTH - 16) / 2,
                top + CELL_Y + row);
        for (ActionButton action : buttons) {
            if (action.id() == selected) {
                Button button = action.button();
                outline(graphics, button.getX() - 1, button.getY() - 1, button.getWidth() + 2,
                        button.getHeight() + 2, SELECTED);
            }
        }

        SurveyMapPayload map = map();
        SurveyTableOutcome outcome = outcome(map);
        boolean problem = !outcome.hasResult() && outcome != SurveyTableOutcome.NO_INPUT;
        centered(graphics, Component.translatable("cartofold.cartography.outcome." + outcome.key()),
                left + PANEL_LEFT, top + 5, PANEL_WIDTH, problem ? ERROR_TEXT : TEXT);
        renderPreview(graphics, outcome, map, left + MAP_X, top + MAP_Y);
        renderCost(graphics, outcome, left, top);
        centered(graphics, detail(outcome, map), left + PANEL_LEFT, top + DETAIL_Y, PANEL_WIDTH,
                problem ? ERROR_TEXT : TEXT);
    }

    private static Component detail(SurveyTableOutcome outcome, @Nullable SurveyMapPayload map) {
        String key = "cartofold.cartography.detail.";
        return switch (outcome) {
            case ZOOM -> Component.translatable(key + "zoom",
                    1 << Math.min((map == null ? 0 : map.scale()) + 1, SurveyMapData.MAX_SCALE));
            case MOVE_NORTH, MOVE_SOUTH, MOVE_WEST, MOVE_EAST -> Component.translatable(key + "move");
            case UPDATE -> {
                int count = map == null ? 0
                        : MapUpdates.chunkCount(new PaperMapGeometry(map.centerX(), map.centerZ(), map.scale()));
                yield Component.translatable(key + "update", Math.min(count, MapUpdates.LIMIT), count);
            }
            default -> Component.translatable(key + outcome.key());
        };
    }

    /** Draws the result on the paper of a map. */
    private void renderPreview(GuiGraphics graphics, SurveyTableOutcome outcome, @Nullable SurveyMapPayload map,
            int x, int y) {
        int paper = MAP_SIZE + MAP_BORDER * 2;
        graphics.blit(MAP_BACKGROUND, x - MAP_BORDER, y - MAP_BORDER, paper, paper, 0, 0, 64, 64, 64, 64);
        if (map == null) {
            return;
        }
        float scale = MAP_SIZE / (float) MapMoves.SIZE;
        SurveyMapAction action = outcome.action();
        if (outcome == SurveyTableOutcome.ZOOM) {
            // The zoom gives a new map: the old map is a quarter of it.
            PaperMapGeometry old = new PaperMapGeometry(map.centerX(), map.centerZ(), map.scale());
            PaperMapGeometry zoomed = SurveyMapTable.zoomed(old);
            float px = x + zoomed.pixelX(old.blockX(0)) * scale;
            float pz = y + zoomed.pixelZ(old.blockZ(0)) * scale;
            drawMap(graphics, map, px, pz, scale / 2);
            int side = MAP_SIZE / 2;
            front(graphics, () -> outline(graphics, Math.round(px) - 1, Math.round(pz) - 1, side + 2, side + 2,
                    0x80000000));
        } else if (action != null && action.direction() != MapMoves.NONE) {
            drawMap(graphics, moved(map, action.direction()), x, y, scale);
        } else {
            drawMap(graphics, map, x, y, scale);
        }
        front(graphics, () -> {
            switch (outcome) {
                case LOCK -> graphics.blitSprite(LOCKED, x + MAP_SIZE - 12, y + MAP_SIZE - 16, 10, 14);
                case COPY -> graphics.drawString(font, "×2", x + MAP_SIZE - font.width("×2") - 2,
                        y + MAP_SIZE - 10, LIGHT);
                case LOCKED, MAX_SCALE, WRONG_INPUT -> graphics.blitSprite(ERROR, x + (MAP_SIZE - 28) / 2,
                        y + (MAP_SIZE - 21) / 2, 28, 21);
                default -> {
                }
            }
        });
    }

    /** The data of the map after a move: the same pixels, moved, with no marker. */
    private SurveyMapPayload moved(SurveyMapPayload map, int direction) {
        if (preview == null || previewSource != map || previewOutcome != outcome(map)) {
            int blocks = MapMoves.STEP << map.scale();
            preview = new SurveyMapPayload(PREVIEW_ID, map.version(), map.dimension(),
                    map.centerX() + MapMoves.dx(direction) * blocks, map.centerZ() + MapMoves.dz(direction) * blocks,
                    map.scale(), false, MapMoves.shifted(map.colors(), MapMoves.dx(direction) * MapMoves.STEP,
                            MapMoves.dz(direction) * MapMoves.STEP), List.of());
            previewSource = map;
            previewOutcome = outcome(map);
        }
        return preview;
    }

    /** Draws the cost: the item that the table uses. With no result, the items that the table accepts. */
    private void renderCost(GuiGraphics graphics, SurveyTableOutcome outcome, int left, int top) {
        costItems.clear();
        String label;
        if (outcome.hasResult()) {
            label = "cost";
            costItems.add(menu.getSlot(CartographyTableMenu.ADDITIONAL_SLOT).getItem().copyWithCount(1));
        } else if (outcome == SurveyTableOutcome.MAX_SCALE) {
            return;
        } else {
            label = "use";
            if (outcome != SurveyTableOutcome.LOCKED) {
                costItems.add(new ItemStack(Items.PAPER));
                costItems.add(new ItemStack(Items.GLASS_PANE));
            }
            costItems.add(new ItemStack(CartofoldItems.EMPTY_SURVEY_MAP.get()));
        }
        Component text = Component.translatable("cartofold.cartography." + label);
        Component name = costItems.size() == 1 ? costItems.get(0).getHoverName() : Component.empty();
        int nameWidth = Math.min(font.width(name), PANEL_WIDTH - font.width(text) - 4 - costItems.size() * 18);
        int width = font.width(text) + 4 + costItems.size() * 18 + nameWidth;
        int x = PANEL_LEFT + (PANEL_WIDTH - width) / 2;
        graphics.drawString(font, text, left + x, top + COST_Y + 4, TEXT, false);
        costX = x + font.width(text) + 4;
        for (int i = 0; i < costItems.size(); i++) {
            graphics.renderItem(costItems.get(i), left + costX + i * 18, top + COST_Y);
        }
        if (costItems.size() == 1) {
            fitted(graphics, name, left + costX + 18, top + COST_Y + 4, nameWidth, TEXT);
        }
    }

    private static void drawMap(GuiGraphics graphics, SurveyMapPayload map, float x, float y, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 1.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        SurveyMapRenderer.render(graphics.pose(), graphics.bufferSource(), map, FULL_LIGHT, false);
        graphics.flush();
        graphics.pose().popPose();
    }

    /** Draws above the map. */
    private static void front(GuiGraphics graphics, Runnable draw) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 2.0f);
        draw.run();
        graphics.pose().popPose();
    }

    /** Draws a text in the middle of a width, smaller if the text is wider. */
    private void centered(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        int textWidth = Math.min(font.width(text), width);
        fitted(graphics, text, x + (width - textWidth) / 2, y, width, color);
    }

    /** Draws a text, smaller if the text is wider than a width. */
    private void fitted(GuiGraphics graphics, Component text, int x, int y, int width, int color) {
        int textWidth = font.width(text);
        if (textWidth <= width) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        float scale = width / (float) textWidth;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y + font.lineHeight * (1 - scale) / 2, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    /** A raised panel, as the background of the vanilla screens. */
    private static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x + 1, y, x + width - 1, y + height, 0xFF000000);
        graphics.fill(x, y + 1, x + width, y + height - 1, 0xFF000000);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, FILL);
        graphics.fill(x + 1, y + 1, x + width - 3, y + 3, LIGHT);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 3, LIGHT);
        graphics.fill(x + 3, y + height - 3, x + width - 1, y + height - 1, SHADOW);
        graphics.fill(x + width - 3, y + 3, x + width - 1, y + height - 1, SHADOW);
    }

    /** A sunken box, as a slot. */
    private static void sunken(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, SLOT);
        graphics.fill(x, y, x + width - 1, y + 1, DARK);
        graphics.fill(x, y, x + 1, y + height - 1, DARK);
        graphics.fill(x + 1, y + height - 1, x + width, y + height, LIGHT);
        graphics.fill(x + width - 1, y + 1, x + width, y + height, LIGHT);
    }

    private static void outline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
