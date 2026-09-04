package com.mohistmc.academy.skill.passive;

/** Exact linear tables used by VecDeviationContext/VecReflectionContext in AcademyCraft 1.0.7. */
public final class VecDefenseLegacyMath {
    private VecDefenseLegacyMath() {}

    public static float deviationTickCost(float proficiency) {
        return lerp(13, 5, proficiency);
    }

    public static float deviationSecondaryCp(float proficiency) {
        return lerp(5, 2.5f, proficiency);
    }

    public static float deviationSecondaryOverload(float proficiency) {
        return lerp(.5f, .2f, proficiency);
    }

    public static float deviationEntityCost(float proficiency) {
        return lerp(15, 12, proficiency);
    }

    public static float deviationDamageCost(float proficiency) {
        return lerp(15, 12, proficiency);
    }

    public static float deviationDamageMultiplier(float proficiency) {
        return 1 - lerp(.4f, .9f, proficiency);
    }

    public static float reflectionTickCost(float proficiency) {
        return lerp(15, 11, proficiency);
    }

    public static float reflectionEntityCost(float proficiency, float difficulty) {
        return difficulty * lerp(300, 160, proficiency);
    }

    public static float reflectionDamageCost(float proficiency, float damage) {
        return lerp(20, 15, proficiency) * damage;
    }

    public static float reflectedDamage(float proficiency, float damage) {
        return lerp(.6f, 1.2f, proficiency) * damage;
    }

    private static float lerp(float from, float to, float value) {
        return from + (to - from) * value;
    }
}
