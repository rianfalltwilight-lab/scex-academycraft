package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalizationResourceContractTest {
    private static final Path LANG = Path.of("src/main/resources/assets/academy/lang");

    @Test
    void internalAndJeiKeysNeverLeakAsRawTranslationIds() throws Exception {
        List<String> required = List.of(
                "item.academy.logo",
                "block.academy.dev_advanced_sub",
                "block.academy.dev_normal_sub",
                "block.academy.mag_manip_escrow",
                "block.academy.matrix_sub",
                "block.academy.phase_liquid",
                "block.academy.windgen_base_sub",
                "block.academy.windgen_fan_block",
                "container.academy.imag_fusor",
                "gui.academy.data_terminal",
                "key.academy.open_terminal",
                "gui.academy.matrix.link_nodes",
                "gui.academy.matrix.unlink_nodes",
                "ac.gui.crafttype.shaped",
                "ac.gui.crafttype.shapeless",
                "ac.tutorial.crafting",
                "ac.tutorial.no_recipe",
                "ac.tutorial.update",
                "jei.academy.metal_former.mode",
                "ac.settings.cat.generic",
                "ac.settings.prop.attackPlayer",
                "ac.settings.prop.destroyBlocks",
                "ac.settings.prop.headsOrTails",
                "ac.settings.enabled",
                "ac.settings.disabled",
                "ac.settings.cat.interface",
                "ac.settings.prop.editHud",
                "ac.settings.prop.skillSounds",
                "ac.hud_editor.title",
                "ac.hud_editor.hint",
                "ac.hud_editor.jade_zone",
                "ac.hud_editor.cpbar",
                "ac.hud_editor.keyhints",
                "ac.hud_editor.reset",
                "ac.headsOrTails.0",
                "ac.headsOrTails.1");
        for (String locale : List.of("en_us", "ja_jp", "ko_kr", "ru_ru", "zh_cn", "zh_tw")) {
            String json = Files.readString(LANG.resolve(locale + ".json"));
            for (String key : required) {
                assertTrue(json.contains("\"" + key + "\""), locale + " is missing " + key);
            }
        }
    }

    @Test
    void screensReuseExistingLocalizedItemAndBlockNames() throws Exception {
        String freq = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/client/gui/FreqTransmitterGui.java"));
        String solar = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/world/block/SolarGen.java"));
        assertTrue(freq.contains("item.academy.app_freq_transmitter"));
        assertTrue(solar.contains("block.academy.solar_gen"));
    }
}
