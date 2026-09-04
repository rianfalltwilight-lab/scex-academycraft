package com.mohistmc.academy.client.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SkillSelectionUiContractTest {
    @Test
    void learnedZeroCostActiveSkillsRemainAssignable() throws Exception {
        String selector = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/client/gui/SkillSlotGui.java"));
        String registry = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/skill/SkillRegistry.java"));

        assertTrue(selector.contains("skill.getType() != SkillType.ACTIVE"));
        assertTrue(selector.contains("data.hasLearnedSkill(skill.getId())"));
        assertFalse(selector.contains("skill.getBaseCpCost() <= 0"),
                "resource settlement strategy must not decide whether an active skill is assignable");

        assertTrue(registry.contains("new Skill.Builder(\"threatening_teleport\""));
        assertTrue(registry.contains("new Skill.Builder(\"flashing\""));
        assertTrue(registry.contains("new Skill.Builder(\"overload_thinking\""));
    }

    @Test
    void legacyTreesUseTheirHandAuthoredCanvasAndSingleParentEdge() throws Exception {
        String gui = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/client/gui/SkillTreeGui.java"));
        String skill = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/skill/Skill.java"));
        assertTrue(skill.contains("hasLegacyTreePosition"));
        assertTrue(gui.contains("allSkills.stream().allMatch(Skill::hasLegacyTreePosition)"));
        assertTrue(gui.contains("treeAreaLeft + scaled((int) Math.round(skill.getTreeX()))"));
        assertTrue(gui.contains("treeAreaTop + scaled((int) Math.round(skill.getTreeY()))"));
        assertFalse(gui.contains("treeAreaWidth / 241.0"),
                "1.0.7 places guiX/guiY directly in the 257x139 area and must not stretch them");
        assertTrue(gui.contains(".limit(1).toList()"),
                "1.0.7 addSkillDep conditions must not render as extra parent lines");
    }
}
