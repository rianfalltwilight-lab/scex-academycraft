package com.mohistmc.academy.client.gui;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DevLearningFeedbackContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/", path));
    }

    @Test void rejectionIsImmediateDetailedAndDoesNotPreSpendClientSession() throws Exception {
        String tree = source("com/mohistmc/academy/client/gui/SkillTreeGui.java");
        String server = source("com/mohistmc/academy/network/LearnSkillPacket.java");
        assertTrue(tree.contains("acceptServerResult"));
        assertFalse(tree.contains("sessionSpent = true;\n                pendingSince"));
        assertTrue(server.contains("前置技能、等级或熟练度不足"));
        assertTrue(server.contains("无法开始：开发机当前不可用或能量不足"));
        assertTrue(server.contains("同步率不足"));
        assertTrue(server.contains("DevLearningSessionManager.start"));
    }

    @Test void energyIsLiveAndTinyAdvancedScreenDisablesInteraction() throws Exception {
        String tree = source("com/mohistmc/academy/client/gui/SkillTreeGui.java");
        String gui = source("com/mohistmc/academy/client/block/gui/DevAdvancedGui.java");
        assertTrue(tree.contains("refreshAuthoritativeEnergy()"));
        assertTrue(gui.contains("compactFallback"));
        assertTrue(gui.contains("if (compactFallback) return true"));
        assertFalse(gui.contains("addAcademySlot"));
    }

    @Test void skillSelectionSessionAllowsNormalReadingTimeWithoutLosingOneShotAuthority() throws Exception {
        String sessions = source("com/mohistmc/academy/network/DevLearningSessionManager.java");
        assertTrue(sessions.contains("20L * 60L * 5L"));
        assertTrue(sessions.contains("SESSIONS.commit"));
        assertTrue(sessions.contains("clear(UUID playerId, UUID nonce)"));
    }
}
