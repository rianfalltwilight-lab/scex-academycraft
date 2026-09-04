package com.mohistmc.academy.skill;

/** Pure AcademyCraft 1.0.7 Mag Manip curves, kept free of game classes for regression tests. */
public final class MagManipLegacyMath {
    private MagManipLegacyMath() {}

    private static float lerp(float from, float to, float proficiency) {
        return from + (to - from) * proficiency;
    }

    public static float cpCost(float proficiency) { return lerp(140, 270, proficiency); }
    public static float overloadCost(float proficiency) { return lerp(35, 20, proficiency); }
    public static float throwSpeed(float proficiency) { return lerp(.5f, 1, proficiency); }
    /** The local 8-15 curve is dead code in 1.0.7; the entity constructor receives literal 10. */
    public static float impactDamage(float proficiency) { return 10; }
    public static int cooldown(float proficiency) { return (int) lerp(60, 40, proficiency); }
}
