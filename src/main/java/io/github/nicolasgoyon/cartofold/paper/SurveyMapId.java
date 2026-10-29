package io.github.nicolasgoyon.cartofold.paper;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The id of a survey map: the data component of a survey map item.
 *
 * @param id the number of the map
 */
public record SurveyMapId(int id) {
    public static final Codec<SurveyMapId> CODEC = Codec.INT.xmap(SurveyMapId::new, SurveyMapId::id);
    public static final StreamCodec<ByteBuf, SurveyMapId> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(SurveyMapId::new, SurveyMapId::id);

    /** The key of the data of the map in the storage of the world. */
    public String key() {
        return "cartofold_survey_map_" + id;
    }
}
