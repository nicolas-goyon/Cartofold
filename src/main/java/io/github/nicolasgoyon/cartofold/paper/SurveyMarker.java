package io.github.nicolasgoyon.cartofold.paper;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * A marker of a block on a survey map, as a banner on a vanilla map.
 *
 * @param key    the key of the marker in the map
 * @param pos    the position of the block
 * @param type   the map decoration type of the icon
 * @param name   the name under the icon, or an empty text
 * @param source the source of the marker (see {@link io.github.nicolasgoyon.cartofold.api.PaperMarkerSource})
 */
public record SurveyMarker(String key, BlockPos pos, ResourceLocation type, String name, ResourceLocation source) {
    public static final StreamCodec<ByteBuf, SurveyMarker> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SurveyMarker::key,
            BlockPos.STREAM_CODEC, SurveyMarker::pos,
            ResourceLocation.STREAM_CODEC, SurveyMarker::type,
            ByteBufCodecs.STRING_UTF8, SurveyMarker::name,
            ResourceLocation.STREAM_CODEC, SurveyMarker::source,
            SurveyMarker::new);
}
