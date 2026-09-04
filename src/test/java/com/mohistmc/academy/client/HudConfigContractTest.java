package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HudConfigContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy/" + relative));
    }

    @Test
    void everyAdvertisedHudAndSoundSettingHasARuntimeConsumer() throws Exception {
        String cp = source("client/gui/CPBarOverlay.java");
        assertTrue(cp.contains("showHud()") && cp.contains("showCpBar()"));
        assertTrue(cp.contains("cpBarX()") && cp.contains("cpBarY()") && cp.contains("autoAvoidJade()"));

        String keys = source("client/gui/AbilityHudOverlay.java");
        assertTrue(keys.contains("showHud()") && keys.contains("showKeyHints()"));
        assertTrue(keys.contains("keyHintX()") && keys.contains("keyHintY()"));

        String charging = source("client/ChargingHudOverlay.java");
        assertTrue(charging.contains("showHud()") && charging.contains("showChargingHud()"));

        String loops = source("client/sound/AbilityLoopSoundManager.java");
        String sounds = source("client/sound/SkillSoundConfigHandler.java");
        assertTrue(loops.contains("enableSkillSounds()"));
        assertTrue(sounds.contains("PlaySoundEvent") && sounds.contains("event.setSound(null)"));
    }

    @Test
    void legacySettingsAppExposesDragEditorAndAllClientToggles() throws Exception {
        String settings = source("client/gui/SettingsAppGui.java");
        for (String key : new String[]{"SHOW_HUD", "SHOW_CP_BAR", "SHOW_CHARGING_HUD",
                "SHOW_KEY_HINTS", "AUTO_AVOID_JADE", "SKILL_SOUNDS", "EDIT_HUD"}) {
            assertTrue(settings.contains(key), "Settings app is missing " + key);
        }
        assertTrue(settings.contains("new HudCustomizeGui()"));

        String editor = source("client/gui/HudCustomizeGui.java");
        assertTrue(editor.contains("edit_preview/cpbar.png"));
        assertTrue(editor.contains("edit_preview/key_hint.png"));
        assertTrue(editor.contains("saveLayout()"));
    }
}
