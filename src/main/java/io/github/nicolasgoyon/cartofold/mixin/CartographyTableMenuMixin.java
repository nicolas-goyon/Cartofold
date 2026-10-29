package io.github.nicolasgoyon.cartofold.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.nicolasgoyon.cartofold.paper.CartofoldItems;
import io.github.nicolasgoyon.cartofold.paper.MapMoves;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapItem;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapTable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The cartography table with a survey map (see {@link SurveyMapTable}). The buttons of the screen select a move or the
 * update. The vanilla maps keep the vanilla table.
 */
@Mixin(CartographyTableMenu.class)
// Mixin calls the injected methods, and gives them the parameters of the target method.
@SuppressWarnings({"UnusedMethod", "UnusedVariable"})
abstract class CartographyTableMenuMixin extends AbstractContainerMenu {
    @Shadow
    @Final
    private ContainerLevelAccess access;

    @Shadow
    @Final
    private ResultContainer resultContainer;

    /** The selected button ({@link MapMoves#NONE} for the zoom of the game). */
    @Unique
    private int cartofold$button = MapMoves.NONE;

    private CartographyTableMenuMixin() {
        super(null, 0);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < MapMoves.NORTH || id > MapMoves.UPDATE) {
            return false;
        }
        cartofold$button = id == cartofold$button ? MapMoves.NONE : id;
        slotsChanged(slots.get(0).container);
        return true;
    }

    /** The result of a survey map. */
    @Inject(method = "setupResultSlot", at = @At("HEAD"), cancellable = true)
    private void cartofold$surveyResult(ItemStack map, ItemStack firstSlotStack, ItemStack resultOutput,
            CallbackInfo info) {
        if (!(map.getItem() instanceof SurveyMapItem)) {
            return;
        }
        info.cancel();
        access.execute((level, pos) -> {
            ItemStack result = level instanceof ServerLevel server
                    ? SurveyMapTable.result(server, map, firstSlotStack, cartofold$button) : ItemStack.EMPTY;
            if (result.isEmpty()) {
                resultContainer.removeItemNoUpdate(2);
            } else if (!ItemStack.matches(result, resultOutput)) {
                resultContainer.setItem(2, result);
            }
            broadcastChanges();
        });
    }

    /** A shift click puts a survey map in the first slot, and an empty survey map in the second slot. */
    @WrapOperation(method = "quickMoveStack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z"))
    private boolean cartofold$surveyMapSlot(ItemStack stack, Item item, Operation<Boolean> original) {
        if (item == Items.FILLED_MAP && stack.is(CartofoldItems.SURVEY_MAP.get())) {
            return true;
        }
        if (item == Items.MAP && stack.is(CartofoldItems.EMPTY_SURVEY_MAP.get())) {
            return true;
        }
        return original.call(stack, item);
    }
}
