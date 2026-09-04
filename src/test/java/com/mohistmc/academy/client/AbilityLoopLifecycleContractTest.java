package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import com.mohistmc.academy.network.ChargingHandshake;

class AbilityLoopLifecycleContractTest {
    private static String source(String path) throws Exception {return Files.readString(Path.of("src/main/java").resolve(path));}
    @Test void movementLoopIsSingleClientOwnedAndSessionBound() throws Exception {
        String loop=source("com/mohistmc/academy/client/sound/AbilityLoopSoundManager.java");
        String movement=source("com/mohistmc/academy/skill/ability/electromaster/MagMovementEffect.java");
        String reset=source("com/mohistmc/academy/client/ClientSessionState.java");
        assertTrue(loop.contains("AbstractTickableSoundInstance")&&loop.contains("this.looping = looping"));
        assertTrue(loop.contains("sync(mc, movement")&&loop.contains("\"mag_movement\"")&&loop.contains("stopMovement"));
        assertFalse(movement.contains("AcademySounds.EM_MOVE_LOOP"),"server tick must not stack the loop sound");
        assertFalse(source("com/mohistmc/academy/skill/ability/electromaster/ChargingEffect.java").contains("EM_CHARGE_LOOP"));
        assertFalse(source("com/mohistmc/academy/skill/ability/electromaster/BodyIntensifyEffect.java").contains("EM_INTENSIFY_LOOP"));
        assertTrue(reset.contains("AbilityLoopSoundManager.resetClientSession"));
    }

    @Test void legacyMagManipAndMeltdownerSoundsHaveBoundedLifecycles() throws Exception {
        String loop=source("com/mohistmc/academy/client/sound/AbilityLoopSoundManager.java");
        assertTrue(loop.contains("\"mag_manip\", AcademySounds.EM_LF_LOOP"));
        assertTrue(loop.contains("AcademySounds.MD_MINE_LOOP"));
        assertTrue(loop.contains("\"mine_ray_basic\", \"mine_ray_luck\", \"mine_ray_expert\""));
        assertTrue(loop.contains("\"light_shield\", AcademySounds.MD_SHIELD_LOOP"));
        assertTrue(loop.contains("AcademySounds.MD_MD_CHARGE"));
        assertTrue(loop.contains("false, () -> ChargingHudOverlay.isCharging(\"meltdowner\")"),
                "the finite legacy charge sound must not be restarted as a loop");
        assertTrue(loop.contains("meltdownerWasCharging = false"));
    }

    @Test void mineDetectionUsesFinal112FiveTickRefreshAndBatchedMarkers() throws Exception {
        String effect=source("com/mohistmc/academy/skill/ability/electromaster/MineDetectEffect.java");
        String bridge=source("com/mohistmc/academy/client/ClientPacketBridge.java");
        String renderer=source("com/mohistmc/academy/client/render/MineDetectBatchRenderer.java");
        assertTrue(effect.contains("now+5")&&effect.contains("expiresAt"));
        assertTrue(effect.contains("if(!results.equals(s.lastResults))"),
                "five-tick final-1.12.2 rescans should not resend identical large snapshots");
        assertTrue(bridge.contains("mineDetectSnapshot = p")
                        && bridge.contains("new MineDetectResultPacket(java.util.List.of(), 0)"),
                "disconnect/level teardown must clear the client snapshot");
        assertTrue(renderer.contains("for (MineDetectResultPacket.Entry entry")
                        && renderer.contains("buffers.endBatch(TYPE)"),
                "the final 8400-entry ceiling requires the original single-batch rendering model");
    }

    @Test void staleChargingPacketsCannotOverwriteHudBeforeStateValidation() throws Exception {
        String bridge=source("com/mohistmc/academy/client/ClientPacketBridge.java");
        String input=source("com/mohistmc/academy/client/KeyInputHandler.java");
        assertTrue(bridge.indexOf("acceptChargingState") < bridge.indexOf("setChargingState"));
        assertTrue(input.contains("return false")&&input.contains("return true"));
    }

    @Test void chargingProtocolIsSafeUnderDuplicateReorderedDelayedAndLostPackets() {
        assertEquals(ChargingHandshake.AckAction.ACCEPT,
                ChargingHandshake.ack(true, false, 7L, "skill", 7L, "skill", true, 11L));
        assertEquals(ChargingHandshake.AckAction.IGNORE,
                ChargingHandshake.ack(false, false, 7L, "skill", 7L, "skill", true, 11L));
        assertEquals(ChargingHandshake.AckAction.IGNORE,
                ChargingHandshake.ack(true, false, 8L, "replacement", 7L, "skill", true, 11L));
        assertEquals(ChargingHandshake.AckAction.ACK_AND_CANCEL,
                ChargingHandshake.ack(true, true, 9L, "skill", 9L, "skill", true, 12L));
        assertTrue(ChargingHandshake.serverStartExpired(false, 100L, 201L));
        assertFalse(ChargingHandshake.serverStartExpired(true, 100L, 1000L));
    }
}
