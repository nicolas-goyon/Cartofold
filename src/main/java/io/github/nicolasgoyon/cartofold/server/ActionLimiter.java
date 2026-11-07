package io.github.nicolasgoyon.cartofold.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Limits the number of actions of each player in a window of ticks. The server refuses the actions over the limit, so a
 * client cannot flood the server with messages.
 */
public final class ActionLimiter {
    private final int max;
    private final long window;
    private final Map<UUID, long[]> players = new HashMap<>();

    /**
     * Makes a limiter.
     *
     * @param max    the largest number of actions of a player in a window
     * @param window the length of a window, in ticks
     */
    public ActionLimiter(int max, long window) {
        this.max = max;
        this.window = window;
    }

    /**
     * Records an action of a player.
     *
     * @param player the UUID of the player
     * @param now    the game time
     * @return true if the action is in the limit, false if the server must refuse it
     */
    public boolean allow(UUID player, long now) {
        // [0] is the start of the window, [1] is the number of actions in it.
        long[] state = players.computeIfAbsent(player, ignored -> new long[] {now, 0});
        if (now - state[0] >= window || now < state[0]) {
            state[0] = now;
            state[1] = 0;
        }
        if (state[1] >= max) {
            return false;
        }
        state[1]++;
        return true;
    }

    /** Forgets a player, for example after the player leaves the server. */
    public void remove(UUID player) {
        players.remove(player);
    }
}
