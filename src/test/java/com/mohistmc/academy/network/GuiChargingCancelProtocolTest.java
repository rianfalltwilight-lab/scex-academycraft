package com.mohistmc.academy.network;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GuiChargingCancelProtocolTest {
    @Test void lateAcceptedAckForTombstoneMustAbortNotRelease() {
        assertEquals(ChargingHandshake.AckAction.ACK_AND_CANCEL,
                ChargingHandshake.ack(true, true, 7, "railgun", 7, "railgun", true, 99));
    }
    @Test void staleAckCannotCancelAReplacementSession() {
        assertEquals(ChargingHandshake.AckAction.IGNORE,
                ChargingHandshake.ack(true, true, 8, "railgun", 7, "railgun", true, 99));
        assertEquals(ChargingHandshake.AckAction.IGNORE,
                ChargingHandshake.ack(true, true, 7, "meltdowner", 7, "railgun", true, 99));
    }
}
