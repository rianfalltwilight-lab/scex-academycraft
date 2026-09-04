package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LegacyResourceProgressionTest {
    @Test
    void matchesThe107ResourceTablesExactly() {
        float[] cp = {1800, 1800, 2800, 4000, 5800, 8000};
        float[] cpGrowth = {0, 900, 1000, 1500, 1700, 12000};
        float[] overload = {100, 100, 150, 240, 350, 500};
        float[] overloadGrowth = {0, 40, 70, 80, 100, 500};
        for (int level = 0; level <= 5; level++) {
            assertEquals(cp[level], LegacyResourceProgression.initialCp(level));
            assertEquals(cpGrowth[level], LegacyResourceProgression.maxAddedCp(level));
            assertEquals(overload[level], LegacyResourceProgression.initialOverload(level));
            assertEquals(overloadGrowth[level], LegacyResourceProgression.maxAddedOverload(level));
        }
    }

    @Test
    void usageGrowthUsesThe107RatesAndBothCaps() {
        assertEquals(1.0f, LegacyResourceProgression.growCp(0, 400, .0025f, 1), .00001f);
        assertEquals(900, LegacyResourceProgression.growCp(899.5f, 1000, .0025f, 1));
        assertEquals(.58f, LegacyResourceProgression.growOverload(0, 100, .0058f, 5), .00001f);
        assertEquals(10, LegacyResourceProgression.growOverload(0, 1_000_000, .0058f, 5),
                "1.0.7 caps overload growth from one action at ten points");
        assertEquals(40, LegacyResourceProgression.growOverload(39, 1_000_000, .0058f, 1));
    }

    @Test
    void genericCoursesUseThe107Bonuses() {
        assertEquals(0, LegacyResourceProgression.courseCpBonus(false, false));
        assertEquals(1000, LegacyResourceProgression.courseCpBonus(true, false));
        assertEquals(2500, LegacyResourceProgression.courseCpBonus(true, true));
        assertEquals(100, LegacyResourceProgression.courseOverloadBonus(true));
        assertEquals(1.2f, LegacyResourceProgression.recoveryMultiplier(true));
    }

    @Test
    void rebuiltV3TotalsMigrateWithoutTurningOldCourseBonusesIntoUsageGrowth() {
        assertEquals(500, LegacyResourceProgression.importedRebuiltUsageCp(4500, true, true));
        assertEquals(50, LegacyResourceProgression.importedRebuiltUsageOverload(650, true));
        assertEquals(0, LegacyResourceProgression.importedRebuiltUsageCp(Float.NaN, false, false));
        assertEquals(0, LegacyResourceProgression.importedRebuiltUsageOverload(-10, false));
    }

    @Test
    void consumeWithForceDepletesAndClampsInsteadOfRejecting() {
        var result = LegacyResourceProgression.consumeWithForce(25, 90, 100, 100, 40);
        assertEquals(0, result.cp());
        assertEquals(100, result.overload());
        assertEquals(null, LegacyResourceProgression.consumeWithForce(25, 90, 100, Float.NaN, 1));
        assertEquals(null, LegacyResourceProgression.consumeWithForce(25, 90, 100, 1, -1));
    }
}
