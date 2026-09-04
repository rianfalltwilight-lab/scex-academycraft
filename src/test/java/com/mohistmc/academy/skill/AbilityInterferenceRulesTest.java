package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AbilityInterferenceRulesTest {
    @Test
    void clampsLegacyRangeAndSquaresPulseCost() {
        assertEquals(10, AbilityInterferenceRules.clampRange(-100));
        assertEquals(10, AbilityInterferenceRules.clampRange(10));
        assertEquals(73, AbilityInterferenceRules.clampRange(73));
        assertEquals(100, AbilityInterferenceRules.clampRange(1_000));
        assertEquals(100, AbilityInterferenceRules.pulseCost(10));
        assertEquals(10_000, AbilityInterferenceRules.pulseCost(100));
    }

    @Test
    void guiUsesOfficial112TenBlockRangeSteps() {
        assertEquals(10, AbilityInterferenceRules.stepRange(10, -1));
        assertEquals(20, AbilityInterferenceRules.stepRange(10, 1));
        assertEquals(63, AbilityInterferenceRules.stepRange(73, -1));
        assertEquals(83, AbilityInterferenceRules.stepRange(73, 1));
        assertEquals(100, AbilityInterferenceRules.stepRange(100, 1));
    }

    @Test
    void usesLegacyAxisAlignedCubeWithInclusiveBoundary() {
        assertTrue(AbilityInterferenceRules.contains(.5, .5, .5,
                10.5, 10.5, 10.5, 10));
        assertFalse(AbilityInterferenceRules.contains(.5, .5, .5,
                10.5001, .5, .5, 10));
        // This corner lies outside a radius-10 sphere, proving the rule is a cube.
        assertTrue(AbilityInterferenceRules.contains(.5, .5, .5,
                9.5, 9.5, 9.5, 10));
    }

    @Test
    void creativeAndWhitelistAreHardExemptions() {
        assertFalse(AbilityInterferenceRules.affects(true, false,
                0, 0, 0, 1, 1, 1, 10));
        assertFalse(AbilityInterferenceRules.affects(false, true,
                0, 0, 0, 1, 1, 1, 10));
        assertTrue(AbilityInterferenceRules.affects(false, false,
                0, 0, 0, 1, 1, 1, 10));
    }
}
