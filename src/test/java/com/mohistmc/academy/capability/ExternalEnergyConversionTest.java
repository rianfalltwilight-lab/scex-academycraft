package com.mohistmc.academy.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExternalEnergyConversionTest {
    @Test
    void preservesLegacyFourRfPerIfRatioWithoutPartialIfClaims() {
        assertEquals(4, ExternalEnergyConversion.FE_PER_IF);
        assertEquals(0, ExternalEnergyConversion.wholeIfFromFe(3));
        assertEquals(1, ExternalEnergyConversion.wholeIfFromFe(4));
        assertEquals(25, ExternalEnergyConversion.wholeIfFromFe(103));
        assertEquals(0, ExternalEnergyConversion.ifToFe(-1));
        assertEquals(400, ExternalEnergyConversion.ifToFe(100));
        assertEquals(Integer.MAX_VALUE, ExternalEnergyConversion.ifToFe(Integer.MAX_VALUE));
    }
}
