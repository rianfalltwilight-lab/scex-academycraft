package com.mohistmc.academy.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Cheap source-level guard for the creative-tab crash caused by accepting block items twice. */
class CreativeInventoryContractTest {
    private static final Path WORLD = Path.of("src/main/java/com/mohistmc/academy/world");
    private static final Pattern BLOCK = Pattern.compile(
            "DeferredBlock<Block>\\s+(\\w+)\\s*=\\s*BLOCKS\\.register\\(\"([^\"]+)\"");
    private static final Pattern BLOCK_ITEM = Pattern.compile(
            "DeferredItem<Item>\\s+(\\w+)\\s*=\\s*ITEMS\\.register\\(\"([^\"]+)\"[^;]+"
                    + "new (?:BlockItem|EnergyBridgeBlockItem|com\\.mohistmc\\.academy\\.world\\.item\\.(?:MatrixBlockItem|DevMachineBlockItem|WindGenBaseBlockItem|WindGenMainBlockItem))\\("
                    + "(?:\\(com\\.mohistmc\\.academy\\.world\\.block\\.[^)]+\\))?\\s*AcademyBlocks\\.(\\w+)\\.get\\(\\)", Pattern.DOTALL);

    private static String source(String name) throws IOException {
        return Files.readString(WORLD.resolve(name));
    }

    @Test
    void everyPlayerObtainableBlockHasExactlyOneMatchingBlockItem() throws Exception {
        Matcher blocks = BLOCK.matcher(source("AcademyBlocks.java"));
        Matcher items = BLOCK_ITEM.matcher(source("AcademyItems.java"));
        Set<String> registeredBlocks = new HashSet<>();
        Set<String> blockItemTargets = new HashSet<>();
        Set<String> blockItemIds = new HashSet<>();
        while (blocks.find()) assertTrue(registeredBlocks.add(blocks.group(1)), "duplicate block constant");
        while (items.find()) {
            assertEquals(items.group(2), blockRegistryId(source("AcademyBlocks.java"), items.group(3)),
                    "BlockItem id must equal its target block id: " + items.group(1));
            assertTrue(blockItemTargets.add(items.group(3)), "two BlockItems target " + items.group(3));
            assertTrue(blockItemIds.add(items.group(2)), "duplicate BlockItem registry id " + items.group(2));
        }

        // Deliberately non-player blocks: durable migration escrow, multiblock implementation part,
        // and fluid block (represented to players by the registered phase bucket).
        Set<String> internalWithoutBlockItem = Set.of(
                "MAG_MANIP_ESCROW", "WIND_GEN_BASE_SUB", "PHASE_LIQUID",
                "MATRIX_SUB", "DEV_NORMAL_SUB", "DEV_ADVANCED_SUB", "WINDGEN_FAN");
        Set<String> expectedPlayerBlocks = new HashSet<>(registeredBlocks);
        expectedPlayerBlocks.removeAll(internalWithoutBlockItem);
        assertEquals(expectedPlayerBlocks, blockItemTargets,
                "every ordinary/player-facing Academy block must retain its explicit BlockItem");
        assertTrue(source("AcademyItems.java").contains("PHASE_BUCKET"),
                "phase fluid must remain obtainable through its bucket");
    }

    @Test
    void multiblockImplementationPartsHaveNeitherItemsNorItemModels() throws Exception {
        String items = source("AcademyItems.java");
        for (String id : Set.of("matrix_sub", "dev_normal_sub", "dev_advanced_sub", "windgen_fan_block")) {
            assertFalse(items.contains("ITEMS.register(\"" + id + "\""),
                    id + " is an internal structure part and must not be obtainable or indexed by JEI");
            assertFalse(Files.exists(Path.of("src/main/resources/assets/academy/models/item", id + ".json")),
                    id + " must not advertise an item model");
            Path loot = Path.of("src/main/resources/data/academy/loot_table/blocks", id + ".json");
            assertTrue(Files.exists(loot), id + " needs an explicit empty loot table");
            assertTrue(Files.readString(loot).contains("\"pools\": []"),
                    id + " loot table must not materialize the proxy block");
        }
    }

    @Test
    void creativeGeneratorHasOnlyTheItemRegistryAsItsStackSource() throws Exception {
        String items = source("AcademyItems.java");
        int generator = items.indexOf(".displayItems((params, output) -> {");
        int build = items.indexOf(".build()", generator);
        assertTrue(generator >= 0 && build > generator, "creative tab generator not found");
        String body = items.substring(generator, build);
        assertTrue(body.contains("AcademyItems.ITEMS.getEntries()"));
        assertFalse(body.contains("AcademyBlocks.BLOCKS.getEntries()"),
                "iterating BLOCKS duplicates every explicit BlockItem and crashes creative inventory");
    }

    private static String blockRegistryId(String blocksSource, String constant) {
        Matcher matcher = BLOCK.matcher(blocksSource);
        while (matcher.find()) if (matcher.group(1).equals(constant)) return matcher.group(2);
        throw new AssertionError("unknown Academy block constant " + constant);
    }
}
