package com.mohistmc.academy.world.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class MachineStateSanitizerTest {
    @Test void clampsCorruptMachinePersistenceValues() {
        assertEquals(0, MachineStateSanitizer.clampCounter(-1, 40));
        assertEquals(39, MachineStateSanitizer.clampCounter(Integer.MAX_VALUE, 40));
        assertEquals(0, MachineStateSanitizer.clampAmount(-1, 8000));
        assertEquals(8000, MachineStateSanitizer.clampAmount(Integer.MAX_VALUE, 8000));
        assertEquals(0, MachineStateSanitizer.clampFinite(Float.NaN, 5000));
        assertEquals(0, MachineStateSanitizer.clampFinite(Float.POSITIVE_INFINITY, 5000));
        assertEquals(5000, MachineStateSanitizer.clampFinite(Float.MAX_VALUE, 5000));
    }
}
