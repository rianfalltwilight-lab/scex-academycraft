package com.mohistmc.academy.client.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards coordinates and layers copied from the official 1.0.7 containers/XML. */
class LegacyMachineScreenLayoutContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java").resolve(path));
    }

    @Test void playerAndMachineSlotsUseLegacyTextureOrigins() throws Exception {
        assertTrue(source("com/mohistmc/academy/world/menu/AcademyMenu.java").contains("INV_X = 6"));
        assertTrue(source("com/mohistmc/academy/world/menu/BaseNodeMenu.java").contains("0, 42, 10"));
        assertTrue(source("com/mohistmc/academy/world/menu/SolarGenMenu.java").contains("0, 42, 81"));
        assertTrue(source("com/mohistmc/academy/world/menu/WindGenBaseMenu.java").contains("0, 42, 80"));
        assertTrue(source("com/mohistmc/academy/world/menu/WindGenMainMenu.java").contains("0, 78, 9"));
        String phase = source("com/mohistmc/academy/world/menu/PhaseGenMenu.java");
        assertTrue(phase.contains("0, 45, 12"));
        assertTrue(phase.contains("1, 112, 51"));
        assertTrue(phase.contains("2, 42, 80"));
        String fusor = source("com/mohistmc/academy/world/menu/ImagFusorMenu.java");
        assertTrue(fusor.contains("FLUID_INPUT_SLOT, 13, 10"));
        assertTrue(fusor.contains("EMPTY_UNIT_SLOT, 143, 10"));
        assertTrue(fusor.contains("ENERGY_INPUT_SLOT, 42, 80"));
    }

    @Test void solarUsesTheOfficialWindbaseLayerRatherThanPhaseGeneratorArt() throws Exception {
        String solar = source("com/mohistmc/academy/client/block/gui/SolarGenGui.java");
        assertTrue(solar.contains("textures/guis/ui/ui_windbase.png"));
        assertFalse(solar.contains("textures/guis/ui/ui_phasegen.png"));
    }

    @Test void machineStatusPanelsUseAuthoritativeMenuData() throws Exception {
        String phase = source("com/mohistmc/academy/client/block/gui/PhaseGenGui.java");
        assertTrue(phase.contains("InfoArea.histEnergy(menu.getEnergy(), menu.getMaxEnergy())"));
        assertTrue(phase.contains("new InfoArea.HistElement(\"IF\", 0xFFB983FB"));
        assertFalse(phase.contains("graphics.fill(barX"));
        String fusor = source("com/mohistmc/academy/client/block/gui/ImagFusorGui.java");
        assertTrue(fusor.contains("setRenderWireless(true)"));
        assertTrue(fusor.contains("InfoArea.histEnergy(menu.getEnergy(), menu.getMaxEnergy())"));
        String solar = source("com/mohistmc/academy/client/block/gui/SolarGenGui.java");
        assertTrue(solar.contains("InfoArea.histBuffer(menu.getEnergy(), menu.getMaxEnergy())"));
        assertTrue(solar.contains("menu.getStatus()"));
        assertFalse(solar.contains("getBlockEntity"));
        assertTrue(source("com/mohistmc/academy/world/menu/SolarGenMenu.java").contains("addDataSlots(machineData)"));
        String windBase = source("com/mohistmc/academy/client/block/gui/WindBaseGui.java");
        assertTrue(windBase.contains("InfoArea.histBuffer(menu.getEnergy(), menu.getMaxEnergy())"));
        assertTrue(windBase.contains("menu.getGenerationRate()"));
        assertFalse(windBase.contains("getBlockEntity"));
        String windMain = source("com/mohistmc/academy/client/block/gui/WindMainGui.java");
        assertTrue(windMain.contains("menu.isFanInstalled()"));
        assertTrue(windMain.contains("menu.isStructureComplete()"));
        assertTrue(source("com/mohistmc/academy/world/menu/WindGenBaseMenu.java")
                .contains("addDataSlots(machineData)"));
        assertTrue(source("com/mohistmc/academy/world/menu/WindGenMainMenu.java")
                .contains("addDataSlots(machineData)"));
        String node = source("com/mohistmc/academy/client/block/gui/BaseNodeGui.java");
        assertTrue(node.contains("menu.isConnected()"));
        assertTrue(node.contains("InfoArea.histEnergy(menu.getNodeEnergy(), menu.getNodeMaxEnergy())"));
        assertFalse(node.contains("getBlockEntity"));
        assertTrue(source("com/mohistmc/academy/world/menu/BaseNodeMenu.java")
                .contains("case 8 -> node.isConnected() ? 1 : 0"));
    }

    @Test void imagFusorUsesTheOfficial107ProgressLayerAndXmlCoordinates() throws Exception {
        String fusor = source("com/mohistmc/academy/client/block/gui/ImagFusorGui.java");
        assertTrue(fusor.contains("textures/guis/progress/progress_fusor.png"));
        assertTrue(fusor.contains("PROGRESS_X = 58"));
        assertTrue(fusor.contains("PROGRESS_Y = 47"));
        assertTrue(fusor.contains("PROGRESS_W = 61"));
        assertFalse(fusor.contains("graphics.fill(61, 52"));
    }

    @Test void wirelessNodeListPagesAndPasswordsAreNotTruncatedOrLeaked() throws Exception {
        String ui = source("com/mohistmc/academy/client/gui/AcademyBaseUI.java");
        assertTrue(ui.contains("nodePageOffset"));
        assertTrue(ui.contains("nodePageOffset + NODES_PER_PAGE"));
        assertTrue(ui.contains("nodePageOffset - NODES_PER_PAGE"));
        assertTrue(ui.contains("availableNodeIndices()"));
        assertTrue(ui.contains("NetworkInputLimits.PASSWORD"));
        assertTrue(ui.contains("\"*\".repeat(inputPass.length())"));
        String node = source("com/mohistmc/academy/client/block/gui/BaseNodeGui.java");
        // A password-only edit must not resend an unrelated name from this
        // viewer's opening snapshot. Both fields retain explicit empty optionals.
        assertTrue(node.contains("nameEdit ? java.util.Optional.of(nodeNameInput.toString()) : java.util.Optional.empty()"));
        assertTrue(node.contains("nameEdit ? java.util.Optional.empty() : java.util.Optional.of(nodePasswordInput.toString())"));
        assertTrue(node.contains("if (!menu.actionSessionReady() || !menu.canEditNode() || menu.pos == null) return false;"));
        // Enter before the nonce arrives must leave focus and draft intact;
        // only a successfully submitted property ends editing.
        assertTrue(node.contains("if (submitNodeConfig()) editFocus = EditFocus.NONE;"));
        assertFalse(node.contains("submitNodeConfig();\n            editFocus = EditFocus.NONE;"));
        assertTrue(ui.contains("if ((keyCode == 257 || keyCode == 335) && !menu.actionSessionReady()) return true;"));
    }

    @Test void nodeNamesRefreshFromThePublicMirrorWithoutReplacingUnsubmittedDrafts() throws Exception {
        String gui = source("com/mohistmc/academy/client/block/gui/BaseNodeGui.java");
        String menu = source("com/mohistmc/academy/world/menu/BaseNodeMenu.java");
        assertTrue(menu.contains("return boundedNodeName(node.getNodeName());"));
        assertTrue(gui.contains("String current = menu.getCurrentNodeName();"));
        assertTrue(gui.contains("if (!nodeNameEdited && editFocus != EditFocus.NAME)"));
        assertFalse(gui.contains("if (nodeInputInitialized) return;"));
        assertTrue(gui.contains("if (nameEdit) {\n            nodeNameEdited = false;\n        } else {\n            passwordEdited = false;\n            nodePasswordInput.setLength(0);\n        }"));
        assertFalse(gui.contains("nodeNameEdited = false;\n        passwordEdited = false;"));
        assertTrue(gui.contains("if (submitNodeConfig()) editFocus = EditFocus.NONE;"));
    }

    @Test void matrixControlsLiveInASeparateSynchronizedPanelWithoutABlankWirelessPage() throws Exception {
        String gui = source("com/mohistmc/academy/client/block/gui/MatrixGui.java");
        String menu = source("com/mohistmc/academy/world/menu/MatrixMenu.java");
        assertTrue(gui.contains("PANEL_X = 183"));
        assertTrue(gui.contains("this.imageWidth = PANEL_X + PANEL_W"));
        assertTrue(source("com/mohistmc/academy/client/gui/AcademyBaseUI.java")
                .contains("this.leftPos, this.topPos, graphics"));
        assertTrue(gui.contains("setRenderWireless(false)"));
        assertTrue(gui.contains("menu.isInitialized()"));
        assertTrue(gui.contains("menu.hasInitializationMaterials()"));
        assertFalse(gui.contains("getBlockEntity"));
        assertTrue(menu.contains("addDataSlots(machineData)"));
        assertTrue(menu.contains("getInitialSsid()"));
        assertTrue(menu.contains("getOwnerLabel()"));
    }

    @Test void sidebarHighlightsTheVisiblePageRatherThanTheConstructorDefault() throws Exception {
        String ui = source("com/mohistmc/academy/client/gui/AcademyBaseUI.java");
        assertTrue(ui.contains("|| !panelActive ? 1 : 0.8f"));
        assertTrue(ui.contains("panelActive && (wirelessState == WirelessState.WIFI"));
        assertTrue(ui.contains("panelActive = false"));
        assertTrue(ui.contains("panelActive = true"));
    }

    @Test void regularMachineOriginReservesTheLegacySideInformationPanel() throws Exception {
        String ui = source("com/mohistmc/academy/client/gui/AcademyBaseUI.java");
        assertTrue(ui.contains("RegularMachineLayout.machineLeft(this.width, reserveSideInfoArea)"));
        assertTrue(ui.contains("RegularMachineLayout.centeredElementX(this.leftPos"));
        assertFalse(ui.contains("((this.width - GUI_WIDTH) / 2)"));
        assertTrue(source("com/mohistmc/academy/client/block/gui/PhaseGenGui.java")
                .contains("renderStandardMachinePanel(graphics, UI_PHASE_GEN)"));
        assertTrue(source("com/mohistmc/academy/client/block/gui/SolarGenGui.java")
                .contains("int guiLeft = this.leftPos"));
    }

    @Test void officialMachineLayersDoNotReceiveVanillaContainerCaptions() throws Exception {
        String windMain = source("com/mohistmc/academy/client/block/gui/WindMainGui.java");
        String devNormal = source("com/mohistmc/academy/client/block/gui/DevNormalGui.java");
        assertTrue(windMain.contains("protected void renderLabels"));
        assertTrue(devNormal.contains("protected void renderLabels"));
    }

    @Test void developerScreensHaveInteractiveCompactPagesAndExternalFullCanvasSidebars() throws Exception {
        for (String name : new String[] {"DevNormalGui.java", "DevAdvancedGui.java"}) {
            String gui = source("com/mohistmc/academy/client/block/gui/" + name);
            assertTrue(gui.contains("compactLayout = width < RegularMachineLayout.DEVELOPER_COMPOSITION_WIDTH"), name);
            assertTrue(gui.contains("RegularMachineLayout.machineLeft(width, true)"), name);
            assertTrue(gui.contains("RegularMachineLayout.developerMenuLeft(width)"), name);
            assertTrue(gui.contains("renderCompactDeveloperPanel"), name);
            assertTrue(gui.contains("RegularMachineLayout.developerSidebarLeft(width)"), name);
        }
        String advanced = source("com/mohistmc/academy/client/block/gui/DevAdvancedGui.java");
        assertTrue(advanced.contains("renderCompactDeveloperPanel(graphics, false)"));
        assertFalse(advanced.contains("renderDeveloperInventoryOverlay(graphics)"));
        assertFalse(advanced.contains("drawSlotFrame"));
    }
}
