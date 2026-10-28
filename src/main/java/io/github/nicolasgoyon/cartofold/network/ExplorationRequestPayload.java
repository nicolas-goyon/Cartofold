package io.github.nicolasgoyon.cartofold.network;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Client to server: the client needs the exploration data of regions of a dimension. The server sends the explored
 * chunks of these regions, then their changes (see {@link ExplorationChunksPayload}).
 *
 * @param dimension the dimension of the regions
 * @param regions   the regions, {@link #MAX_REGIONS} at most
 */
public record ExplorationRequestPayload(ResourceKey<Level> dimension, List<RegionPos> regions)
        implements CustomPacketPayload {
    /** The largest number of regions in one request. */
    public static final int MAX_REGIONS = 64;

    public static final Type<ExplorationRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "exploration_request"));

    private static final StreamCodec<FriendlyByteBuf, RegionPos> REGION_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RegionPos::x,
            ByteBufCodecs.VAR_INT, RegionPos::z,
            RegionPos::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, ExplorationRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceKey.streamCodec(Registries.DIMENSION), ExplorationRequestPayload::dimension,
                    REGION_CODEC.apply(ByteBufCodecs.list(MAX_REGIONS)), ExplorationRequestPayload::regions,
                    ExplorationRequestPayload::new);

    public ExplorationRequestPayload {
        regions = List.copyOf(regions);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
