package com.mohistmc.academy.capability;

/** Exact AcademyCraft 1.0.7 CoFH conversion boundary: {@code 1 IF = 4 RF/FE}. */
public final class ExternalEnergyConversion {
    public static final int FE_PER_IF = 4;

    private ExternalEnergyConversion() {}

    public static int wholeIfFromFe(int fe) {
        return Math.max(0, fe) / FE_PER_IF;
    }

    public static int ifToFe(int imaginaryEnergy) {
        long converted = (long) Math.max(0, imaginaryEnergy) * FE_PER_IF;
        return (int) Math.min(Integer.MAX_VALUE, converted);
    }
}
