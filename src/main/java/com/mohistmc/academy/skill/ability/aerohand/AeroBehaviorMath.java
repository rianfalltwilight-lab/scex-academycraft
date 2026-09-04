package com.mohistmc.academy.skill.ability.aerohand;

/**
 * Pure, bounded tuning functions shared by Aerohand effects and their tests.
 * Keeping the policy independent from Minecraft objects makes the important
 * progression endpoints auditable without a running client or server.
 */
public final class AeroBehaviorMath {
    private AeroBehaviorMath() {}

    public static float coolingReduction(float proficiency) {
        return lerp(200.0f, 800.0f, proficiency);
    }

    public static float cooledOverload(float currentOverload, float proficiency) {
        if (!Float.isFinite(currentOverload) || currentOverload <= 0) return 0;
        return Math.max(0, currentOverload - coolingReduction(proficiency));
    }

    public static double airJetSpeed(float proficiency) {
        return lerp(2.0f, 4.0f, proficiency);
    }

    /** ExtraAcC's projectile retains half of its initial damage at its 80-tick lifetime. */
    public static float volcanicDamage(float pointBlankDamage, double distance, double range) {
        if (!Float.isFinite(pointBlankDamage) || pointBlankDamage <= 0
                || !Double.isFinite(distance) || !Double.isFinite(range) || range <= 0) return 0;
        float travelled = clamp01((float) (Math.max(0, distance) / range));
        return pointBlankDamage * lerp(1.0f, 0.5f, travelled);
    }

    public static int separatorChargeTicks(float proficiency) {
        return Math.round(lerp(30.0f, 18.0f, proficiency));
    }

    public static float separatorRadius(float proficiency) {
        return 3.0f;
    }

    public static float separatorDamage(float proficiency) {
        return lerp(40.0f, 60.0f, proficiency);
    }

    /** Maximum raw fall damage before vanilla enchantment/effect reductions. */
    public static float ascendingAirDamageCap(float proficiency) {
        return lerp(8.0f, 2.0f, proficiency);
    }

    public static float cappedFallDistance(float distance, float damageMultiplier, float proficiency) {
        if (!Float.isFinite(distance) || distance <= 0) return 0;
        if (!Float.isFinite(damageMultiplier) || damageMultiplier <= 0) return distance;
        float maximumDistance = 3.0f + ascendingAirDamageCap(proficiency) / damageMultiplier;
        return Math.min(distance, maximumDistance);
    }

    public static float offenseArmourDamageMultiplier(float proficiency) {
        return lerp(0.10f, 0.05f, proficiency);
    }

    /** Number of ticks between vanilla air consumption steps below mastery. */
    public static int airflowConsumptionInterval(float proficiency) {
        return Math.max(1, Math.round(lerp(1.0f, 5.0f, proficiency)));
    }

    public static int cruiseBombOrbCount(float proficiency) {
        float p = clamp01(proficiency);
        if (p >= 1.0f) return 8;
        if (p >= 0.75f) return 7;
        if (p >= 0.5f) return 6;
        if (p >= 0.25f) return 5;
        return 4;
    }

    public static int cruiseBombDurationTicks(float proficiency) {
        return 72_000;
    }

    public static float cruiseBombDamage(float proficiency) {
        return lerp(4.0f, 8.0f, proficiency);
    }

    private static float lerp(float low, float high, float proficiency) {
        return low + (high - low) * clamp01(proficiency);
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0;
        return Math.clamp(value, 0.0f, 1.0f);
    }
}
