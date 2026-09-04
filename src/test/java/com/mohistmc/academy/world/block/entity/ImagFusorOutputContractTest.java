package com.mohistmc.academy.world.block.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ImagFusorOutputContractTest {
    @Test void productionUsesRecipeCountComponentsAndRemainingCapacity() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/world/block/entity/ImagFusorBlockEntity.java"));
        assertTrue(source.contains("ItemStack.isSameItemSameComponents(existing, result)"));
        assertTrue(source.contains("existing.getMaxStackSize() - result.getCount()"));
        assertTrue(source.contains("output.grow(result.getCount())"));
        assertFalse(source.contains("output.grow(1)"));
    }
}
