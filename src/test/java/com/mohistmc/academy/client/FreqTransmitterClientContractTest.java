package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards the client/server handoff that previously armed after an Esc race. */
class FreqTransmitterClientContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java").resolve(relative));
    }

    @Test
    void openUsesCancelableRequestNonceAndWorldSelectionOnlyAfterServerState() throws Exception {
        String gui = source("com/mohistmc/academy/client/gui/FreqTransmitterGui.java");
        assertTrue(gui.contains("this(true, UUID.randomUUID()"));
        assertTrue(gui.contains("FreqTransmitterActionPacket.open(nonce)"));
        assertTrue(gui.contains("LOCALLY_CANCELED.add(nonce)"));
        assertTrue(gui.contains("LOCALLY_CANCELED.contains(packet.nonce())"));
        assertTrue(gui.contains("packet.state() == FreqTransmitterStatePacket.SELECT_SOURCE"));
        assertTrue(gui.contains("minecraft.setScreen(null)"));
    }

    @Test
    void c2sPayloadCannotForgeCoordinatesAndServerOwnsRealClickRange() throws Exception {
        String action = source("com/mohistmc/academy/network/FreqTransmitterActionPacket.java");
        String manager = source("com/mohistmc/academy/network/FreqTransmitterSessionManager.java");
        assertFalse(action.contains("BlockPos"));
        assertTrue(manager.contains("PlayerInteractEvent.RightClickBlock"));
        assertTrue(manager.contains("event.getHitVec().getLocation()"));
        assertTrue(manager.contains("eyeToBlockDistanceSqr(player, pos)"));
        assertTrue(manager.contains("level.mayInteract(player, pos)"));
    }
}
