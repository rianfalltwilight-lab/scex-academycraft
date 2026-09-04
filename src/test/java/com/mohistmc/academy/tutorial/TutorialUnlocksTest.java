package com.mohistmc.academy.tutorial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TutorialUnlocksTest {
    @Test
    void everyLegacyConditionalTutorialHasAServerSafeTrigger() {
        Map<String, String> samples = Map.of(
                "academy:constraint_metal", "ores",
                "academy:phase_gen", "phase_generator",
                "academy:solar_gen", "solar_generator",
                "academy:windgen_fan", "wind_generator",
                "academy:metal_former", "metal_former",
                "academy:imag_fusor", "imag_fusor",
                "academy:app_skill_tree", "terminal",
                "academy:dev_normal", "ability_developer",
                "academy:rf_output", "energy_bridge");
        samples.forEach((item, tutorial) -> assertEquals(tutorial, TutorialUnlocks.tutorialForItem(item)));
        assertNull(TutorialUnlocks.tutorialForItem("minecraft:stone"));
    }

    @Test
    void multipleItemsForOneTutorialEmitOnlyOneActivation() {
        var activated = new HashSet<String>();
        assertEquals("ores", TutorialUnlocks.activateForItem(activated, "academy:constraint_metal"));
        assertNull(TutorialUnlocks.activateForItem(activated, "academy:crystal_ore"));
        assertTrue(activated.contains("ores"));
    }

    @Test
    void existingObtainedHistoryMigratesWithoutInventingDefaultTutorials() {
        var activated = new HashSet<String>();
        TutorialUnlocks.reconcile(java.util.Set.of("academy:windgen_main"), activated);
        assertTrue(activated.contains("wind_generator"));
        assertFalse(activated.contains("welcome"));
    }
}
