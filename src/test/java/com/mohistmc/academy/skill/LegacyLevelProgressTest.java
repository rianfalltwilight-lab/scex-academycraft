package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LegacyLevelProgressTest {
    @Test void thresholdMatchesAcademyCraft107() {
        assertEquals(1.332f, LegacyLevelProgress.threshold(1, 2), 1.0e-6f);
        assertEquals(3.999f, LegacyLevelProgress.threshold(4, 3), 1.0e-6f);
        assertEquals(0.0f, LegacyLevelProgress.threshold(3, 0));
    }

    @Test void progressIsBoundedAndLevelFiveCannotAdvance() {
        assertEquals(0.5f, LegacyLevelProgress.fraction(0.666f, 1.332f), 1.0e-6f);
        assertEquals(1.0f, LegacyLevelProgress.fraction(99, 1));
        assertEquals(0.0f, LegacyLevelProgress.fraction(Float.NaN, 1));
        assertTrue(LegacyLevelProgress.canLevelUp(true, 4, 3.999f, 3.999f));
        assertFalse(LegacyLevelProgress.canLevelUp(true, 5, 10, 1));
        assertFalse(LegacyLevelProgress.canLevelUp(false, 1, 10, 1));
    }
}
