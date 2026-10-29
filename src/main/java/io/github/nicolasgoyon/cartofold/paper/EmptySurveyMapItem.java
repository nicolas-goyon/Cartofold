package io.github.nicolasgoyon.cartofold.paper;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The empty survey map: a use gives a survey map around the player, as the vanilla empty map. */
public final class EmptySurveyMapItem extends Item {
    public EmptySurveyMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server)) {
            return InteractionResultHolder.success(stack);
        }
        stack.consume(1, player);
        player.awardStat(Stats.ITEM_USED.get(this));
        level.playSound(null, player, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, player.getSoundSource(), 1.0F,
                1.0F);
        ItemStack map = SurveyMapItem.create(server.getServer(),
                SurveyMapData.around(level.dimension(), player.getBlockX(), player.getBlockZ(), 0));
        if (stack.isEmpty()) {
            return InteractionResultHolder.consume(map);
        }
        if (!player.getInventory().add(map.copy())) {
            player.drop(map, false);
        }
        return InteractionResultHolder.consume(stack);
    }
}
