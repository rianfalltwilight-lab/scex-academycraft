package com.mohistmc.academy.network;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FlashingEpochStateTest {
    @Test void duplicateStartIsIdempotent() {
        FlashingEpochState state = new FlashingEpochState();
        assertTrue(state.accept(true, 41));
        assertFalse(state.accept(true, 41));
        assertTrue(state.active());
        assertEquals(41, state.epoch());
    }

    @Test void staleEndCannotCloseNewEpoch() {
        FlashingEpochState state = new FlashingEpochState();
        state.accept(true, 41);
        state.accept(true, 42);
        assertFalse(state.accept(false, 41));
        assertTrue(state.active());
        assertEquals(42, state.epoch());
        assertTrue(state.accept(false, 42));
        assertFalse(state.active());
    }

    @Test void zeroIsResetOnlyAndNeverAStartEpoch() {
        FlashingEpochState state = new FlashingEpochState();
        assertFalse(state.accept(true, 0));
        state.accept(true, 7);
        assertTrue(state.accept(false, 0));
        assertFalse(state.active());
    }
}
