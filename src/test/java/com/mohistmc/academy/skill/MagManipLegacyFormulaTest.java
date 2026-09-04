package com.mohistmc.academy.skill;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MagManipLegacyFormulaTest {
    @Test void endpointsMatchAcademyCraft107() {
        assertEquals(140f, MagManipLegacyMath.cpCost(0), 1e-6f);
        assertEquals(270f, MagManipLegacyMath.cpCost(1), 1e-6f);
        assertEquals(35f, MagManipLegacyMath.overloadCost(0), 1e-6f);
        assertEquals(20f, MagManipLegacyMath.overloadCost(1), 1e-6f);
        assertEquals(.5f, MagManipLegacyMath.throwSpeed(0), 1e-6f);
        assertEquals(1f, MagManipLegacyMath.throwSpeed(1), 1e-6f);
        assertEquals(10f, MagManipLegacyMath.impactDamage(0), 1e-6f);
        assertEquals(10f, MagManipLegacyMath.impactDamage(1), 1e-6f);
        assertEquals(60, MagManipLegacyMath.cooldown(0));
        assertEquals(40, MagManipLegacyMath.cooldown(1));
    }
}
