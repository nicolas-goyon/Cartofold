package io.github.nicolasgoyon.cartofold.waypoint;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.api.MapMarker;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapBanner;

/**
 * A banner that a player marked on a map.
 *
 * @param dimension the dimension of the banner
 * @param pos       the position of the banner
 * @param color     the color of the banner
 * @param name      the name of the banner, or empty
 */
public record BannerMark(ResourceKey<Level> dimension, BlockPos pos, DyeColor color, Optional<Component> name) {
    /** The layer of the banners of each player. */
    public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "banners");

    public static final Codec<BannerMark> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(BannerMark::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(BannerMark::pos),
            DyeColor.CODEC.fieldOf("color").forGetter(BannerMark::color),
            ComponentSerialization.FLAT_CODEC.lenientOptionalFieldOf("name").forGetter(BannerMark::name))
            .apply(instance, BannerMark::new));

    public static final Codec<List<BannerMark>> LIST_CODEC = CODEC.listOf();

    /** The mark of a banner of the world. */
    public static BannerMark of(ResourceKey<Level> dimension, MapBanner banner) {
        return new BannerMark(dimension, banner.pos().immutable(), banner.color(), banner.name());
    }

    /** The identifier of the marker of the banner. */
    public String id() {
        return dimension.location() + "/" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** The marker of the banner: the icon of the vanilla maps, and the name as a tooltip. */
    public MapMarker marker() {
        ResourceLocation icon = ResourceLocation.withDefaultNamespace("textures/map/decorations/" + color.getName()
                + "_banner.png");
        return new MapMarker(id(), dimension, pos, color.getTextColor(), Optional.of(icon),
                name.orElse(Component.empty()), name, false);
    }

    /** A list with a mark, in place of the mark at the same position. */
    public static List<BannerMark> set(List<BannerMark> marks, BannerMark mark) {
        List<BannerMark> next = new ArrayList<>(remove(marks, mark.dimension(), mark.pos()));
        next.add(mark);
        return next;
    }

    /** A list with no mark at a position. */
    public static List<BannerMark> remove(List<BannerMark> marks, ResourceKey<Level> dimension, BlockPos pos) {
        return marks.stream().filter(mark -> !mark.dimension().equals(dimension) || !mark.pos().equals(pos)).toList();
    }
}
