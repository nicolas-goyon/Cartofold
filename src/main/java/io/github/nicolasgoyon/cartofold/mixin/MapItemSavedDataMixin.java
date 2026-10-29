package io.github.nicolasgoyon.cartofold.mixin;

import io.github.nicolasgoyon.cartofold.server.PaperMapSync;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The data of a vanilla map: its first packet to a player also sends its place (see {@link PaperMapSync}). */
@Mixin(MapItemSavedData.class)
// Mixin calls the injected methods, and gives them the parameters of the target method.
@SuppressWarnings({"UnusedMethod", "UnusedVariable"})
abstract class MapItemSavedDataMixin {
    @Inject(method = "getUpdatePacket", at = @At("RETURN"))
    private void cartofold$sendPlace(MapId mapId, Player player, CallbackInfoReturnable<Packet<?>> info) {
        if (info.getReturnValue() != null && player instanceof ServerPlayer serverPlayer) {
            PaperMapSync.onUpdatePacket(serverPlayer, mapId, (MapItemSavedData) (Object) this);
        }
    }
}
