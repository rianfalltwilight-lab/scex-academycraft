package com.mohistmc.academy.world;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LootTableResourceContractTest {
    private static final Path BLOCKS = Path.of("src/main/resources/data/academy/loot_table/blocks");

    @Test void everyPlayerFacingAcademyBlockHasItsRequiredLootContract() throws Exception {
        List<String> selfDrops = List.of("ability_interferer", "cat_engine", "constraint_metal",
                "dev_advanced", "dev_normal", "imag_fusor",
                "imagsil_ore", "machine_frame", "matrix", "metal_former", "node_basic",
                "node_advanced", "node_standard", "phase_gen", "solar_gen", "windgen_base",
                "windgen_main", "windgen_pillar", "rf_input", "rf_output");
        for (String id : selfDrops) {
            String json = Files.readString(BLOCKS.resolve(id + ".json"));
            assertTrue(json.contains("\"name\": \"academy:" + id + "\""), id + " must self-drop");
        }
        String crystal = Files.readString(BLOCKS.resolve("crystal_ore.json"));
        assertTrue(crystal.contains("academy:crystal_low"));
        String crystalCompact = crystal.replaceAll("\\s+", "");
        assertTrue(crystalCompact.contains("\"min\":1") && crystalCompact.contains("\"max\":3"));
        assertSpecialOreContract(crystal, "crystal_ore", "crystal_low");
        String reso = Files.readString(BLOCKS.resolve("reso_ore.json"));
        assertTrue(reso.contains("academy:reso_crystal"));
        String resoCompact = reso.replaceAll("\\s+", "");
        assertTrue(resoCompact.contains("\"min\":1") && resoCompact.contains("\"max\":2"));
        assertSpecialOreContract(reso, "reso_ore", "reso_crystal");
        assertEmptyProxyLoot("matrix_sub");
        assertEmptyProxyLoot("dev_normal_sub");
        assertEmptyProxyLoot("dev_advanced_sub");
        assertEmptyProxyLoot("windgen_fan_block");
        assertEquals(26, Files.list(BLOCKS).filter(p -> p.toString().endsWith(".json")).count());
    }

    private static void assertEmptyProxyLoot(String id) throws Exception {
        String json = Files.readString(BLOCKS.resolve(id + ".json")).replaceAll("\\s+", "");
        assertTrue(json.contains("\"pools\":[]"),
                "internal structure proxy must have an explicit empty loot table");
        assertFalse(json.contains("academy:" + id),
                "internal structure proxy must never drop an obtainable helper item");
    }

    private static void assertSpecialOreContract(String json, String ore, String normalDrop) {
        assertTrue(json.contains("minecraft:alternatives"), ore + " needs Silk/non-Silk alternatives");
        assertTrue(json.contains("minecraft:match_tool"));
        assertTrue(json.contains("minecraft:enchantments"));
        assertTrue(json.contains("minecraft:silk_touch"));
        assertTrue(json.contains("\"name\": \"academy:" + ore + "\""), "Silk Touch must drop the ore block");
        assertTrue(json.contains("\"name\": \"academy:" + normalDrop + "\""), "normal drop changed");
        assertTrue(json.contains("minecraft:apply_bonus"));
        assertTrue(json.contains("minecraft:fortune"));
        assertTrue(json.contains("minecraft:ore_drops"));
        assertTrue(json.contains("minecraft:explosion_decay"));
        assertTrue(json.contains("\"random_sequence\": \"academy:blocks/" + ore + "\""));
        assertTrue(json.indexOf("minecraft:silk_touch") < json.indexOf("academy:" + normalDrop),
                "Silk Touch alternative must be evaluated first");
    }
}
