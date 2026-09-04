package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LegacyLevelDevelopmentContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy/", path));
    }

    @Test void skillUseFillsIndependentGaugeAndHigherSkillsStayLocked() throws Exception {
        String data = source("skill/PlayerAbilityData.java");
        String mutation = source("skill/AbilityMutationService.java");
        assertTrue(data.contains("skill.getLevel() > playerLevel"));
        assertFalse(data.contains("skill.getLevel() > playerLevel + 1"));
        assertTrue(data.contains("levelProgressExp"));
        assertTrue(mutation.indexOf("data.addLevelProgress(amount)") < mutation.indexOf("float oldExp"),
                "maxed skill uses must still advance the 1.0.7 level gauge");
    }

    @Test void developerRunsASeparateServerAuthoritativeLevelAction() throws Exception {
        String packet = source("network/LearnSkillPacket.java");
        String session = source("network/DevLearningSessionManager.java");
        String ui = source("client/gui/SkillTreeGui.java");
        assertTrue(packet.contains("LEVEL_UP_ACTION = \"__level_up__\""));
        assertTrue(packet.contains("completeLevelUp") && packet.contains("data.canLevelUp()"));
        assertFalse(packet.contains("computeEffectiveLevel"), "learning a skill must never auto-promote the ability");
        assertTrue(session.contains("5 * (expectedLevel + 1)"));
        assertTrue(session.contains("LearnSkillPacket.completeLevelUp"));
        assertTrue(ui.contains("LearnSkillPacket.LEVEL_UP_ACTION"));
        assertTrue(ui.contains("data.getLevelProgress() * 100"));
    }

    @Test void persistenceAndSyncCarryTheGaugeGeneration() throws Exception {
        String data = source("skill/PlayerAbilityData.java");
        String codec = source("skill/PlayerAbilityDataCodec.java");
        assertTrue(data.contains("putFloat(\"level_progress_exp\""));
        assertTrue(data.contains("setLevelProgressExp(tag.getFloat(\"level_progress_exp\"))"));
        assertTrue(codec.contains("DATA_VERSION = 4"));
        assertTrue(codec.contains("putFloat(\"level_progress_exp\""));
    }
}
