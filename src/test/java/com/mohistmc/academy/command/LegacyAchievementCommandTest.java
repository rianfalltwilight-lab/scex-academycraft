package com.mohistmc.academy.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class LegacyAchievementCommandTest {
    @Test
    void resolvesExactLegacy107IdsToGeneratedAdvancementPaths() {
        assertEquals("default/phase_liquid",
                LegacyAchievementIds.toAdvancementPath("phase_liquid"));
        assertEquals("electromaster/lv1",
                LegacyAchievementIds.toAdvancementPath("electromaster.lv1"));
        assertEquals("teleporter/critical_attack",
                LegacyAchievementIds.toAdvancementPath("teleporter.critical_attack"));
        assertEquals("vecmanip/vec_reflection",
                LegacyAchievementIds.toAdvancementPath(
                        "academy:legacy/vecmanip/vec_reflection"));
        assertNull(LegacyAchievementIds.toAdvancementPath("not_a_real_achievement"));
        assertNull(LegacyAchievementIds.toAdvancementPath("electromaster.not_real"));
        assertEquals(56, LegacyAchievementIds.all().size());
    }
}
