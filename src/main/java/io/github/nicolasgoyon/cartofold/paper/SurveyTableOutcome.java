package io.github.nicolasgoyon.cartofold.paper;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The outcome of the cartography table with a survey map: an action, a copy, or the reason for no result. The server
 * gives the result from the outcome (see {@link SurveyMapTable}), and the screen of the client shows it.
 */
public enum SurveyTableOutcome {
    /** A new map with the next scale. */
    ZOOM,
    /** A new locked copy of the map. */
    LOCK,
    /** Two maps with the same data. */
    COPY,
    /** A new map half of a side to the north. */
    MOVE_NORTH,
    /** A new map half of a side to the south. */
    MOVE_SOUTH,
    /** A new map half of a side to the west. */
    MOVE_WEST,
    /** A new map half of a side to the east. */
    MOVE_EAST,
    /** The same map, updated at a distance. */
    UPDATE,
    /** No result: the second slot is empty. */
    NO_INPUT,
    /** No result: the item of the second slot does not work with a survey map. */
    WRONG_INPUT,
    /** No result: a locked map does not zoom, move, update or lock again. */
    LOCKED,
    /** No result: the map has the largest scale and does not zoom out. */
    MAX_SCALE;

    /** The kind of the item in the second slot of the table. */
    public enum Input {
        NONE,
        PAPER,
        GLASS_PANE,
        EMPTY_SURVEY_MAP,
        OTHER;

        /** The kind of an item. */
        public static Input of(ItemStack stack) {
            if (stack.isEmpty()) {
                return NONE;
            }
            if (stack.is(Items.PAPER)) {
                return PAPER;
            }
            if (stack.is(Items.GLASS_PANE)) {
                return GLASS_PANE;
            }
            if (stack.is(CartofoldItems.EMPTY_SURVEY_MAP.get())) {
                return EMPTY_SURVEY_MAP;
            }
            return OTHER;
        }
    }

    /**
     * The outcome of the table.
     *
     * @param input  the kind of the item in the second slot
     * @param locked true for a locked map
     * @param scale  the scale of the map
     * @param button the selected button (see {@link MapMoves}), or {@link MapMoves#NONE}
     * @return the outcome
     */
    public static SurveyTableOutcome of(Input input, boolean locked, int scale, int button) {
        return switch (input) {
            case EMPTY_SURVEY_MAP -> COPY;
            case OTHER -> WRONG_INPUT;
            case NONE -> locked ? LOCKED : NO_INPUT;
            case GLASS_PANE -> locked ? LOCKED : LOCK;
            case PAPER -> {
                if (locked) {
                    yield LOCKED;
                }
                SurveyMapAction action = SurveyMapAction.ofButton(button);
                if (action == SurveyMapAction.ZOOM && scale >= SurveyMapData.MAX_SCALE) {
                    yield MAX_SCALE;
                }
                yield of(action);
            }
        };
    }

    private static SurveyTableOutcome of(SurveyMapAction action) {
        return switch (action) {
            case ZOOM -> ZOOM;
            case LOCK -> LOCK;
            case MOVE_NORTH -> MOVE_NORTH;
            case MOVE_SOUTH -> MOVE_SOUTH;
            case MOVE_WEST -> MOVE_WEST;
            case MOVE_EAST -> MOVE_EAST;
            case UPDATE -> UPDATE;
        };
    }

    /** The action of the result, or null for a copy (the result has no action) and for no result. */
    @Nullable
    public SurveyMapAction action() {
        return switch (this) {
            case ZOOM -> SurveyMapAction.ZOOM;
            case LOCK -> SurveyMapAction.LOCK;
            case MOVE_NORTH -> SurveyMapAction.MOVE_NORTH;
            case MOVE_SOUTH -> SurveyMapAction.MOVE_SOUTH;
            case MOVE_WEST -> SurveyMapAction.MOVE_WEST;
            case MOVE_EAST -> SurveyMapAction.MOVE_EAST;
            case UPDATE -> SurveyMapAction.UPDATE;
            case COPY, NO_INPUT, WRONG_INPUT, LOCKED, MAX_SCALE -> null;
        };
    }

    /** True if the table gives a result. */
    public boolean hasResult() {
        return this == COPY || action() != null;
    }

    /** The name of the outcome in the keys of the translations. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
