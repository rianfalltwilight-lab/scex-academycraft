package com.mohistmc.academy.skill;

/** Pure resource tables and growth caps copied from AcademyCraft 1.0.7 default.conf/CPData. */
public final class LegacyResourceProgression {
    private static final float[] INITIAL_CP = {1800, 1800, 2800, 4000, 5800, 8000};
    private static final float[] MAX_ADDED_CP = {0, 900, 1000, 1500, 1700, 12000};
    private static final float[] INITIAL_OVERLOAD = {100, 100, 150, 240, 350, 500};
    private static final float[] MAX_ADDED_OVERLOAD = {0, 40, 70, 80, 100, 500};

    private LegacyResourceProgression() {}

    private static int level(int value) { return Math.clamp(value, 0, 5); }
    public static float initialCp(int level) { return INITIAL_CP[level(level)]; }
    public static float initialOverload(int level) { return INITIAL_OVERLOAD[level(level)]; }
    public static float maxAddedCp(int level) { return MAX_ADDED_CP[level(level)]; }
    public static float maxAddedOverload(int level) { return MAX_ADDED_OVERLOAD[level(level)]; }

    public static float growCp(float currentAdded, float consumedCp, float rate, int level) {
        if (!Float.isFinite(currentAdded) || !Float.isFinite(consumedCp) || !Float.isFinite(rate)
                || consumedCp < 0 || rate < 0) return Math.clamp(Float.isFinite(currentAdded) ? currentAdded : 0,
                0, maxAddedCp(level));
        return Math.clamp(currentAdded + consumedCp * rate, 0, maxAddedCp(level));
    }

    public static float growOverload(float currentAdded, float consumedOverload, float rate, int level) {
        if (!Float.isFinite(currentAdded) || !Float.isFinite(consumedOverload) || !Float.isFinite(rate)
                || consumedOverload < 0 || rate < 0) return Math.clamp(Float.isFinite(currentAdded) ? currentAdded : 0,
                0, maxAddedOverload(level));
        float perUse = Math.clamp(consumedOverload * rate, 0, 10);
        return Math.clamp(currentAdded + perUse, 0, maxAddedOverload(level));
    }

    public static float courseCpBonus(boolean brain, boolean advanced) {
        return (brain ? 1000 : 0) + (advanced ? 1500 : 0);
    }

    public static float courseOverloadBonus(boolean advanced) { return advanced ? 100 : 0; }
    public static float recoveryMultiplier(boolean mindCourse) { return mindCourse ? 1.2f : 1.0f; }

    /** Extracts usage growth from a rebuilt v1-v3 total (fixed 2000 base, old course bonuses). */
    public static float importedRebuiltUsageCp(float oldTotal, boolean brain, boolean advanced) {
        float oldCourseBonus = (brain ? 1000 : 0) + (advanced ? 1000 : 0);
        return finiteNonNegative(oldTotal - 2000 - oldCourseBonus);
    }

    /** Extracts usage growth from a rebuilt v1-v3 overload total (fixed 500 base). */
    public static float importedRebuiltUsageOverload(float oldTotal, boolean advanced) {
        return finiteNonNegative(oldTotal - 500 - (advanced ? 100 : 0));
    }

    public static float finiteNonNegative(float value) {
        return Float.isFinite(value) ? Math.max(0, value) : 0;
    }

    public record ForcedResources(float cp, float overload) {}

    /** Pure form of 1.0.7 CPData.consumeWithForce; null means hostile/non-finite input. */
    public static ForcedResources consumeWithForce(float currentCp, float currentOverload,
                                                   float maxOverload, float cpCost, float overloadCost) {
        if (!Float.isFinite(currentCp) || !Float.isFinite(currentOverload) || !Float.isFinite(maxOverload)
                || !Float.isFinite(cpCost) || !Float.isFinite(overloadCost)
                || currentCp < 0 || currentOverload < 0 || maxOverload < 0 || cpCost < 0 || overloadCost < 0) {
            return null;
        }
        return new ForcedResources(Math.max(0, currentCp - cpCost),
                Math.clamp(currentOverload + overloadCost, 0, maxOverload));
    }
}
