package com.mohistmc.academy.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnergyBridgeParityContractTest {
    private static final Path ROOT = Path.of("src/main");

    @Test
    void registersBothDirectionalBridgesAndTheirAuthoritativeBoundaries() throws Exception {
        String blocks = source("java/com/mohistmc/academy/world/AcademyBlocks.java");
        String entities = source("java/com/mohistmc/academy/world/AcademyBlockEntities.java");
        String items = source("java/com/mohistmc/academy/world/AcademyItems.java");
        String menus = source("java/com/mohistmc/academy/world/AcademyMenus.java");
        String mod = source("java/com/mohistmc/academy/AcademyCraft.java");
        for (String id : List.of("rf_input", "rf_output")) {
            assertTrue(blocks.contains("\"" + id + "\""));
            assertTrue(entities.contains("\"" + id + "\""));
            assertTrue(items.contains("\"" + id + "\""));
        }
        assertTrue(menus.contains("ENERGY_BRIDGE_MENU"));
        assertTrue(mod.contains("AcademyBlockEntities.RF_INPUT"));
        assertTrue(mod.contains("AcademyBlockEntities.RF_OUTPUT"));

        String base = source("java/com/mohistmc/academy/world/block/entity/EnergyBridgeBlockEntity.java");
        String input = source("java/com/mohistmc/academy/world/block/entity/EnergyBridgeInputBlockEntity.java");
        String output = source("java/com/mohistmc/academy/world/block/entity/EnergyBridgeOutputBlockEntity.java");
        assertTrue(base.contains("MAX_IF = 2000"));
        assertTrue(base.contains("BANDWIDTH_IF = 100.0"));
        assertTrue(base.contains("bridgeStoredFe"));
        assertTrue(input.contains("implements IWirelessGenerator"));
        assertTrue(input.contains("canReceive() { return true; }"));
        assertTrue(input.contains("canExtract() { return false; }"));
        assertTrue(output.contains("implements IWirelessReceiver"));
        assertTrue(output.contains("Direction.values()"));
        assertTrue(output.contains("canReceive() { return false; }"));
        assertTrue(output.contains("canExtract() { return true; }"));
    }

    @Test
    void shipsExactLegacyTexturesRecipesLootAndTutorial() throws Exception {
        Path inputTexture = ROOT.resolve("resources/assets/academy/textures/block/rf_input.png");
        Path outputTexture = ROOT.resolve("resources/assets/academy/textures/block/rf_output.png");
        assertEquals("b5e86bc7cb6b54a3383d9a2b55c73794e5347deeb03d667107dafca6afe7881a",
                sha256(inputTexture));
        assertEquals("ea900ec425d9851b7c9418dafd4efbeec24eb03d065ae6e381649e54da86c71f",
                sha256(outputTexture));
        for (String id : List.of("rf_input", "rf_output")) {
            assertTrue(Files.isRegularFile(ROOT.resolve("resources/assets/academy/blockstates/" + id + ".json")));
            assertTrue(Files.isRegularFile(ROOT.resolve("resources/assets/academy/models/block/" + id + ".json")));
            assertTrue(Files.isRegularFile(ROOT.resolve("resources/assets/academy/models/item/" + id + ".json")));
            assertTrue(Files.isRegularFile(ROOT.resolve("resources/data/academy/loot_table/blocks/" + id + ".json")));
            String recipe = Files.readString(ROOT.resolve("resources/data/academy/recipe/official_" + id + ".json"));
            assertTrue(recipe.contains("\"ABC\""));
            assertTrue(recipe.contains("\" D \""));
        }
        assertTrue(Files.readString(ROOT.resolve("resources/data/academy/recipe/official_rf_input.json"))
                .contains("academy:constraint_plate"));
        assertTrue(Files.readString(ROOT.resolve("resources/data/academy/recipe/official_rf_output.json"))
                .contains("academy:reso_crystal"));
        assertTrue(Files.isRegularFile(ROOT.resolve("resources/assets/academy/tutorials/en_us/energy_bridge.md")));
        assertTrue(Files.isRegularFile(ROOT.resolve("resources/assets/academy/tutorials/zh_cn/energy_bridge.md")));
        assertTrue(source("java/com/mohistmc/academy/tutorial/TutorialInit.java")
                .contains("defnTut(\"energy_bridge\")"));
    }

    private static String source(String path) throws Exception {
        return Files.readString(ROOT.resolve(path));
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }
}
