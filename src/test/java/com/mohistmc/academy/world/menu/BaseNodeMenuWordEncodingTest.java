package com.mohistmc.academy.world.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BaseNodeMenuWordEncodingTest {
    @Test
    void roundTripsValuesAcrossTheSignedShortBoundary() {
        for (int value : new int[] {0, 1, 32_767, 32_768, 50_000, 200_000, Integer.MAX_VALUE}) {
            // Reproduce the signed-short values received by the client.
            int lowOnClient = (short) MenuDataWords.low(value);
            int highOnClient = (short) MenuDataWords.high(value);
            assertEquals(value, MenuDataWords.join(lowOnClient, highOnClient));
        }
    }
}
