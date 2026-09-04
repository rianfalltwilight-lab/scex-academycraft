package com.mohistmc.academy.client.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DevMachineUiContractTest {
    private static String source(String p) throws Exception { return Files.readString(Path.of("src/main/java").resolve(p)); }

    @Test void treeRestoresTheFinal112DeveloperCanvasAndAuthoritativeActions() throws Exception {
        String s = source("com/mohistmc/academy/client/gui/SkillTreeGui.java");
        String packet = source("com/mohistmc/academy/network/OpenDevGuiPacket.java");
        assertTrue(s.contains("LEGACY_GUI_WIDTH = 400"));
        assertTrue(s.contains("LEGACY_GUI_HEIGHT = 187"));
        assertTrue(s.contains("parent_background_developerright.png"));
        assertTrue(s.contains("parent_background_developerleft.png"));
        assertTrue(s.contains("effect_developer_background.png"));
        assertTrue(s.contains("consoleInput"));
        assertTrue(s.contains("\"learn\".equals(command)"));
        assertTrue(s.contains("\"reset\".equals(command)"));
        assertTrue(s.contains("detailOpen"));
        assertTrue(s.contains("getProficiency"));
        assertTrue(s.contains("getPrerequisites"));
        assertFalse(s.contains("stateMark"));
        assertTrue(s.contains("LEGACY_LINE"));
        assertTrue(s.contains("Math.atan2(dy, dx)"));
        assertTrue(s.contains("treeAreaLeft + scaled((int) Math.round(skill.getTreeX()))"));
        assertTrue(s.contains("drawLegacyRequirements"));
        assertTrue(s.contains("data.hasLearnedSkill(pendingSkillId)"));
        assertTrue(s.contains("new LearnSkillPacket"));
        assertTrue(s.contains("linkedNodeName"));
        assertTrue(packet.contains("MAX_NODE_NAME_LENGTH"));
        assertTrue(packet.contains("String nodeName"));
    }

    @Test void advancedMachineUsesTheLegacyHandInventoryResetFlow() throws Exception {
        String tree = source("com/mohistmc/academy/client/gui/SkillTreeGui.java");
        String packet = source("com/mohistmc/academy/network/LearnSkillPacket.java");
        String menu = source("com/mohistmc/academy/world/menu/DevAdvancedMenu.java");
        assertTrue(tree.contains("LearnSkillPacket.RESET_ACTION"));
        assertTrue(tree.contains("getMainHandItem().is(AcademyItems.MAGNETIC_COIL.get())"));
        assertTrue(packet.contains("startReset"));
        assertTrue(packet.contains("completeReset"));
        assertTrue(menu.contains("false"));
        assertFalse(menu.contains("addAcademySlot"));
    }

    @Test void bothMachinesUseTheirLiveMenuAndNonceBoundSkillTreeAction() throws Exception {
        String block = source("com/mohistmc/academy/world/block/DevMachineBase.java");
        String normal = source("com/mohistmc/academy/client/block/gui/DevNormalGui.java");
        String menu = source("com/mohistmc/academy/world/menu/DevNormalMenu.java");
        String command = source("com/mohistmc/academy/network/ConsoleCommandPacket.java");
        String bridge = source("com/mohistmc/academy/client/ClientPacketBridge.java");
        String network = source("com/mohistmc/academy/network/OpenDevNetworkPacket.java");
        String normalGui = source("com/mohistmc/academy/client/block/gui/DevNormalGui.java");
        String advancedGui = source("com/mohistmc/academy/client/block/gui/DevAdvancedGui.java");
        assertTrue(block.contains("new OpenDevGuiPacket"));
        assertTrue(block.contains("DevLearningSessionManager.issue"));
        assertTrue(block.contains("serverPlayer.closeContainer();"));
        assertTrue(block.indexOf("serverPlayer.closeContainer();")
                < block.indexOf("new OpenDevGuiPacket"));
        assertFalse(block.contains("player.openMenu(getMenuProvider(state, level, pos), pos)"));
        assertTrue(normal.contains("new ConsoleCommandPacket(menu.pos, \"learn\")"));
        assertTrue(normal.contains("if (!panelActive && button == 0"));
        assertTrue(normal.contains("\"技能树\""));
        assertTrue(menu.contains("addDataSlots(machineData)"));
        assertTrue(menu.contains("isBoundTo"));
        assertTrue(command.contains("player.containerMenu instanceof DevNormalMenu"));
        assertTrue(command.contains("player.containerMenu instanceof DevAdvancedMenu"));
        assertTrue(command.contains("player.closeContainer()"));
        assertTrue(command.contains("DevLearningSessionManager.issue(player, type, sessionPos)"));
        assertTrue(command.contains("new OpenDevGuiPacket"));
        assertTrue(bridge.contains("new SkillTreeGui(false, false"));
        assertTrue(source("com/mohistmc/academy/client/block/gui/DevAdvancedGui.java")
                .contains("if (!panelActive && button == 0"));
        assertTrue(network.contains("new OpenDevNetworkPagePacket"));
        assertTrue(network.contains("DevMachineType.ADVANCED"));
        assertTrue(network.contains("instanceof DevAdvanced"));
        assertTrue(bridge.contains("gui.openNetworkPage"));
        assertTrue(normalGui.contains("openInitialWirelessPanel()"));
        assertTrue(advancedGui.contains("openInitialWirelessPanel()"));
        assertTrue(source("com/mohistmc/academy/client/gui/SkillTreeGui.java")
                .contains("devType != DevMachineType.PORTABLE"));
    }

    @Test void wirelessReturnCommandRemainsCodecBoundAndHasNoDormantTextField() throws Exception {
        String advanced = source("com/mohistmc/academy/client/block/gui/DevAdvancedGui.java");
        String command = source("com/mohistmc/academy/network/ConsoleCommandPacket.java");
        assertTrue(command.contains("public static final int MAX_COMMAND_LENGTH = 8"));
        assertTrue(advanced.contains("reopenSkillTree"));
        assertFalse(advanced.contains("consoleInput"));
    }

    @Test void skillTreeUsesTheOfficialLegacyNodeAndLearnButtonLayers() throws Exception {
        String s = source("com/mohistmc/academy/client/gui/SkillTreeGui.java");
        assertTrue(s.contains("textures/guis/developer/skill_back.png"));
        assertTrue(s.contains("textures/guis/developer/skill_outline.png"));
        assertTrue(s.contains("textures/guis/developer/skill_view_outline_glow.png"));
        assertTrue(s.contains("textures/guis/button/button_learn.png"));
        assertTrue(s.contains("graphics.setColor(1, 1, 1, 1)"));
    }
}
