package com.mohistmc.academy.skill.passive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VecDefenseLegacyMathTest {
    @Test
    void deviationMatchesThe107EndpointTables() {
        assertEquals(13, VecDefenseLegacyMath.deviationTickCost(0));
        assertEquals(5, VecDefenseLegacyMath.deviationTickCost(1));
        assertEquals(5, VecDefenseLegacyMath.deviationSecondaryCp(0));
        assertEquals(2.5f, VecDefenseLegacyMath.deviationSecondaryCp(1));
        assertEquals(.5f, VecDefenseLegacyMath.deviationSecondaryOverload(0));
        assertEquals(.2f, VecDefenseLegacyMath.deviationSecondaryOverload(1), .00001f);
        assertEquals(15, VecDefenseLegacyMath.deviationEntityCost(0));
        assertEquals(12, VecDefenseLegacyMath.deviationEntityCost(1));
        assertEquals(.6f, VecDefenseLegacyMath.deviationDamageMultiplier(0), .00001f);
        assertEquals(.1f, VecDefenseLegacyMath.deviationDamageMultiplier(1), .00001f);
    }

    @Test
    void deviationEntityDebitDoesNotUseDifficultyButReflectionDoes() {
        assertEquals(15, VecDefenseLegacyMath.deviationEntityCost(0));
        assertEquals(30, 2 * VecDefenseLegacyMath.deviationEntityCost(0));
        assertEquals(300, VecDefenseLegacyMath.reflectionEntityCost(0, 1));
        assertEquals(30, VecDefenseLegacyMath.reflectionEntityCost(0, .1f));
        assertEquals(420, VecDefenseLegacyMath.reflectionEntityCost(0, 1.4f));
        assertEquals(160, VecDefenseLegacyMath.reflectionEntityCost(1, 1));
    }

    @Test
    void reflectionMatchesThe107DamageAndUpkeepTables() {
        assertEquals(15, VecDefenseLegacyMath.reflectionTickCost(0));
        assertEquals(11, VecDefenseLegacyMath.reflectionTickCost(1));
        assertEquals(200, VecDefenseLegacyMath.reflectionDamageCost(0, 10));
        assertEquals(150, VecDefenseLegacyMath.reflectionDamageCost(1, 10));
        assertEquals(6, VecDefenseLegacyMath.reflectedDamage(0, 10));
        assertEquals(12, VecDefenseLegacyMath.reflectedDamage(1, 10));
    }
}
