package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.mohistmc.academy.config.LegacyMetalIdRules;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MagManipLegacyBlockListContractTest {
    @Test void defaultLegacyMagneticBlocksRemainAccepted() throws Exception {
        String config = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/config/ACConfig.java"));
        String effect = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/skill/ability/MagManipEffect.java"));
        for (String legacyId : new String[]{"golden_rail", "iron_door", "dispenser", "hopper", "iron_ore"}) {
            assertTrue(config.contains("\"" + legacyId + "\""), legacyId);
        }
        assertEquals("minecraft:powered_rail", LegacyMetalIdRules.blockId("golden_rail"));
        assertEquals("minecraft:iron_door", LegacyMetalIdRules.blockId("iron_door"));
        assertTrue(effect.contains("ElectromasterMetalTargets.isAny(s)"),
                "Mag Manip must consume the shared config/tag driven legacy target list");
    }
}
