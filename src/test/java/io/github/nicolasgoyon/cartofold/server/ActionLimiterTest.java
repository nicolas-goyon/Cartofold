package io.github.nicolasgoyon.cartofold.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActionLimiterTest {
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void actionsOverTheLimitAreRefused() {
        ActionLimiter limiter = new ActionLimiter(2, 20);

        assertTrue(limiter.allow(PLAYER, 100));
        assertTrue(limiter.allow(PLAYER, 105));
        assertFalse(limiter.allow(PLAYER, 110));
    }

    @Test
    void aNewWindowAllowsActionsAgain() {
        ActionLimiter limiter = new ActionLimiter(1, 20);

        assertTrue(limiter.allow(PLAYER, 100));
        assertFalse(limiter.allow(PLAYER, 119));
        assertTrue(limiter.allow(PLAYER, 120));
    }

    @Test
    void eachPlayerHasItsOwnLimit() {
        ActionLimiter limiter = new ActionLimiter(1, 20);

        assertTrue(limiter.allow(PLAYER, 100));
        assertTrue(limiter.allow(UUID.randomUUID(), 100));
    }
}
