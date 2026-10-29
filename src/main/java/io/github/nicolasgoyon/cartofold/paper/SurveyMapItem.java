package io.github.nicolasgoyon.cartofold.paper;

import io.github.nicolasgoyon.cartofold.network.SurveyMapPayload;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The survey map: a paper map of Cartofold. Its data is on the server (see {@link SurveyMapData}). In the hand of a
 * player, the map explores (see {@link SurveyMapExploration}).
 */
public final class SurveyMapItem extends Item {
    public SurveyMapItem(Properties properties) {
        super(properties);
    }

    /** A new survey map item with new data. */
    public static ItemStack create(MinecraftServer server, SurveyMapData data) {
        ItemStack stack = new ItemStack(CartofoldItems.SURVEY_MAP.get());
        stack.set(CartofoldItems.SURVEY_MAP_ID.get(), SurveyMapData.create(server, data));
        return stack;
    }

    /** The id of the map of an item, or null for an item with no map. */
    @Nullable
    public static SurveyMapId id(ItemStack stack) {
        return stack.get(CartofoldItems.SURVEY_MAP_ID.get());
    }

    /** The data of the map of an item, on the server, or null. */
    @Nullable
    public static SurveyMapData data(MinecraftServer server, ItemStack stack) {
        SurveyMapId id = id(stack);
        return id == null ? null : SurveyMapData.get(server, id);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (entity instanceof ServerPlayer player && entity.tickCount % SurveyMapExploration.UPDATE_TICKS == 0
                && (selected || player.getOffhandItem() == stack)) {
            SurveyMapData data = data(player.server, stack);
            if (data != null) {
                SurveyMapExploration.update(player, data);
            }
        }
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        if (level instanceof ServerLevel server) {
            SurveyMapTable.apply(stack, server);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        SurveyMapId id = id(stack);
        return id == null ? super.getName(stack)
                : Component.translatable("item.cartofold.survey_map.numbered", id.id());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
            TooltipFlag flag) {
        SurveyMapId id = id(stack);
        if (id == null) {
            return;
        }
        SurveyMapPayload map = SurveyMapLookup.client(id);
        if (map != null) {
            tooltip.add(Component.translatable("filled_map.scale", 1 << map.scale())
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("filled_map.level", map.scale(), SurveyMapData.MAX_SCALE)
                    .withStyle(ChatFormatting.GRAY));
            if (map.locked()) {
                tooltip.add(Component.translatable("filled_map.locked", id.id()).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
