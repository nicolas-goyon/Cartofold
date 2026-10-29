package io.github.nicolasgoyon.cartofold.network;

import io.github.nicolasgoyon.cartofold.Cartofold;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Server to client: the place of a vanilla map. The vanilla packets give the pixels of a map, but not its center and
 * its dimension: the layers on the map need them.
 *
 * @param map       the id of the map
 * @param centerX   the X of the center
 * @param centerZ   the Z of the center
 * @param scale     the scale
 * @param dimension the dimension
 */
public record PaperMapInfoPayload(int map, int centerX, int centerZ, int scale, ResourceKey<Level> dimension)
        implements CustomPacketPayload {
    public static final Type<PaperMapInfoPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "paper_map_info"));

    public static final StreamCodec<FriendlyByteBuf, PaperMapInfoPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PaperMapInfoPayload::map,
            ByteBufCodecs.INT, PaperMapInfoPayload::centerX,
            ByteBufCodecs.INT, PaperMapInfoPayload::centerZ,
            ByteBufCodecs.VAR_INT, PaperMapInfoPayload::scale,
            ResourceKey.streamCodec(Registries.DIMENSION), PaperMapInfoPayload::dimension,
            PaperMapInfoPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
