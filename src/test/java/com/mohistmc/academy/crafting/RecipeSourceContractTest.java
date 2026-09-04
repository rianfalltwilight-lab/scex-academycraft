package com.mohistmc.academy.crafting;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RecipeSourceContractTest {
    private static final Path ROOT = Path.of("src/main/resources/data/academy/recipe");

    @Test void restoresExactMachineRecipeInventory() throws Exception {
        List<Path> json;
        try (var files = Files.list(ROOT)) {
            json = files.filter(p -> p.toString().endsWith(".json")).toList();
        }
        assertEquals(112, json.size(), "83 AcademyCraft recipes plus 29 clean-room ExtraAcC compatibility recipes are required");
        long former = json.stream().filter(p -> read(p).contains("\"academy:metal_forming\"")).count();
        Pattern fusorType = Pattern.compile(
                "\\\"type\\\"\\s*:\\s*\\\"academy:imag_fus(?:or|ing)\\\"");
        long fusor = json.stream().filter(p -> fusorType.matcher(read(p)).find()).count();
        assertEquals(28, former);
        assertEquals(7, fusor);
        for (String name : List.of("fuse_crystal_normal.json", "fuse_crystal_pure.json")) {
            String recipe = read(ROOT.resolve(name));
            assertTrue(recipe.contains("\"type\":\"academy:imag_fusor\""),
                    name + " must use the current MohistMC upstream recipe id");
            assertTrue(recipe.contains("\"phaseLiquid\":"),
                    name + " must use the current MohistMC upstream field spelling");
            assertFalse(recipe.contains("\"phase_liquid\":"));
        }
    }

    @Test void restoresLegacyCrossModOreDictionaryRefiningThroughCommonTags() {
        for (String metal : List.of("copper", "tin", "lead", "platinum", "silver", "nickel")) {
            String recipe = read(ROOT.resolve("refine_common_" + metal + ".json"));
            assertTrue(recipe.contains("\"type\":\"academy:metal_forming\""), metal);
            assertTrue(recipe.contains("\"mode\":\"refine\""), metal);
            assertTrue(recipe.contains("\"tag\":\"c:ores/" + metal + "\""), metal);
            assertTrue(recipe.contains("\"output_tag\":\"c:ingots/" + metal + "\""), metal);
            assertTrue(recipe.contains("\"output_count\":2"), metal);
        }
        String implementation = read(Path.of(
                "src/main/java/com/mohistmc/academy/crafting/MetalFormingRecipe.java"));
        assertTrue(implementation.contains("TagKey.codec(Registries.ITEM).optionalFieldOf(\"output_tag\")"));
        assertTrue(implementation.contains("set.stream().findFirst()"),
                "1.0.7 chose the first registered OreDictionary ingot");
        assertTrue(implementation.contains("getOutput(level.registryAccess())"),
                "tag output must be resolved against the active server registry");
    }

    @Test void imagFusorKeepsCurrentUpstreamAndRebuildDatapackCompatibility() {
        String types = read(Path.of(
                "src/main/java/com/mohistmc/academy/crafting/AcademyRecipeTypes.java"));
        String serializers = read(Path.of(
                "src/main/java/com/mohistmc/academy/crafting/AcademyRecipeSerializers.java"));
        String recipe = read(Path.of(
                "src/main/java/com/mohistmc/academy/crafting/ImagFusorRecipe.java"));

        assertTrue(types.contains("RECIPE_TYPES.register(\"imag_fusor\""));
        assertTrue(types.contains("IMAG_FUSING = IMAG_FUSOR"),
                "source integrations compiled against the rebuild alias must keep working");
        assertTrue(serializers.contains("SERIALIZERS.register(\"imag_fusor\""),
                "current MohistMC datapacks must decode");
        assertTrue(serializers.contains("SERIALIZERS.register(\"imag_fusing\""),
                "0.0.4-0.0.10 rebuild datapacks must continue to decode");
        assertTrue(recipe.contains("optionalFieldOf(\"phaseLiquid\")"));
        assertTrue(recipe.contains("optionalFieldOf(\"phase_liquid\")"));
        assertTrue(recipe.contains("optionalFieldOf(\"inputCount\", 1)"));
        assertTrue(recipe.contains("getSerializer() { return AcademyRecipeSerializers.IMAG_FUSOR.get(); }"));
        assertTrue(recipe.contains("getType() { return AcademyRecipeTypes.IMAG_FUSOR.get(); }"));
        String machine = read(Path.of(
                "src/main/java/com/mohistmc/academy/world/block/entity/ImagFusorBlockEntity.java"));
        String jei = read(Path.of(
                "src/main/java/com/mohistmc/academy/client/jei/ImagFusorJeiCategory.java"));
        assertTrue(machine.contains("input.shrink(recipe.inputCount())"),
                "machine must consume the recipe-declared amount atomically");
        assertTrue(jei.contains("copy.setCount(recipe.inputCount())"),
                "JEI must render the real Imag Fusor input count");
    }

    @Test void restoresLegacyVanillaOreRefiningRecipesExactly() {
        Map<String, String[]> expected = Map.of(
                "gold", new String[]{"minecraft:gold_ingot", "2"},
                "iron", new String[]{"minecraft:iron_ingot", "2"},
                "emerald", new String[]{"minecraft:emerald", "2"},
                "quartz", new String[]{"minecraft:quartz", "2"},
                "diamond", new String[]{"minecraft:diamond", "2"},
                "redstone", new String[]{"minecraft:redstone_block", "1"},
                "lapis", new String[]{"minecraft:lapis_lazuli", "12"},
                "coal", new String[]{"minecraft:coal", "2"});
        expected.forEach((ore, output) -> {
            String recipe = read(ROOT.resolve("refine_vanilla_" + ore + ".json"));
            assertTrue(recipe.contains("\"type\":\"academy:metal_forming\""), ore);
            assertTrue(recipe.contains("\"mode\":\"refine\""), ore);
            assertTrue(recipe.contains("\"tag\":\"c:ores/" + ore + "\""), ore);
            assertTrue(recipe.contains("\"id\":\"" + output[0] + "\""), ore);
            assertTrue(recipe.contains("\"count\":" + output[1]), ore);
        });
    }

    @Test void officialJarRecipeInventoryAndAllIdsAreMappedExactly() throws Exception {
        Set<String> expected = Set.of(
                "ability_interf","app_freq_transmitter","app_media_player","app_skill_tree","brain_comp",
                "calc_chip","calc_chip_2","cons_ingot","cons_plate","conv_comp","crystal0","data_chip","data_chip_2",
                "dev_advanced","dev_normal","dev_normal_2","dev_portable","ene_unit","ene_unit_2","ene_unit_3",
                "frame","fusor","fusor_2","fusor_3","imagsil_ingot","info_comp","mag_hook","magnetic_coil",
                "mat","mat_core_0","mat_core_1","mat_core_2","matter_unit","metal_former","node0","node1","node2",
                "phase_gen","plateiron","reso_comp","si_piece","silbarn","solar_gen","terminal","tutorial","wafer",
                "windgen_base","windgen_fan","windgen_main","windgen_pillar",
                "rf_input","rf_output","rf_input_from_output","rf_output_from_input");
        List<Path> official;
        try (var files = Files.list(ROOT)) {
            official = files.filter(p -> p.getFileName().toString().startsWith("official_")).toList();
        }
        Set<String> actual = official.stream().map(p -> p.getFileName().toString()
                .replaceFirst("^official_", "").replaceFirst("\\.json$", "")).collect(Collectors.toSet());
        assertEquals(expected, actual, "official 1.0.7 default.recipe + always-on RFSupport inventory drifted");

        Set<String> academyIds = Set.of("imag_silicon_piece","wafer","data_chip","calc_chip","reinforced_iron_plate",
                "machine_frame","phase_gen","solar_gen","windgen_base","windgen_pillar","windgen_main","windgen_fan",
                "node_basic","node_standard","node_advanced","matter_unit_none","energy_unit","constraint_plate",
                "constraint_ingot","terminal_installer","imag_fusor","metal_former","matrix","mat_core_0","mat_core_1",
                "mat_core_2","imag_silicon_ingot","imagsil_ore","constraint_metal","crystal_low","crystal_normal",
                "crystal_pure","crystal_ore","info_component","brain_component","resonance_component",
                "energy_convert_component","reso_crystal","app_skill_tree","app_media_player","app_freq_transmitter",
                "mag_hook","developer_portable","dev_normal","dev_advanced","ability_interferer","tutorial","silbarn",
                "magnetic_coil","rf_input","rf_output");
        Set<String> vanillaIds = Set.of("redstone","glowstone_dust","iron_ingot","quartz","glass_pane","glass",
                "iron_bars","redstone_block","gold_nugget","compass","note_block","white_bed","piston","glowstone",
                "shears","ender_pearl","diamond","book");
        Pattern id = Pattern.compile("\\\"(?:item|id)\\\"\\s*:\\s*\\\"(academy|minecraft):([a-z0-9_]+)\\\"");
        for (Path path : official) {
            var matcher = id.matcher(read(path)); int refs = 0;
            while (matcher.find()) {
                refs++;
                assertTrue((matcher.group(1).equals("academy") ? academyIds : vanillaIds).contains(matcher.group(2)),
                        () -> "unregistered/unreviewed id in " + path + ": " + matcher.group());
            }
            assertTrue(refs >= 2, "recipe must contain input and output ids: " + path);
        }
        // EnergyUnit is durability-backed and therefore maxStackSize=1 on 1.21.1.
        // The legacy x2/x4 outputs are preserved as distinct alternative recipes but
        // safely yield one unit, otherwise ItemStack.CODEC rejects the entire datapack entry.
        for (String name : List.of("official_ene_unit_2.json", "official_ene_unit_3.json")) {
            String recipe = read(ROOT.resolve(name));
            assertTrue(recipe.contains("\"id\": \"academy:energy_unit\""));
            assertTrue(recipe.contains("\"count\": 1"), name + " must respect non-stackable EnergyUnit");
            assertFalse(recipe.contains("\"count\": 2"));
            assertFalse(recipe.contains("\"count\": 4"));
        }
    }

    @Test void jeiIsOptionalAndClientIsolated() throws Exception {
        String gradle = Files.readString(Path.of("build.gradle"));
        String toml = Files.readString(Path.of("src/main/templates/META-INF/neoforge.mods.toml"));
        String plugin = Files.readString(Path.of("src/main/java/com/mohistmc/academy/client/jei/AcademyJeiPlugin.java"));
        String metalCategory = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/client/jei/MetalFormerJeiCategory.java"));
        String fusorCategory = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/client/jei/ImagFusorJeiCategory.java"));
        assertTrue(gradle.contains("compileOnly \"mezz.jei:jei-${minecraft_version}-common-api"));
        assertFalse(gradle.contains("implementation \"mezz.jei"));
        assertTrue(toml.contains("modId=\"jei\""));
        assertTrue(toml.contains("type=\"optional\""));
        assertTrue(plugin.contains("@OnlyIn(Dist.CLIENT)"));
        assertTrue(plugin.contains("@JeiPlugin"));
        assertTrue(plugin.contains("registerItemSubtypes(ISubtypeRegistration"),
                "charged and empty creative variants need distinct JEI identities");
        assertTrue(plugin.contains("AcademyItems.ENERGY_UNIT.get(), ENERGY_SUBTYPE"));
        assertTrue(plugin.contains("AcademyItems.DEVELOPER_PORTABLE.get(), ENERGY_SUBTYPE"));
        assertTrue(plugin.contains("? \"empty\" : \"charged\""),
                "JEI energy subtype must distinguish the two intentional creative variants");
        assertTrue(plugin.contains("context == UidContext.Ingredient ? energyState(stack) : null"),
                "recipe lookup must ignore charge while the ingredient list distinguishes it");
        assertTrue(plugin.contains("context == UidContext.Ingredient ? energyState(stack) : \"\""),
                "legacy JEI recipe UIDs must retain the same broad matching rule");
        assertTrue(plugin.contains("registerRecipeTransferHandlers"));
        assertTrue(Pattern.compile("METAL_FORMING,\\s*36,\\s*1,\\s*0,\\s*36")
                .matcher(plugin).find(), "JEI transfer must target input slot 36 and player slots 0..35");
        assertTrue(plugin.contains("new IRecipeTransferInfo<ImagFusorMenu"),
                "Imag Fusor inputs are non-contiguous and need a custom transfer handler");
        assertTrue(plugin.contains("List.of(menu.slots.get(38), menu.slots.get(36))"),
                "JEI must map crystal then phase-unit input without exposing empty-unit output slot 37");
        assertTrue(fusorCategory.contains("RecipeIngredientRole.INPUT"),
                "consumed phase units must be recipe inputs, not reusable catalysts");
        assertFalse(fusorCategory.contains("RecipeIngredientRole.CATALYST"));
        assertTrue(metalCategory.contains("stack.setCount(recipe.getInputCount())"),
                "JEI must render the recipe's real input count");
        assertTrue(metalCategory.contains("jei.academy.metal_former.mode"),
                "JEI must expose the selected machine mode");
    }

    private static String read(Path p) {
        try { return Files.readString(p); }
        catch (Exception e) { throw new AssertionError(e); }
    }
}
