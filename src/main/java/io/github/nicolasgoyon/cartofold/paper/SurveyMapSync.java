package io.github.nicolasgoyon.cartofold.paper;

import io.github.nicolasgoyon.cartofold.network.SurveyMapPayload;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Sends the survey maps to the clients. Every {@link #CHECK_TICKS} ticks, a player gets the maps in its inventory, in
 * its open menu (for example the cartography table) and in the item frames at {@link #FRAME_DISTANCE} blocks or less,
 * when their version changed since the last send.
 */
public final class SurveyMapSync {
    /** The time between two checks of the maps of a player, in ticks. */
    public static final int CHECK_TICKS = 10;
    /** The item frames with a map at this distance or less from a player get to the player, in blocks. */
    public static final double FRAME_DISTANCE = 64.0;
    /** The time between two checks of the markers of the maps (see {@link PaperWaypoints#check}), in ticks. */
    public static final int CHECK_MARKER_TICKS = 40;

    private static final Map<UUID, Int2IntOpenHashMap> SENT = new HashMap<>();

    private SurveyMapSync() {
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer
                || player.tickCount % CHECK_TICKS != 0
                || !player.connection.hasChannel(SurveyMapPayload.TYPE)) {
            return;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (InteractionHand hand : InteractionHand.values()) {
            stacks.add(player.getItemInHand(hand));
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            stacks.add(player.getInventory().getItem(slot));
        }
        player.containerMenu.slots.forEach(slot -> stacks.add(slot.getItem()));
        for (ItemFrame frame : player.serverLevel().getEntitiesOfClass(ItemFrame.class,
                player.getBoundingBox().inflate(FRAME_DISTANCE))) {
            stacks.add(frame.getItem());
        }
        Int2IntOpenHashMap sent = SENT.computeIfAbsent(player.getUUID(), key -> new Int2IntOpenHashMap());
        for (ItemStack stack : stacks) {
            SurveyMapId id = SurveyMapItem.id(stack);
            SurveyMapData data = id == null ? null : SurveyMapData.get(player.server, id);
            if (data != null && player.tickCount % CHECK_MARKER_TICKS == 0) {
                PaperWaypoints.check(player.serverLevel(), data);
            }
            if (data != null && sent.getOrDefault(id.id(), -1) != data.version()) {
                sent.put(id.id(), data.version());
                PacketDistributor.sendToPlayer(player, SurveyMapPayload.of(id.id(), data));
            }
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SENT.remove(event.getEntity().getUUID());
    }
}
