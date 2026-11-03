package io.github.nicolasgoyon.cartofold.network;

import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.data.MapSlice;
import io.github.nicolasgoyon.cartofold.data.RegionPos;
import java.util.Comparator;
import java.util.LinkedHashSet;
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
 * Client to server: the client needs the exploration data of regions of slices of a dimension: the surface, and a band
 * of caves (see {@link MapSlice}). The server sends the explored chunks of these regions, then their changes (see
 * {@link ExplorationChunksPayload}). A request replaces the last request.
 *
 * @param dimension the dimension of the regions
 * @param slices    the regions of each slice, {@link #MAX_SLICES} slices at most
 */
public record ExplorationRequestPayload(ResourceKey<Level> dimension, List<SliceRegions> slices)
        implements CustomPacketPayload {
    /** The largest number of slices in one request. */
    public static final int MAX_SLICES = 4;

    /**
     * The regions of a slice.
     *
     * @param slice   the slice
     * @param regions the regions, {@link #MAX_REGIONS} at most
     */
    public record SliceRegions(MapSlice slice, List<RegionPos> regions) {
        public SliceRegions {
            regions = List.copyOf(regions);
        }
    }

    /** The largest number of regions of a slice in one request. */
    public static final int MAX_REGIONS = 128;

    public static final Type<ExplorationRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "exploration_request"));

    private static final StreamCodec<FriendlyByteBuf, RegionPos> REGION_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RegionPos::x,
            ByteBufCodecs.VAR_INT, RegionPos::z,
            RegionPos::new);

    private static final StreamCodec<FriendlyByteBuf, SliceRegions> SLICE_CODEC = StreamCodec.composite(
            MapSlice.STREAM_CODEC, SliceRegions::slice,
            REGION_CODEC.apply(ByteBufCodecs.list(MAX_REGIONS)), SliceRegions::regions,
            SliceRegions::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, ExplorationRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceKey.streamCodec(Registries.DIMENSION), ExplorationRequestPayload::dimension,
                    SLICE_CODEC.apply(ByteBufCodecs.list(MAX_SLICES)), ExplorationRequestPayload::slices,
                    ExplorationRequestPayload::new);

    public ExplorationRequestPayload {
        slices = List.copyOf(slices);
    }

    /**
     * The regions of a request: the first regions, then the other regions, with no duplicate. When there are more than
     * {@link #MAX_REGIONS} regions, the other regions nearest to the center of the other regions stay.
     *
     * @param first the regions with the priority (the regions near the player)
     * @param other the other regions (the regions of a view)
     * @return the regions, {@link #MAX_REGIONS} at most
     */
    public static List<RegionPos> combine(List<RegionPos> first, List<RegionPos> other) {
        LinkedHashSet<RegionPos> regions = new LinkedHashSet<>(first);
        if (!other.isEmpty()) {
            double centerX = other.stream().mapToInt(RegionPos::x).average().orElse(0);
            double centerZ = other.stream().mapToInt(RegionPos::z).average().orElse(0);
            other.stream()
                    .sorted(Comparator.comparingDouble(pos -> Math.hypot(pos.x() - centerX, pos.z() - centerZ)))
                    .forEach(regions::add);
        }
        return regions.stream().limit(MAX_REGIONS).toList();
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
