package com.mohistmc.academy.world.block.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MachineOwnershipTest {
    @Test
    void ownerCanManageButUnrelatedVisitorCannot() {
        UUID owner = UUID.randomUUID();
        assertTrue(MachineOwnership.canManage(owner, owner, false));
        assertFalse(MachineOwnership.canManage(owner, UUID.randomUUID(), false));
    }

    @Test
    void administratorCanManageOwnedAndUnownedMachines() {
        UUID actor = UUID.randomUUID();
        assertTrue(MachineOwnership.canManage(UUID.randomUUID(), actor, true));
        assertTrue(MachineOwnership.canManage(null, actor, true));
        assertTrue(MachineOwnership.canClaimLegacyByPolicy(null, true));
        assertFalse(MachineOwnership.canClaimLegacyByPolicy(UUID.randomUUID(), true));
        assertFalse(MachineOwnership.canClaimLegacyByPolicy(null, false));
    }

    @Test
    void missingOwnerNeverMakesAnOrdinaryVisitorManager() {
        assertFalse(MachineOwnership.canManage(null, UUID.randomUUID(), false));
        assertFalse(MachineOwnership.canManage(null, null, false));
    }
}
