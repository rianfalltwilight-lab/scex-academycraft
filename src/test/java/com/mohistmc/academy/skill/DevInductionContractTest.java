package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Adversarial source contracts around the server-owned induction transaction. */
class DevInductionContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java").resolve(path));
    }

    @Test void survivalFactorCannotBypassTheDeveloperSession() throws Exception {
        String factor = source("com/mohistmc/academy/world/item/BaseFactor.java");
        assertTrue(factor.contains("super(properties.stacksTo(1))"));
        assertTrue(factor.contains("if (!player.isCreative())"));
        assertTrue(factor.contains("请在能力开发机中开始能力诱导"));
        assertFalse(factor.contains("if (!player.isCreative()) {\n            stack.shrink(1)"));
    }

    @Test void factorIsLockedByExactSourceAndConsumedOnlyAtCompletion() throws Exception {
        String sessions = source("com/mohistmc/academy/network/DevLearningSessionManager.java");
        String packet = source("com/mohistmc/academy/network/LearnSkillPacket.java");
        assertTrue(sessions.contains("current == expectedStack"));
        assertTrue(sessions.contains("current.getCount() == expectedCount"));
        assertFalse(sessions.contains("Source.ADVANCED_SLOT"));
        assertTrue(sessions.contains("Developer blocks\n        // never owned factor slots"));
        assertTrue(sessions.contains("new ArrayList<>(AbilityCategory.all())"));
        assertTrue(sessions.contains("锁定的能力诱导因子已被移走或替换"));
        assertTrue(packet.contains("completeInduction"));
        assertTrue(packet.contains("selection.consume(player, type, pos)"));
        assertTrue(packet.contains("data.setPlayerLevel(1)"));
        assertTrue(packet.contains("SkillRegistry.getSkill(data.getCurrentAbility(), packet.skillId())"));
        assertTrue(packet.contains("data.getCurrentAbility() != skillCategory"));
    }

    @Test void resetRestoresLegacyLevelThreeGateAndLifecycleEvents() throws Exception {
        String packet = source("com/mohistmc/academy/network/LearnSkillPacket.java");
        String sessions = source("com/mohistmc/academy/network/DevLearningSessionManager.java");
        assertTrue(packet.contains("data.getPlayerLevel() < 3"));
        assertTrue(sessions.contains("selection.expectedLevel() * 10"));
        assertTrue(sessions.contains("player.getMainHandItem().is(AcademyItems.MAGNETIC_COIL.get())"));
        assertTrue(packet.contains("new AbilityEvents.CategoryChanged"));
        assertTrue(packet.contains("new AbilityEvents.LevelChanged"));
    }

    @Test void processStaticInductionStateIsClearedWhenTheServerStops() throws Exception {
        String sessions = source("com/mohistmc/academy/network/DevLearningSessionManager.java");
        String cleanup = source("com/mohistmc/academy/network/NetworkRuntimeCleanup.java");
        assertTrue(sessions.contains("SESSIONS.clearAll()"));
        assertTrue(sessions.contains("ACTIVE.clear()"));
        assertTrue(cleanup.contains("DevLearningSessionManager.clearAll()"));
    }
}
