package com.mohistmc.academy.skill;

/** Pure legacy-parity rules for the Ability Interferer. */
public final class AbilityInterferenceRules {
    public static final int MIN_RANGE = 10;
    public static final int MAX_RANGE = 100;
    public static final int RANGE_STEP = 10;
    public static final int MAX_ENERGY = 10_000;
    public static final int PAYMENT_INTERVAL_TICKS = 10;

    private AbilityInterferenceRules() {}

    public static int clampRange(int range) {
        return Math.clamp(range, MIN_RANGE, MAX_RANGE);
    }

    /** The 1.12.2 GUI changes the configured range in ten-block steps. */
    public static int stepRange(int range, int direction) {
        return clampRange(range + Integer.signum(direction) * RANGE_STEP);
    }

    /** Legacy TileAbilityInterferer pays range squared once per ten ticks. */
    public static int pulseCost(int range) {
        int bounded = clampRange(range);
        return bounded * bounded;
    }

    /** Legacy selection uses an axis-aligned cube, rather than a sphere. */
    public static boolean contains(double centerX, double centerY, double centerZ,
                                   double x, double y, double z, int range) {
        int bounded = clampRange(range);
        return Math.abs(x - centerX) <= bounded
                && Math.abs(y - centerY) <= bounded
                && Math.abs(z - centerZ) <= bounded;
    }

    public static boolean affects(boolean creative, boolean whitelisted,
                                  double centerX, double centerY, double centerZ,
                                  double x, double y, double z, int range) {
        return !creative && !whitelisted
                && contains(centerX, centerY, centerZ, x, y, z, range);
    }
}
