package com.mohistmc.academy.network;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FlashingHeldInputTest {
    @Test void pressOnlyPreviewsAndSameKeyReleaseCommits() {
        assertTrue(FlashingHeldInput.canHold(-1, 2));
        assertTrue(FlashingHeldInput.canRelease(2, 2, 100, 110, 40));
        assertFalse(FlashingHeldInput.canRelease(2, 1, 100, 110, 40));
    }

    @Test void secondDirectionCannotStealHeldMarker() {
        assertTrue(FlashingHeldInput.canHold(0, 0));
        assertFalse(FlashingHeldInput.canHold(0, 3));
    }

    @Test void lostKeyUpExpiresWithoutLateCommit() {
        assertTrue(FlashingHeldInput.canRelease(1, 1, 20, 60, 40));
        assertFalse(FlashingHeldInput.canRelease(1, 1, 20, 61, 40));
        assertFalse(FlashingHeldInput.canRelease(1, 1, 20, 19, 40));
    }
}
