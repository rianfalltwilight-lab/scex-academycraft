package com.mohistmc.academy.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MatterUnitLegacyContractTest {
    @Test
    void splitMatterUnitVariantsRetainTheLegacyStackLimit() throws Exception {
        for (String name : new String[]{"MatterUnitNone.java", "MatterUnitPhaseLiquid.java"}) {
            String source = Files.readString(Path.of(
                    "src/main/java/com/mohistmc/academy/world/item", name));
            assertTrue(source.contains("new Properties().stacksTo(16)"), name);
        }
    }

    @Test
    void creativeHarvestDoesNotConsumeTheEmptyUnit() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/world/item/MatterUnitNone.java"));
        assertTrue(source.contains("if (!player.getAbilities().instabuild) stack.shrink(1)"));
    }
}
