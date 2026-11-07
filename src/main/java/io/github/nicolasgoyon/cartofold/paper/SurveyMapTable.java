package io.github.nicolasgoyon.cartofold.paper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The cartography table with a survey map, as the vanilla table with a vanilla map:
 *
 * <ul>
 *   <li>a paper zooms the map out (a new map with the next scale), or, with a button of the screen, moves the map or
 *       updates it (see {@link MapMoves} and {@link MapUpdates});</li>
 *   <li>a glass pane locks a copy of the map;</li>
 *   <li>an empty survey map gives two maps with the same data.</li>
 * </ul>
 *
 * <p>A locked map does not zoom, move, update or lock again (see {@link SurveyTableOutcome}). The action happens when
 * the player takes the result.
 */
public final class SurveyMapTable {
    private SurveyMapTable() {
    }

    /**
     * The result of the table.
     *
     * @param level  the level of the table
     * @param map    the map in the first slot
     * @param input  the item in the second slot
     * @param button the selected button (see {@link MapMoves}), or {@link MapMoves#NONE}
     * @return the result, or an empty stack
     */
    public static ItemStack result(ServerLevel level, ItemStack map, ItemStack input, int button) {
        SurveyMapData data = SurveyMapItem.data(level.getServer(), map);
        if (data == null) {
            return ItemStack.EMPTY;
        }
        SurveyTableOutcome outcome = SurveyTableOutcome.of(SurveyTableOutcome.Input.of(input), data.locked(),
                data.scale(), button);
        if (outcome == SurveyTableOutcome.COPY) {
            return map.copyWithCount(2);
        }
        SurveyMapAction action = outcome.action();
        return action == null ? ItemStack.EMPTY : withAction(map, action);
    }

    /**
     * The geometry of the map that a zoom gives: the next scale, with the center on the grid of the game.
     *
     * @param map the geometry of the map
     * @return the geometry of the zoomed-out map
     */
    public static PaperMapGeometry zoomed(PaperMapGeometry map) {
        return SurveyMapData.around(Level.OVERWORLD, map.centerX(), map.centerZ(),
                Math.min(map.scale() + 1, SurveyMapData.MAX_SCALE)).geometry();
    }

    private static ItemStack withAction(ItemStack map, SurveyMapAction action) {
        ItemStack result = map.copyWithCount(1);
        result.set(CartofoldItems.SURVEY_MAP_ACTION.get(), action);
        return result;
    }

    /**
     * True if the inventory slots of a menu can take a result. Vanilla applies the action of the result before it moves
     * the result to the inventory: with no room, the action must not run.
     *
     * @param menu   the menu
     * @param result the result
     * @param first  the first slot of the inventory in the menu
     * @param end    the slot after the last slot of the inventory in the menu
     */
    public static boolean fits(AbstractContainerMenu menu, ItemStack result, int first, int end) {
        for (int index = first; index < end; index++) {
            ItemStack stack = menu.slots.get(index).getItem();
            if (stack.isEmpty() || (ItemStack.isSameItemSameComponents(stack, result)
                    && stack.getCount() + result.getCount() <= stack.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies the action of a map that the player takes from the table, and removes the action from the item.
     *
     * @param stack the map
     * @param level the level
     */
    public static void apply(ItemStack stack, ServerLevel level) {
        SurveyMapAction action = stack.remove(CartofoldItems.SURVEY_MAP_ACTION.get());
        SurveyMapId id = SurveyMapItem.id(stack);
        SurveyMapData data = id == null ? null : SurveyMapData.get(level.getServer(), id);
        if (action == null || data == null || data.locked()) {
            return;
        }
        SurveyMapData result = switch (action) {
            case ZOOM -> SurveyMapData.around(data.dimension(), data.centerX(), data.centerZ(),
                    Math.min(data.scale() + 1, SurveyMapData.MAX_SCALE));
            case LOCK -> data.lockedCopy();
            case MOVE_NORTH, MOVE_SOUTH, MOVE_WEST, MOVE_EAST -> MapMoves.moved(data, action.direction());
            case UPDATE -> {
                // The update reads the land of the dimension of the map, not of the table.
                ServerLevel mapLevel = level.getServer().getLevel(data.dimension());
                if (mapLevel != null) {
                    MapUpdates.start(mapLevel, id, data);
                }
                yield null;
            }
        };
        if (result != null) {
            stack.set(CartofoldItems.SURVEY_MAP_ID.get(), SurveyMapData.create(level.getServer(), result));
        }
    }
}
