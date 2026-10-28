package io.github.nicolasgoyon.cartofold.waypoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class WaypointsTest {
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld"));

    private static Waypoint waypoint(String name, boolean death) {
        return new Waypoint(UUID.randomUUID(), name, 0x123456, OVERWORLD, new BlockPos(1, 64, -3), death);
    }

    @Test
    void waypointCutsItsNameAndItsColor() {
        Waypoint waypoint = new Waypoint(UUID.randomUUID(), "  " + "a".repeat(40) + " ", 0xFF00FF00, OVERWORLD,
                BlockPos.ZERO, false);

        assertEquals(Waypoints.MAX_NAME, waypoint.name().length());
        assertEquals(0x00FF00, waypoint.color());
    }

    @Test
    void codecsKeepTheWaypoint() {
        Waypoint waypoint = waypoint("Home", true);

        JsonElement json = Waypoints.LIST_CODEC.encodeStart(JsonOps.INSTANCE, List.of(waypoint)).getOrThrow();
        assertEquals(List.of(waypoint), Waypoints.LIST_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());

        ByteBuf buf = Unpooled.buffer();
        Waypoint.STREAM_CODEC.encode(buf, waypoint);
        assertEquals(waypoint, Waypoint.STREAM_CODEC.decode(buf));
    }

    @Test
    void setAddsOrChangesTheWaypoint() {
        Waypoint home = waypoint("Home", false);
        List<Waypoint> list = Waypoints.set(List.of(), home);
        Waypoint renamed = new Waypoint(home.id(), "Base", home.color(), OVERWORLD, home.pos(), false);

        list = Waypoints.set(list, renamed);

        assertEquals(List.of(renamed), list);
        assertEquals(List.of(), Waypoints.remove(list, home.id()));
    }

    @Test
    void fullListRefusesANewWaypoint() {
        List<Waypoint> list = new ArrayList<>();
        for (int i = 0; i < Waypoints.MAX_WAYPOINTS; i++) {
            list.add(waypoint("W" + i, false));
        }
        List<Waypoint> full = List.copyOf(list);

        assertSame(full, Waypoints.set(full, waypoint("More", false)));
        assertSame(full, Waypoints.addDeath(full, waypoint("", true)));
    }

    @Test
    void newDeathRemovesTheOldestDeath() {
        Waypoint home = waypoint("Home", false);
        List<Waypoint> list = List.of(home);
        List<Waypoint> deaths = new ArrayList<>();
        for (int i = 0; i < Waypoints.MAX_DEATHS + 1; i++) {
            Waypoint death = waypoint("", true);
            deaths.add(death);
            list = Waypoints.addDeath(list, death);
        }

        assertEquals(Waypoints.MAX_DEATHS + 1, list.size());
        assertTrue(list.contains(home));
        assertTrue(!list.contains(deaths.get(0)));
        assertTrue(list.contains(deaths.get(Waypoints.MAX_DEATHS)));
    }
}
