package io.github.nicolasgoyon.cartofold.mixin.client;

import io.github.nicolasgoyon.cartofold.client.SurveyMapPreview;
import io.github.nicolasgoyon.cartofold.paper.SurveyMapItem;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The preview of the cartography table shows a survey map (see {@link SurveyMapPreview}). */
@Mixin(CartographyTableScreen.class)
// Mixin calls the injected methods, and gives them the parameters of the target method.
@SuppressWarnings({"UnusedMethod", "UnusedVariable"})
abstract class CartographyTableScreenMixin {
    @Inject(method = "renderResultingMap", at = @At("HEAD"), cancellable = true)
    private void cartofold$surveyPreview(GuiGraphics graphics, @Nullable MapId mapId,
            @Nullable MapItemSavedData mapData, boolean hasMap, boolean hasPaper, boolean hasGlassPane,
            boolean isMaxSize, CallbackInfo info) {
        @SuppressWarnings("unchecked")
        AbstractContainerScreen<CartographyTableMenu> screen =
                (AbstractContainerScreen<CartographyTableMenu>) (Object) this;
        ItemStack map = screen.getMenu().getSlot(0).getItem();
        if (map.getItem() instanceof SurveyMapItem) {
            SurveyMapPreview.render(graphics, screen.getGuiLeft(), screen.getGuiTop(), map,
                    screen.getMenu().getSlot(1).getItem());
            info.cancel();
        }
    }
}
