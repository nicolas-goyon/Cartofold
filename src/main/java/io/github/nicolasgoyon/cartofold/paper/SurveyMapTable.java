package io.github.nicolasgoyon.cartofold.paper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
 * <p>A locked map does not zoom, move, update or lock again. The action happens when the player takes the result.
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
        if (input.is(CartofoldItems.EMPTY_SURVEY_MAP.get())) {
            return map.copyWithCount(2);
        }
        if (data.locked()) {
            return ItemStack.EMPTY;
        }
        if (input.is(Items.PAPER)) {
            SurveyMapAction action = SurveyMapAction.ofButton(button);
            if (action == SurveyMapAction.ZOOM && data.scale() >= SurveyMapData.MAX_SCALE) {
                return ItemStack.EMPTY;
            }
            return withAction(map, action);
        }
        if (input.is(Items.GLASS_PANE)) {
            return withAction(map, SurveyMapAction.LOCK);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack withAction(ItemStack map, SurveyMapAction action) {
        ItemStack result = map.copyWithCount(1);
        result.set(CartofoldItems.SURVEY_MAP_ACTION.get(), action);
        return result;
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
                MapUpdates.start(level, id, data);
                yield null;
            }
        };
        if (result != null) {
            stack.set(CartofoldItems.SURVEY_MAP_ID.get(), SurveyMapData.create(level.getServer(), result));
        }
    }
}
