package com.mohistmc.academy.energy.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class EnergyBoundaryTest {
    @Test void maliciousProviderValuesFailClosed() {
        assertEquals(0, EnergyBoundary.bounded(Double.NaN, 20));
        assertEquals(0, EnergyBoundary.bounded(Double.POSITIVE_INFINITY, 20));
        assertEquals(0, EnergyBoundary.bounded(-5, 20));
        assertEquals(20, EnergyBoundary.bounded(200, 20));
        assertEquals(7, EnergyBoundary.bounded(7, 20));
    }
    @Test void maliciousLimitsCannotPoisonTransferState() {
        assertEquals(0, EnergyBoundary.nonNegative(Double.NaN));
        assertEquals(0, EnergyBoundary.nonNegative(Double.NEGATIVE_INFINITY));
        assertEquals(0, EnergyBoundary.nonNegative(-1));
        assertEquals(12, EnergyBoundary.nonNegative(12));
        assertEquals(EnergyBoundary.MAX_ENERGY, EnergyBoundary.energy(Double.MAX_VALUE));
        assertEquals(EnergyBoundary.MAX_TRANSFER, EnergyBoundary.transfer(Double.MAX_VALUE));
        assertEquals(EnergyBoundary.MAX_CONNECTIONS, EnergyBoundary.capacity(Integer.MAX_VALUE));
        double twoHostileNodes = EnergyBoundary.saturatedAdd(EnergyBoundary.MAX_ENERGY, EnergyBoundary.MAX_ENERGY, EnergyBoundary.MAX_NETWORK_ENERGY);
        assertEquals(EnergyBoundary.MAX_ENERGY * 2, twoHostileNodes);
        assertEquals(1, EnergyBoundary.finiteRatio(twoHostileNodes, twoHostileNodes));
        assertEquals(.5, EnergyBoundary.finiteRatio(EnergyBoundary.MAX_ENERGY, twoHostileNodes));
        assertEquals(0, EnergyBoundary.finiteRatio(Double.NaN, Double.POSITIVE_INFINITY));
    }

}
