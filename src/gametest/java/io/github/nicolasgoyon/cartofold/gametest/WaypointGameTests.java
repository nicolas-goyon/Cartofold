package io.github.nicolasgoyon.cartofold.gametest;

import com.mojang.authlib.GameProfile;
import io.github.nicolasgoyon.cartofold.Cartofold;
import io.github.nicolasgoyon.cartofold.network.WaypointActionPayload;
import io.github.nicolasgoyon.cartofold.server.WaypointService;
import io.github.nicolasgoyon.cartofold.waypoint.Waypoint;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the waypoints of the players on the server (see {@link WaypointService}). */
@GameTestHolder(Cartofold.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WaypointGameTests {
    private WaypointGameTests() {
    }

    /** A player adds, changes and removes a waypoint. A death adds a waypoint at the place of the death. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void playerKeepsItsWaypoints(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WaypointService service = WaypointService.get().orElseThrow();
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "cartofold_waypoints"));
        BlockPos home = helper.absolutePos(new BlockPos(1, 2, 1));
        Waypoint waypoint = new Waypoint(UUID.randomUUID(), "Home", 0x00FF00, level.dimension(), home, false);

        service.apply(player, new WaypointActionPayload(waypoint, false));
        helper.assertTrue(service.waypoints(player.getUUID()).equals(List.of(waypoint)), "The player has its waypoint");
        Waypoint renamed = new Waypoint(waypoint.id(), "Base", 0x00FF00, level.dimension(), home, false);
        service.apply(player, new WaypointActionPayload(renamed, false));
        helper.assertTrue(service.waypoints(player.getUUID()).equals(List.of(renamed)), "The waypoint changes");

        player.setPos(home.getX() + 0.5, home.getY(), home.getZ() + 0.5);
        WaypointService.onDeath(new LivingDeathEvent(player, level.damageSources().generic()));
        List<Waypoint> waypoints = service.waypoints(player.getUUID());
        helper.assertTrue(waypoints.size() == 2 && waypoints.get(1).death() && waypoints.get(1).pos().equals(home),
                "The death adds a waypoint: " + waypoints);

        service.apply(player, new WaypointActionPayload(renamed, true));
        helper.assertTrue(service.waypoints(player.getUUID()).size() == 1, "The waypoint goes out of the list");
        helper.succeed();
    }
}
