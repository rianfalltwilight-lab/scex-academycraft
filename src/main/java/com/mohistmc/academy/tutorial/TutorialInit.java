package com.mohistmc.academy.tutorial;

import static com.mohistmc.academy.tutorial.Conditions.itemObtained;
import static com.mohistmc.academy.tutorial.ViewGroups.drawsBlock;
import static com.mohistmc.academy.tutorial.ViewGroups.drawsItem;
import static com.mohistmc.academy.tutorial.ViewGroups.recipes;

import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyItems;

/**
 * 教程注册。
 */
public class TutorialInit {

    public static void init() {
        defnTut("welcome");

        defnTut("ores")
                .addCondition(itemObtained(AcademyBlocks.CONSTRAIN_METAL.get()))
                .addCondition(itemObtained(AcademyBlocks.IMAGSIL_ORE.get()))
                .addCondition(itemObtained(AcademyBlocks.CRYSTAL_ORE.get()))
                .addCondition(itemObtained(AcademyBlocks.RESO_ORE.get()))
                .addPreview(drawsBlock(AcademyBlocks.CONSTRAIN_METAL.get()))
                .addPreview(drawsBlock(AcademyBlocks.IMAGSIL_ORE.get()))
                .addPreview(drawsBlock(AcademyBlocks.CRYSTAL_ORE.get()))
                .addPreview(drawsBlock(AcademyBlocks.RESO_ORE.get()))
                .addPreview(drawsItem(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get()))
                .addPreview(recipes(AcademyItems.CONSTRAINT_PLATE.get()))
                .addPreview(recipes(AcademyItems.IMAG_SILICON_INGOT.get()))
                .addPreview(recipes(AcademyItems.WAFER.get()))
                .addPreview(recipes(AcademyItems.IMAG_SILICON_PIECE.get()));

        defnTut("phase_generator")
                .addCondition(itemObtained(AcademyBlocks.PHASE_GEN.get()))
                .addPreview(recipes(AcademyBlocks.PHASE_GEN.get()));

        defnTut("solar_generator")
                .addCondition(itemObtained(AcademyBlocks.SOLAR_GEN.get()))
                .addPreview(recipes(AcademyBlocks.SOLAR_GEN.get()));

        defnTut("wind_generator")
                .addCondition(itemObtained(AcademyBlocks.WINDGEN_BASE.get()))
                .addCondition(itemObtained(AcademyItems.WINDGEN_FAN.get()))
                .addCondition(itemObtained(AcademyBlocks.WINDGEN_MAIN.get()))
                .addCondition(itemObtained(AcademyBlocks.WINDGEN_PILLAR.get()))
                .addPreview(recipes(AcademyBlocks.WINDGEN_BASE.get()))
                .addPreview(recipes(AcademyBlocks.WINDGEN_PILLAR.get()))
                .addPreview(recipes(AcademyBlocks.WINDGEN_MAIN.get()))
                .addPreview(recipes(AcademyItems.WINDGEN_FAN.get()));

        defnTut("metal_former")
                .addCondition(itemObtained(AcademyBlocks.METAL_FORMER.get()))
                .addPreview(recipes(AcademyBlocks.METAL_FORMER.get()));

        defnTut("imag_fusor")
                .addCondition(itemObtained(AcademyBlocks.IMAG_FUSOR.get()))
                .addPreview(recipes(AcademyBlocks.IMAG_FUSOR.get()))
                .addPreview(recipes(AcademyItems.CRYSTAL_NORMAL.get()))
                .addPreview(recipes(AcademyItems.CRYSTAL_PURE.get()));

        defnTut("terminal")
                .addCondition(itemObtained(AcademyItems.TERMINAL_INSTALLER.get()))
                .addCondition(itemObtained(AcademyItems.APP_SKILL_TREE.get()))
                .addCondition(itemObtained(AcademyItems.APP_FREQ_TRANSMITTER.get()))
                .addCondition(itemObtained(AcademyItems.APP_MEDIA_PLAYER.get()))
                .addPreview(recipes(AcademyItems.TERMINAL_INSTALLER.get()))
                .addPreview(recipes(AcademyItems.APP_SKILL_TREE.get()))
                .addPreview(recipes(AcademyItems.APP_FREQ_TRANSMITTER.get()))
                .addPreview(recipes(AcademyItems.APP_MEDIA_PLAYER.get()));

        defnTut("ability_developer")
                .addCondition(itemObtained(AcademyBlocks.DEV_NORMAL.get()))
                .addCondition(itemObtained(AcademyBlocks.DEV_ADVANCED.get()))
                .addCondition(itemObtained(AcademyItems.DEVELOPER_PORTABLE.get()))
                .addPreview(recipes(AcademyBlocks.DEV_NORMAL.get()))
                .addPreview(recipes(AcademyBlocks.DEV_ADVANCED.get()))
                .addPreview(recipes(AcademyItems.DEVELOPER_PORTABLE.get()));

        defnTut("ability_basis");

        defnTut("energy_bridge")
                .addCondition(itemObtained(AcademyBlocks.RF_INPUT.get()))
                .addCondition(itemObtained(AcademyBlocks.RF_OUTPUT.get()))
                .addPreview(recipes(AcademyBlocks.RF_INPUT.get()))
                .addPreview(recipes(AcademyBlocks.RF_OUTPUT.get()));

        defnTut("misc");
        defnTut("develop_ability");
        defnTut("wireless_network");
    }

    public static ACTutorial defnTut(String name) {
        return TutorialRegistry.addTutorial(name);
    }
}
