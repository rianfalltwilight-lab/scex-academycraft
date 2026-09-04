package com.mohistmc.academy.worldgen;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldgenResourceContractTest {
    private static final Path DATA = Path.of("src/main/resources/data/academy");
    @Test void restoresAllOfficialBasicResourcesAndBiomeInjection() throws Exception {
        for (String id : List.of("ore_reso","ore_constraint","ore_crystal","ore_imagsil","phase_liquid_lake")) {
            assertTrue(Files.isRegularFile(DATA.resolve("worldgen/configured_feature/" + id + ".json")));
            assertTrue(Files.isRegularFile(DATA.resolve("worldgen/placed_feature/" + id + ".json")));
        }
        String ores = Files.readString(DATA.resolve("neoforge/biome_modifier/academy_ores.json"));
        for (String id : List.of("ore_reso","ore_constraint","ore_crystal","ore_imagsil")) assertTrue(ores.contains("academy:" + id));
        String lakes = Files.readString(DATA.resolve("neoforge/biome_modifier/phase_liquid_lakes.json"));
        assertTrue(lakes.contains("academy:phase_liquid_lake"));
        assertTrue(Files.readString(DATA.resolve("worldgen/placed_feature/ore_reso.json")).contains("\"count\": 18"));
        assertTrue(Files.readString(DATA.resolve("worldgen/placed_feature/ore_constraint.json")).contains("\"count\": 24"));
        assertTrue(Files.readString(DATA.resolve("worldgen/placed_feature/ore_crystal.json")).contains("\"count\": 48"));
        assertTrue(Files.readString(DATA.resolve("worldgen/placed_feature/ore_imagsil.json")).contains("\"count\": 22"));
        for (String id : List.of("ore_reso","ore_constraint","ore_crystal","ore_imagsil")) {
            String placed = Files.readString(DATA.resolve("worldgen/placed_feature/" + id + ".json"));
            assertTrue(placed.contains("\"type\": \"academy:ores_enabled\""));
            assertTrue(placed.contains("\"absolute\": 0"));
            assertTrue(placed.contains("\"absolute\": 59"),
                    "legacy rand.nextInt(60) generated only at Y=0..59");
        }
        String phaseLake = Files.readString(DATA.resolve("worldgen/placed_feature/phase_liquid_lake.json"));
        assertTrue(phaseLake.contains("\"type\": \"academy:chance_30\""));
        assertTrue(phaseLake.contains("\"type\": \"academy:phase_liquid_enabled\""));
        String placement = Files.readString(Path.of("src/main/java/com/mohistmc/academy/worldgen/AcademyPlacementModifiers.java"));
        assertTrue(placement.contains("random.nextDouble() < 0.3D"), "phase lake probability must equal legacy 0.3/chunk, not 1/3");
        assertTrue(placement.contains("ACConfig.Server.generateOres()"));
        assertTrue(placement.contains("ACConfig.Server.generatePhaseLiquid()"));
        String config = Files.readString(Path.of("src/main/java/com/mohistmc/academy/config/ACConfig.java"));
        assertTrue(config.contains("define(\"genOres\", true)"));
        assertTrue(config.contains("define(\"genPhaseLiquid\", true)"));
        assertTrue(phaseLake.contains("\"absolute\": 5"));
        assertTrue(phaseLake.contains("\"absolute\": 34"));

        String bootstrap = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/worldgen/AcademyWorldGen.java"));
        assertTrue(bootstrap.contains("ORE_CONSTRAINT"));
        assertTrue(bootstrap.contains("AcademyBlocks.CONSTRAIN_METAL"));
        assertFalse(bootstrap.contains("BlockTags.DEEPSLATE_ORE_REPLACEABLES"));
    }

    @Test void inductionFactorsRemainObtainableFromLegacyStructureChests() throws Exception {
        Path global = Path.of("src/main/resources/data/neoforge/loot_modifiers/global_loot_modifiers.json");
        String index = Files.readString(global);
        for (String structure : List.of("abandoned_mineshaft", "desert_pyramid", "jungle_temple",
                "stronghold_library", "simple_dungeon")) {
            assertTrue(index.contains("academy:induction_factor_" + structure));
            String modifier = Files.readString(DATA.resolve("loot_modifiers/induction_factor_"
                    + structure + ".json"));
            assertTrue(modifier.contains("\"type\": \"neoforge:add_table\""));
            assertTrue(modifier.contains("minecraft:chests/" + structure));
            assertTrue(modifier.contains("academy:chests/induction_factors"));
        }
        String table = Files.readString(DATA.resolve("loot_table/chests/induction_factors.json"));
        for (String category : List.of("electromaster", "meltdowner", "teleporter", "vecmanip",
                "aerohand", "telekinesis")) {
            assertTrue(table.contains("academy:factor_" + category));
        }
        assertTrue(table.contains("\"weight\": 4"), "legacy category weights must remain equal");
    }

    @Test void academyOresAndMachinesKeepLegacyPickaxeTiers() throws Exception {
        Path tags = Path.of("src/generated/resources/data/minecraft/tags/block");
        String wood = Files.readString(tags.resolve("incorrect_for_wooden_tool.json"));
        String stone = Files.readString(tags.resolve("incorrect_for_stone_tool.json"));

        assertTrue(wood.contains("academy:constraint_metal"),
                "legacy harvest-level-1 constraint ore must reject wooden tools");
        assertFalse(stone.contains("academy:constraint_metal"),
                "legacy harvest-level-1 constraint ore must accept stone tools");
        for (String id : List.of("crystal_ore", "reso_ore", "imagsil_ore")) {
            assertTrue(wood.contains("academy:" + id));
            assertTrue(stone.contains("academy:" + id),
                    "legacy harvest-level-2 ore must require iron or better: " + id);
        }
        for (String id : List.of("dev_normal", "dev_advanced", "windgen_base",
                "windgen_main", "windgen_pillar")) {
            assertTrue(stone.contains("academy:" + id),
                    "legacy harvest-level-2 machine must require iron or better: " + id);
        }
    }

    @Test void mediaItemsRemainObtainableFromLegacyStructureChests() throws Exception {
        Path global = Path.of("src/main/resources/data/neoforge/loot_modifiers/global_loot_modifiers.json");
        String index = Files.readString(global);
        for (String structure : List.of("abandoned_mineshaft", "desert_pyramid", "jungle_temple",
                "stronghold_library", "simple_dungeon")) {
            assertTrue(index.contains("academy:media_" + structure));
            String modifier = Files.readString(DATA.resolve("loot_modifiers/media_" + structure + ".json"));
            assertTrue(modifier.contains("\"type\": \"neoforge:add_table\""));
            assertTrue(modifier.contains("minecraft:chests/" + structure));
            assertTrue(modifier.contains("academy:chests/media"));
        }
        String table = Files.readString(DATA.resolve("loot_table/chests/media.json"));
        for (String id : List.of("only_my_railgun", "level5_judgelight", "sisters_noise")) {
            assertTrue(table.contains("academy:media_" + id));
        }
        assertTrue(table.contains("\"chance\": 0.25"),
                "three legacy weight-4 media entries should retain half the six-factor aggregate chance");
        assertTrue(table.contains("\"weight\": 4"), "legacy media weights must remain equal");
    }
}
