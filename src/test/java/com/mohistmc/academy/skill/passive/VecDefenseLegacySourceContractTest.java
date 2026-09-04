package com.mohistmc.academy.skill.passive;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VecDefenseLegacySourceContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy/skill/passive/PassiveSkillEventHandler.java"));
    }

    @Test
    void forcedLegacyDebitsCannotRegressToInsufficientCpEarlyReturns() throws Exception {
        String source = source();
        assertTrue(source.contains("payForced(data, \"vec_deviation\""));
        assertTrue(source.contains("payForced(data, \"vec_reflection\""));
        assertTrue(source.contains("if (!maintained) VecDefenseRuntime.stop"));
    }

    @Test
    void reflectionKeepsLegacyAttributionAndDirectDamageSource() throws Exception {
        String source = source();
        assertTrue(source.contains("getDirectEntity()"));
        assertTrue(source.contains("if (event.getAmount() <= 0) event.setCanceled(true)"));
        assertFalse(source.contains("setOwner(player)"));
        assertFalse(source.contains("maintainedThisTick"));
    }

    @Test
    void reflectedLegacyRaysKeepTheirSecondVisualSegment() throws Exception {
        String railgun = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/skill/ability/electromaster/RailgunEffect.java"));
        String meltdowner = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/skill/ability/meltdowner/MeltdownerEffect.java"));
        assertTrue(railgun.contains("reflectedBeam.setBeam(start, direction, REFLECT_RANGE)"));
        assertTrue(meltdowner.contains("reflectedBeam.setBeam(visualStart,direction,10)"));
    }
}
