package com.mohistmc.academy.client.gui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WirelessMachineUiContractTest {
    private static String source(String p) throws Exception { return Files.readString(Path.of("src/main/java").resolve(p)); }

    @Test void emptyNodeResponseIsDistinctFromLoadingAndExplained() throws Exception {
        String ui = source("com/mohistmc/academy/client/gui/AcademyBaseUI.java");
        assertTrue(ui.contains("nodeResponseReceived"));
        assertTrue(ui.contains("正在扫描附近节点"));
        assertTrue(ui.contains("附近没有可用节点"));
        assertTrue(ui.contains("放置无线节点即可连接；矩阵仅用于扩展网络"));
        assertTrue(ui.contains("pendingNodeData != null"));
        assertTrue(ui.contains("WirelessState.WIFI && this.panelActive"));
        assertTrue(ui.contains("nodeRequestDeadline"));
        assertTrue(ui.contains("gameTime + 40"));
        assertTrue(ui.contains("RegularMachineLayout.centeredElementX(this.leftPos"));
        assertTrue(ui.contains("A click must still target exactly"));
        assertTrue(ui.contains("RegularMachineLayout.centeredElementX(this.leftPos, 150, -5)"));
        assertTrue(ui.contains("this.topPos + 62 + (availIndex * 13), 150, 16"));
    }

    @Test void realClientGateCoversProtectedNodeMouseKeyboardAndServerFlow() throws Exception {
        String ui = source("com/mohistmc/academy/client/gui/AcademyBaseUI.java");
        String gate = source("com/mohistmc/academy/client/gate/MachineVisualGate.java");
        assertTrue(ui.contains("connectFirstProtectedNodeForVisualGate"));
        assertTrue(ui.contains("mouseClicked(leftPos + 145.0"));
        assertTrue(ui.contains("charTyped(password.charAt(index), 0)"));
        assertTrue(ui.contains("keyPressed(257, 0, 0)"));
        assertTrue(gate.contains("connectFirstProtectedNodeForVisualGate(\"gate-pass\")"));
        assertTrue(gate.contains("node.setPassword(\"gate-pass\")"));
        assertTrue(gate.contains("advanced developer linked to the protected standalone node"));
        assertTrue(gate.contains("node rename traversed the real editor/C2S path"));
        assertTrue(gate.contains("V activation and mapped keyboard skill traversed KeyMapping"));
    }

    @Test void regularMachineCompositionIncludesAllThreeLegacyLayers() throws Exception {
        String ui = source("com/mohistmc/academy/client/gui/AcademyBaseUI.java");
        int parent = ui.indexOf("graphics, PARENT_BACKGROUND");
        int inventory = ui.indexOf("graphics, UI_INVENTORY");
        int overlay = ui.indexOf("graphics, overlay");
        assertTrue(parent >= 0 && parent < inventory && inventory < overlay,
                "legacy machine pages require parent, inventory, then machine overlay");
    }

    @Test void auxiliaryWirelessPageReplacesRatherThanOverlaysMachineWidgets() throws Exception {
        String base = source("com/mohistmc/academy/client/gui/AcademyBaseUI.java");
        assertTrue(base.contains("if (panelActive)"));
        assertTrue(base.contains("super.renderBackground(stack, mouseX, mouseY, p_97798_)"));
        assertTrue(base.contains("else {\n            super.render(stack, mouseX, mouseY, p_97798_)"));
    }

    @Test void matrixReadinessIsSharedByClientAndServerAuthority() throws Exception {
        String matrix = source("com/mohistmc/academy/world/block/entity/MatrixBlockEntity.java");
        String gui = source("com/mohistmc/academy/client/block/gui/MatrixGui.java");
        String menu = source("com/mohistmc/academy/world/menu/MatrixMenu.java");
        String packet = source("com/mohistmc/academy/network/InitMatrixPacket.java");
        assertTrue(matrix.contains("hasInitializationMaterials()"));
        assertTrue(matrix.contains("initializationCoreLevel()"));
        // The client must consume the server-synchronized ContainerData bit.
        // Reading a client-side block entity directly races chunk/update-tag
        // delivery and made the INIT control randomly disabled after opening.
        assertTrue(menu.contains("be.hasInitializationMaterials() ? 2 : 0"));
        assertTrue(menu.contains("hasInitializationMaterials()"));
        assertTrue(gui.contains("menu.hasInitializationMaterials()"));
        assertTrue(packet.contains("matrix.hasInitializationMaterials()"));
        assertTrue(packet.contains("installed machine"));
        assertTrue(!packet.contains("matrix.getItems().get(slot).shrink(1)"));
    }

    @Test void everyFormedMatrixPartForwardsInteractionToTheMainMachine() throws Exception {
        String sub = source("com/mohistmc/academy/world/block/MatrixSubBlock.java");
        assertTrue(sub.contains("public InteractionResult useWithoutItem"));
        assertTrue(sub.contains("BlockPos mainPos = findMain(level, pos)"));
        assertTrue(sub.contains("matrix.useWithoutItem(mainState, level, mainPos, player, hitResult)"));
        assertTrue(sub.contains("if (mainPos == null) return InteractionResult.PASS"));
    }

    @Test void nodeNameOpeningSnapshotDoesNotDependOnTheClientBlockEntity() throws Exception {
        String menu = source("com/mohistmc/academy/world/menu/BaseNodeMenu.java");
        String ui = source("com/mohistmc/academy/client/gui/AcademyBaseUI.java");
        assertTrue(menu.contains("writeUtf(boundedNodeName(node.getNodeName()), NetworkInputLimits.NODE_NAME)"));
        assertTrue(menu.contains("data.readUtf(NetworkInputLimits.NODE_NAME)"));
        assertTrue(menu.contains("getInitialNodeName()"));
        for (String block : new String[] {"NodeBasic.java", "NodeStandard.java", "NodeAdvanced.java"}) {
            String node = source("com/mohistmc/academy/world/block/" + block);
            assertTrue(node.contains("BaseNodeMenu.writeOpeningData(buffer, pos, node, player)"));
        }
        String nodeGui = source("com/mohistmc/academy/client/block/gui/BaseNodeGui.java");
        assertTrue(nodeGui.contains("menu.getInitialNodeName()"));
        assertTrue(!ui.contains("getNodeBlockEntity()"));
        assertTrue(!ui.contains("isNodeBlock()"));
    }

    @Test void jeiReservesTheLegacySidebarAndInformationCard() throws Exception {
        String ui = source("com/mohistmc/academy/client/gui/AcademyBaseUI.java");
        String plugin = source("com/mohistmc/academy/client/jei/AcademyJeiPlugin.java");
        assertTrue(ui.contains("getJeiExtraAreas()"));
        assertTrue(ui.contains("InfoArea.resolvePanelX(this.leftPos)"));
        assertTrue(ui.contains("new Rect2i(panelX, this.topPos + InfoArea.Y"));
        assertTrue(plugin.contains("addGenericGuiContainerHandler"));
        assertTrue(plugin.contains("screen.getJeiExtraAreas()"));
    }

    @Test void nodeMatrixConnectionUsesTheRelayAware107DiscoveryRule() throws Exception {
        String world = source("com/mohistmc/academy/energy/impl/WiWorldData.java");
        String packet = source("com/mohistmc/academy/network/ConnectNodeToMatrixPacket.java");
        assertTrue(world.contains("isNetworkDiscoverable(WirelessNet net"));
        assertTrue(world.contains("net.hasLoadedEndpointWithin(x, y, z, range)"));
        assertTrue(world.contains("net.isInRange(x, y, z)"));
        assertTrue(packet.contains("data.isNetworkDiscoverable(targetNetwork"));
        assertTrue(!packet.contains("distance > nodeRange * nodeRange"));
    }

    @Test void remoteNodeAccessUsesTheLegacyPublicOrPasswordContract() throws Exception {
        String discovery = source("com/mohistmc/academy/network/RequestNodesPacket.java");
        String connect = source("com/mohistmc/academy/network/ConnectToNodePacket.java");
        String wireless = source("com/mohistmc/academy/energy/impl/WirelessSystem.java");
        assertFalse(discovery.contains("level.mayInteract(player, bp)"));
        assertFalse(connect.contains("level.mayInteract(player, packet.nodePos())"));
        assertTrue(connect.contains("level.mayInteract(player, packet.machinePos())"));
        assertTrue(connect.contains("WirelessSystem.linkGenerator"));
        assertTrue(wireless.contains("passwordMatches(node.getPassword(), password)"));
    }
}
