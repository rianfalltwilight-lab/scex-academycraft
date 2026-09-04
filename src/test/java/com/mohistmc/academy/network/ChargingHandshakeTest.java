package com.mohistmc.academy.network;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChargingHandshakeTest {
    @Test void lateAcceptedAckAfterFortyTicksIsCancelledWithItsEpoch() {
        assertTrue(ChargingHandshake.shouldTombstone(true,0,41));
        assertEquals(ChargingHandshake.AckAction.ACK_AND_CANCEL,
                ChargingHandshake.ack(true,true,7,"railgun",7,"railgun",true,99));
    }
    @Test void earlyReleaseAndNackHaveTerminalActions() {
        assertEquals(ChargingHandshake.AckAction.ACK_AND_CANCEL,
                ChargingHandshake.ack(true,true,8,"railgun",8,"railgun",true,100));
        assertEquals(ChargingHandshake.AckAction.CLEAR,
                ChargingHandshake.ack(true,false,8,"railgun",8,"railgun",false,0));
    }
    @Test void staleGenerationCannotAffectNewRequestAndServerHasStartTimeout() {
        assertEquals(ChargingHandshake.AckAction.IGNORE,
                ChargingHandshake.ack(true,false,9,"railgun",8,"railgun",true,101));
        assertTrue(ChargingHandshake.serverStartExpired(false,10,111));
        assertFalse(ChargingHandshake.serverStartExpired(true,10,1000));
    }
}
