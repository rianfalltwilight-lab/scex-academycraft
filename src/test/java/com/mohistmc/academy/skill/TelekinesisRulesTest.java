package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mohistmc.academy.skill.ability.telekinesis.TelekinesisRules;
import org.junit.jupiter.api.Test;

class TelekinesisRulesTest {
    @Test void overloadThinkingTradesOverloadForIncreasingCpRecovery() {
        assertEquals(100.0f, TelekinesisRules.overloadThinkingCost(0), 0.001f);
        assertEquals(100.0f, TelekinesisRules.overloadThinkingCost(1), 0.001f);
        assertEquals(1000.0f, TelekinesisRules.overloadThinkingRestore(0), 0.001f);
        assertEquals(2000.0f, TelekinesisRules.overloadThinkingRestore(1), 0.001f);
    }

    @Test void insulationIsStrongestAgainstElectromasterAndMeltdowner() {
        float generic = TelekinesisRules.mitigateAbilityDamage(20, 1, false);
        float favored = TelekinesisRules.mitigateAbilityDamage(20, 1, true);
        assertEquals(16.0f, generic, 0.001f);
        assertEquals(12.0f, favored, 0.001f);
        assertTrue(favored < generic);
    }

    @Test void hardeningNeedsAnAvailablePassiveGroundedCrouchingStance() {
        assertTrue(TelekinesisRules.mayEnterHardenedStance(true, true, true));
        assertFalse(TelekinesisRules.mayEnterHardenedStance(false, true, true));
        assertFalse(TelekinesisRules.mayEnterHardenedStance(true, false, true));
        assertFalse(TelekinesisRules.mayEnterHardenedStance(true, true, false));
    }

    @Test void paperDrillHasAnExactFullStackAndBoundedPulseContract() {
        assertEquals(64, TelekinesisRules.PAPER_DRILL_REQUIRED_PAPER);
        assertEquals(1, TelekinesisRules.PAPER_DRILL_PULSE_INTERVAL);
        assertTrue(TelekinesisRules.paperDrillDamage(1) > TelekinesisRules.paperDrillDamage(0));
        assertEquals(5.0f, TelekinesisRules.paperDrillRange(0), 0.001f);
        assertEquals(5.0f, TelekinesisRules.paperDrillRange(1), 0.001f);
    }
}
