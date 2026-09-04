package com.mohistmc.academy.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayloadRateLimiterTest {
    @Test void boundsFloodAndReopensAtWindowBoundary() {
        UUID player = UUID.randomUUID();
        assertTrue(PayloadRateLimiter.allow(player, "flashing", 100, 2, 2));
        assertTrue(PayloadRateLimiter.allow(player, "flashing", 100, 2, 2));
        for (int i = 0; i < 10_000; i++)
            assertFalse(PayloadRateLimiter.allow(player, "flashing", 101, 2, 2));
        assertTrue(PayloadRateLimiter.allow(player, "flashing", 102, 2, 2));
        PayloadRateLimiter.forget(player);
    }

    @Test void isolatesPlayersAndChannelsAndHandlesClockReset() {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertTrue(PayloadRateLimiter.allow(first, "toggle", 20, 5, 1));
        assertFalse(PayloadRateLimiter.allow(first, "toggle", 21, 5, 1));
        assertTrue(PayloadRateLimiter.allow(first, "slots", 21, 5, 1));
        assertTrue(PayloadRateLimiter.allow(second, "toggle", 21, 5, 1));
        assertTrue(PayloadRateLimiter.allow(first, "toggle", 1, 5, 1));
        PayloadRateLimiter.forget(first);
        PayloadRateLimiter.forget(second);
    }

    @Test void rejectsInvalidPolicyInsteadOfFailingOpen() {
        UUID player = UUID.randomUUID();
        assertFalse(PayloadRateLimiter.allow(player, "x", 0, 0, 1));
        assertFalse(PayloadRateLimiter.allow(player, "x", 0, 1, 0));
    }

    @Test void boundsEntryAndNegativeFeedbackIndependentlyThenRecovers() {
        UUID player = UUID.randomUUID();
        int handled = 0, feedback = 0;
        for (int request = 0; request < 10_000; request++) {
            if (!PayloadRateLimiter.allow(player, "use_skill", 40, 20, 20)) continue;
            handled++;
            if (PayloadRateLimiter.allow(player, "use_skill_feedback:inactive", 40, 20, 1)) feedback++;
        }
        assertEquals(20, handled, "flood must have a bounded server-work budget");
        assertEquals(1, feedback, "negative response amplification must be one per window");
        assertTrue(PayloadRateLimiter.allow(player, "use_skill", 60, 20, 20));
        assertTrue(PayloadRateLimiter.allow(player, "use_skill_feedback:inactive", 60, 20, 1));
        PayloadRateLimiter.forget(player);
    }

    @Test void validFirstPressIsImmediateAndSeparateFromFeedbackBudget() {
        UUID player = UUID.randomUUID();
        assertTrue(PayloadRateLimiter.allow(player, "skill_key_down", 100, 20, 20));
        assertTrue(PayloadRateLimiter.allow(player, "skill_key_down_feedback", 100, 20, 1));
        assertFalse(PayloadRateLimiter.allow(player, "skill_key_down_feedback", 119, 20, 1));
        assertTrue(PayloadRateLimiter.allow(player, "skill_key_down_feedback", 120, 20, 1));
        PayloadRateLimiter.forget(player);
    }
}
