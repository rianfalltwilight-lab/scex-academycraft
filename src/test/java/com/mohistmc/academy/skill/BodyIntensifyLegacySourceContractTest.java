package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BodyIntensifyLegacySourceContractTest {
    @Test void preservesTheObservableUnshuffledPreincrementBuffSequence() throws Exception {
        String source=Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/skill/ability/electromaster/BodyIntensifyEffect.java"));
        assertFalse(source.contains("Collections.shuffle")||source.contains("player.getRandom().nextInt(i + 1)"));
        assertTrue(source.indexOf("idx++;")<source.indexOf("BASE_EFFECTS.get(idx)"));
        assertTrue(source.contains("Jump Boost followed by Regeneration"));
        assertTrue(source.indexOf("DynamicSkillRules.addExp")<source.indexOf("data.setCooldown"));
        assertTrue(source.contains("shouldApplyCooldownAfterRelease")&&source.contains("return false;"));
    }
}
