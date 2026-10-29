package io.github.nicolasgoyon.cartofold.gametest;

import com.mojang.authlib.GameProfile;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.api.ChunkArea;
import io.github.nicolasgoyon.cartofold.api.LayerContent;
import io.github.nicolasgoyon.cartofold.api.LayerInfo;
import io.github.nicolasgoyon.cartofold.api.MapLine;
import io.github.nicolasgoyon.cartofold.api.MapMarker;
import io.github.nicolasgoyon.cartofold.api.MarkerClickEvent;
import io.github.nicolasgoyon.cartofold.server.LayerService;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

/** In-game tests for the layers of the other mods (see {@link LayerService}). */
@GameTestHolder(Cartofold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LayerGameTests {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(Cartofold.MOD_ID, "test");

    private LayerGameTests() {
    }

    private static LayerContent content(ServerLevel level) {
        LayerInfo info = LayerInfo.of(LAYER, Component.literal("Test"), 5, true);
        ChunkArea area = ChunkArea.of("area", level.dimension(), List.of(new ChunkPos(0, 0), new ChunkPos(1, 0)),
                0x4000FF00, 0xFF00FF00, Component.literal("Area"));
        MapLine line = new MapLine("line", level.dimension(), List.of(BlockPos.ZERO, new BlockPos(40, 64, 8)),
                0xFFFF0000, 2);
        MapMarker clickable = new MapMarker("heart", level.dimension(), new BlockPos(8, 64, 8), 0xFFAA00,
                Optional.empty(), Component.literal("Heart"), Optional.of(Component.literal("Click")), true);
        MapMarker plain = MapMarker.of("plain", level.dimension(), new BlockPos(20, 64, 8), 0x0000FF,
                Component.literal("Plain"));
        return new LayerContent(info, List.of(area), List.of(line), List.of(clickable, plain));
    }

    /** The content of a layer stays the same after the network codec. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void layerContentGoesThroughTheNetwork(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LayerContent content = content(level);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess(),
                ConnectionType.NEOFORGE);

        LayerContent.STREAM_CODEC.encode(buf, content);
        LayerContent read = LayerContent.STREAM_CODEC.decode(buf);

        helper.assertTrue(read.equals(content), "The content changes in the codec: " + read);
        helper.succeed();
    }

    /** A click on a clickable marker of a layer of the player posts an event. Other clicks post nothing. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void clickOnAMarkerPostsAnEvent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LayerService service = LayerService.get().orElseThrow();
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "cartofold_layers"));
        List<MarkerClickEvent> events = new ArrayList<>();
        Consumer<MarkerClickEvent> listener = events::add;
        NeoForge.EVENT_BUS.addListener(MarkerClickEvent.class, listener);
        try {
            helper.assertFalse(service.click(player, LAYER, "heart"), "The player has no layer");
            service.send(player, content(level));
            helper.assertFalse(service.click(player, LAYER, "plain"), "The marker is not clickable");
            helper.assertFalse(service.click(player, LAYER, "unknown"), "The marker does not exist");
            helper.assertTrue(service.click(player, LAYER, "heart"), "The click is accepted");
            helper.assertTrue(events.size() == 1 && events.get(0).getMarker().id().equals("heart")
                    && events.get(0).getPlayer() == player, "One event for the marker: " + events);
            service.remove(player, LAYER);
            helper.assertTrue(service.layer(player.getUUID(), LAYER).isEmpty(), "The layer goes out");
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
        helper.succeed();
    }
}
