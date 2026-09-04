package com.mohistmc.academy.skill.passive;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PassiveSkillMathTest {
    private static final float EPS = 0.0001f;

    @Test void radiationUsesMaxCpProgressionAndClamps() {
        assertEquals(0f, PassiveSkillMath.radiationProficiency(-1, 8000), EPS);
        assertEquals(.5f, PassiveSkillMath.radiationProficiency(4000, 8000), EPS);
        assertEquals(1f, PassiveSkillMath.radiationProficiency(16000, 8000), EPS);
        assertEquals(1.4f, PassiveSkillMath.radiationMultiplier(0), EPS);
        assertEquals(1.8f, PassiveSkillMath.radiationMultiplier(1), EPS);
        assertThrows(IllegalArgumentException.class, () -> PassiveSkillMath.radiationProficiency(1, 0));
    }

    @Test void teleportCriticalTiersMatchLegacyEndpoints() {
        assertEquals(0f, PassiveSkillMath.teleportCritProbability(0, -1, -1), EPS);
        assertEquals(.28f, PassiveSkillMath.teleportCritProbability(0, 0, 0), EPS);
        assertEquals(.45f, PassiveSkillMath.teleportCritProbability(0, 1, 1), EPS);
        assertEquals(0f, PassiveSkillMath.teleportCritProbability(1, 1, -1), EPS);
        assertEquals(.10f, PassiveSkillMath.teleportCritProbability(1, -1, 0), EPS);
        assertEquals(.03f, PassiveSkillMath.teleportCritProbability(2, -1, 1), EPS);
        assertArrayEquals(new float[]{1.3f, 1.6f, 2.6f}, new float[]{
                PassiveSkillMath.teleportCritMultiplier(0), PassiveSkillMath.teleportCritMultiplier(1),
                PassiveSkillMath.teleportCritMultiplier(2)}, EPS);
        assertThrows(IllegalArgumentException.class, () -> PassiveSkillMath.teleportCritProbability(3, 0, 0));
    }

    @Test void vectorDeviationEndpointsAndClampingAreStable() {
        assertEquals(.4f, PassiveSkillMath.deviationReduction(-10), EPS);
        assertEquals(.9f, PassiveSkillMath.deviationReduction(10), EPS);
        assertEquals(18f, PassiveSkillMath.deviationTickCp(0), EPS);
        assertEquals(7.5f, PassiveSkillMath.deviationTickCp(1), EPS);
        assertEquals(.5f, PassiveSkillMath.deviationTickOverload(0), EPS);
        assertEquals(.2f, PassiveSkillMath.deviationTickOverload(1), EPS);
        assertEquals(80f, PassiveSkillMath.deviationStartOverload(0), EPS);
        assertEquals(50f, PassiveSkillMath.deviationStartOverload(1), EPS);
    }
}
