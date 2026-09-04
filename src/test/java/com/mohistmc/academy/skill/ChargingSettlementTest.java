package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChargingSettlementTest {
    @Test void successfulReleaseIsTheOnlyRewardingOutcome() {
        var d = ChargingSettlement.decide(ChargingSettlement.TickOutcome.RELEASE, 20, 10, true, true);
        assertTrue(d.attemptRelease());
        assertTrue(d.grantProficiency());
        assertTrue(d.applyCooldown());
        assertFalse(d.abortResource());
    }

    @Test void resourceAbortNeverAttemptsOrRewards() {
        var d = ChargingSettlement.decide(ChargingSettlement.TickOutcome.ABORT_RESOURCE, 20, 10, true, true);
        assertFalse(d.attemptRelease());
        assertFalse(d.grantProficiency());
        assertFalse(d.applyCooldown());
        assertTrue(d.abortResource());
    }

    @Test void insufficientChargeOrFailedReleaseNeverRewards() {
        var shortCharge = ChargingSettlement.decide(ChargingSettlement.TickOutcome.RELEASE, 9, 10, true, true);
        var cannotUse = ChargingSettlement.decide(ChargingSettlement.TickOutcome.RELEASE, 20, 10, false, true);
        var failedRelease = ChargingSettlement.decide(ChargingSettlement.TickOutcome.RELEASE, 20, 10, true, false);
        for (var d : new ChargingSettlement.Decision[]{shortCharge, cannotUse, failedRelease}) {
            assertFalse(d.grantProficiency());
            assertFalse(d.applyCooldown());
        }
        assertFalse(shortCharge.attemptRelease());
        assertFalse(cannotUse.attemptRelease());
        assertTrue(failedRelease.attemptRelease());
    }
}
