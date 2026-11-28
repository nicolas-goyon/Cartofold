package io.github.nicolasgoyon.cartofold;

import io.github.nicolasgoyon.cartofold.paper.SpyglassMaps;
import io.github.nicolasgoyon.cartofold.view.EntityKind;
import java.util.EnumMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The settings of the server, in the folder {@code serverconfig} of the world. The server sends its rules to each
 * player (see {@link io.github.nicolasgoyon.cartofold.server.ServerRules}).
 */
public final class CartofoldServerConfig {
    public static final ModConfigSpec SPEC;
    /** For each kind of entity: true if the maps of the players can show it. */
    public static final Map<EntityKind, ModConfigSpec.BooleanValue> ENTITIES = new EnumMap<>(EntityKind.class);
    /** True if the maps show only the players of the team of the player. */
    public static final ModConfigSpec.BooleanValue SAME_TEAM_ONLY;
    /**
     * A player with a spyglass explores a radius this number of times larger (see
     * {@link io.github.nicolasgoyon.cartofold.paper.SpyglassMaps#explorationFactor}).
     */
    public static final ModConfigSpec.DoubleValue SPYGLASS_FACTOR;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("The entities that the minimaps and the maps of the world of the players can show. A server"
                + " can stop them for a fair game.").translation("cartofold.configuration.server.entities")
                .push("entities");
        for (EntityKind kind : EntityKind.values()) {
            ENTITIES.put(kind, builder.comment("Let the maps of the players show the " + kind.key() + ".")
                    .translation("cartofold.configuration.server.entities." + kind.key()).define(kind.key(), true));
        }
        SAME_TEAM_ONLY = builder.comment("The maps of a player show only the other players of its team. A player with no"
                + " team sees no other player.").translation("cartofold.configuration.server.entities.same_team_only")
                .define("sameTeamOnly", false);
        builder.pop();
        builder.comment("The exploration of the maps.").translation("cartofold.configuration.server.exploration")
                .push("exploration");
        SPYGLASS_FACTOR = builder.comment("A player with a spyglass in the inventory explores a radius this number of"
                + " times larger. Past the normal radius, only the chunks that the server has in memory and the chunks"
                + " with map data count. The spyglass never loads or generates a chunk.")
                .translation("cartofold.configuration.server.exploration.spyglass_factor")
                .defineInRange("spyglassFactor", SpyglassMaps.DEFAULT_FACTOR, SpyglassMaps.MIN_FACTOR,
                        SpyglassMaps.MAX_FACTOR);
        builder.pop();
        SPEC = builder.build();
    }

    private CartofoldServerConfig() {
    }

    /** The kinds of entities that the maps of the players can show, as a set of bits (see {@link EntityKind#bit}). */
    public static int entities() {
        int mask = 0;
        for (Map.Entry<EntityKind, ModConfigSpec.BooleanValue> entry : ENTITIES.entrySet()) {
            if (entry.getValue().get()) {
                mask |= entry.getKey().bit();
            }
        }
        return mask;
    }
}
