package com.mohistmc.academy.network;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class NetworkInputLimitsTest {
    @Test void exactLimitsPassAndOneCodeUnitOverFails() {
        assertTrue(NetworkInputLimits.validRequired("s".repeat(32), NetworkInputLimits.SSID));
        assertFalse(NetworkInputLimits.validRequired("s".repeat(33), NetworkInputLimits.SSID));
        assertTrue(NetworkInputLimits.validOptional("p".repeat(64), NetworkInputLimits.PASSWORD));
        assertFalse(NetworkInputLimits.validOptional("p".repeat(65), NetworkInputLimits.PASSWORD));
    }

    @Test void requiredRejectsNullBlankAndOptionalAllowsAbsent() {
        assertFalse(NetworkInputLimits.validRequired(null, NetworkInputLimits.SSID));
        assertFalse(NetworkInputLimits.validRequired("   ", NetworkInputLimits.SSID));
        assertTrue(NetworkInputLimits.validOptional(null, NetworkInputLimits.PASSWORD));
    }
}
