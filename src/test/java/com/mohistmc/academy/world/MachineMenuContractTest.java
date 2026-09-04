package com.mohistmc.academy.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Release contracts for item-conservation and authoritative machine screens. */
class MachineMenuContractTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java").resolve(path));
    }

    @Test void machineRangesFollowTheActuallyAppendedSlots() throws Exception {
        String s = source("com/mohistmc/academy/world/menu/AcademyMenu.java");
        assertTrue(s.contains("getMachineSlotStart() { return getPlayerSlotEnd(); }"));
        assertTrue(s.contains("p_38942_ >= machineStart && p_38942_ < machineEnd"));
        assertFalse(s.contains("p_38942_ < machineSlots"));
    }

    @Test void containerUsesCountAwareVanillaHelpersAndAllowsClearing() throws Exception {
        String s = source("com/mohistmc/academy/world/menu/AcademyMenu.java");
        assertTrue(s.contains("ContainerHelper.removeItem(items"));
        assertTrue(s.contains("ContainerHelper.takeItem(items"));
        assertFalse(s.contains("p_18945_ == ItemStack.EMPTY"));
        assertFalse(s.contains("items.clear()"));
    }

    @Test void validityIsBoundToIdentityRangeLoadAndPermission() throws Exception {
        String s = source("com/mohistmc/academy/world/menu/AcademyMenu.java");
        assertTrue(s.contains("entity == menu.boundEntity()"));
        assertTrue(s.contains("distanceToSqr"));
        assertTrue(s.contains("isLoaded(menu.pos)"));
        assertTrue(s.contains("mayInteract"));
    }

    @Test void matrixIsARealFourSlotAcademyContainer() throws Exception {
        String s = source("com/mohistmc/academy/world/block/entity/MatrixBlockEntity.java");
        assertTrue(s.contains("extends AcademyContainerBlockEntity"));
        assertTrue(s.contains("getContainerSize() { return 4; }"));
    }

    @Test void visibleDynamicStateUsesMenuDataAndNodeRepliesAreCorrelated() throws Exception {
        assertTrue(source("com/mohistmc/academy/world/menu/PhaseGenMenu.java").contains("addDataSlots(machineData)"));
        assertTrue(source("com/mohistmc/academy/world/menu/ImagFusorMenu.java").contains("addDataSlots(machineData)"));
        assertTrue(source("com/mohistmc/academy/world/menu/DevAdvancedMenu.java").contains("addDataSlots(machineData)"));
        String request = source("com/mohistmc/academy/network/RequestNodesPacket.java");
        String matrixRequest = source("com/mohistmc/academy/network/RequestMatrixNetworksPacket.java");
        String ui = source("com/mohistmc/academy/client/gui/AcademyBaseUI.java");
        assertTrue(request.contains("containerId"));
        assertTrue(request.contains("machinePos"));
        assertTrue(request.contains("record RequestKey(UUID playerId, int containerId, BlockPos machinePos)"));
        assertTrue(matrixRequest.contains("record RequestKey(UUID playerId, int containerId, BlockPos nodePos)"));
        assertTrue(ui.contains("screen.menu.containerId"));
        assertTrue(ui.contains("screen.menu.pos.asLong()"));
    }
}
