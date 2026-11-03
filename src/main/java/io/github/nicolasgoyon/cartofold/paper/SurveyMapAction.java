package io.github.nicolasgoyon.cartofold.paper;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * The action of the cartography table on a survey map: the result of the table has it, and the action happens when
 * the player takes the result (see {@link SurveyMapItem#onCraftedBy}).
 */
public enum SurveyMapAction implements StringRepresentable {
    /** A new map with the next scale. */
    ZOOM,
    /** A new locked copy of the map. */
    LOCK,
    /** A new map half of a side to the north. */
    MOVE_NORTH,
    /** A new map half of a side to the south. */
    MOVE_SOUTH,
    /** A new map half of a side to the west. */
    MOVE_WEST,
    /** A new map half of a side to the east. */
    MOVE_EAST,
    /** The same map, updated at a distance. */
    UPDATE;

    public static final Codec<SurveyMapAction> CODEC = StringRepresentable.fromEnum(SurveyMapAction::values);
    public static final StreamCodec<ByteBuf, SurveyMapAction> STREAM_CODEC =
            ByteBufCodecs.idMapper(index -> values()[index], SurveyMapAction::index);

    private int index() {
        for (int i = 0; i < values().length; i++) {
            if (values()[i] == this) {
                return i;
            }
        }
        throw new IllegalStateException("An action is in the values");
    }

    /** The action of a button of the table (see {@link MapMoves}), with a paper. */
    public static SurveyMapAction ofButton(int button) {
        return switch (button) {
            case MapMoves.NORTH -> MOVE_NORTH;
            case MapMoves.SOUTH -> MOVE_SOUTH;
            case MapMoves.WEST -> MOVE_WEST;
            case MapMoves.EAST -> MOVE_EAST;
            case MapMoves.UPDATE -> UPDATE;
            default -> ZOOM;
        };
    }

    /** The direction of a move (see {@link MapMoves}), or {@link MapMoves#NONE}. */
    public int direction() {
        return switch (this) {
            case MOVE_NORTH -> MapMoves.NORTH;
            case MOVE_SOUTH -> MapMoves.SOUTH;
            case MOVE_WEST -> MapMoves.WEST;
            case MOVE_EAST -> MapMoves.EAST;
            default -> MapMoves.NONE;
        };
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
