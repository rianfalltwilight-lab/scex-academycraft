package com.mohistmc.academy.tutorial;

import com.mohistmc.academy.skill.PlayerAbilityData;
import java.util.Map;
import java.util.Set;

/**
 * Server-safe mapping of the 1.0.7 item-obtained conditions to tutorial ids.
 * It intentionally contains only non-default tutorials: the default entries
 * are visible from the first launch and never emitted an activation event.
 */
public final class TutorialUnlocks {
    private static final Map<String, String> BY_ITEM = Map.ofEntries(
            Map.entry("academy:constraint_metal", "ores"),
            Map.entry("academy:imagsil_ore", "ores"),
            Map.entry("academy:crystal_ore", "ores"),
            Map.entry("academy:reso_ore", "ores"),
            Map.entry("academy:phase_gen", "phase_generator"),
            Map.entry("academy:solar_gen", "solar_generator"),
            Map.entry("academy:windgen_base", "wind_generator"),
            Map.entry("academy:windgen_pillar", "wind_generator"),
            Map.entry("academy:windgen_main", "wind_generator"),
            Map.entry("academy:windgen_fan", "wind_generator"),
            Map.entry("academy:metal_former", "metal_former"),
            Map.entry("academy:imag_fusor", "imag_fusor"),
            Map.entry("academy:terminal_installer", "terminal"),
            Map.entry("academy:app_skill_tree", "terminal"),
            Map.entry("academy:app_freq_transmitter", "terminal"),
            Map.entry("academy:app_media_player", "terminal"),
            Map.entry("academy:developer_portable", "ability_developer"),
            Map.entry("academy:dev_normal", "ability_developer"),
            Map.entry("academy:dev_advanced", "ability_developer"),
            Map.entry("academy:rf_input", "energy_bridge"),
            Map.entry("academy:rf_output", "energy_bridge")
    );

    private TutorialUnlocks() {}

    public static String tutorialForItem(String itemId) {
        return itemId == null ? null : BY_ITEM.get(itemId);
    }

    /** Returns the newly activated tutorial id, or {@code null} if unchanged. */
    public static String activateForItem(PlayerAbilityData data, String itemId) {
        String tutorialId = tutorialForItem(itemId);
        return tutorialId != null && data.activateTutorial(tutorialId) ? tutorialId : null;
    }

    static String activateForItem(Set<String> activatedTutorials, String itemId) {
        String tutorialId = tutorialForItem(itemId);
        return tutorialId != null && activatedTutorials.add(tutorialId) ? tutorialId : null;
    }

    /** Migrates existing obtained-item history without replaying old notifications. */
    public static void reconcile(PlayerAbilityData data) {
        for (String itemId : data.getObtainedItems()) activateForItem(data, itemId);
    }

    static void reconcile(Set<String> obtainedItems, Set<String> activatedTutorials) {
        for (String itemId : obtainedItems) activateForItem(activatedTutorials, itemId);
    }
}
