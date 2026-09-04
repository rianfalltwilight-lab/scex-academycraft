package com.mohistmc.academy.skill.ability.aerohand;

/**
 * Pure, bounded tuning functions shared by Aerohand effects and their tests.
 * Keeping the policy independent from Minecraft objects makes the important
 * progression endpoints auditable without a running client or server.
 */
public final class AeroBehaviorMath {
    private AeroBehaviorMath() {}

    public static float coolingReduction(float proficiency) {
        return lerp(80.0f, 180.0f, proficiency);
    }

    public static float cooledOverload(float currentOverload, float proficiency) {
        if (!Float.isFinite(currentOverload) || currentOverload <= 0) return 0;
        return Math.max(0, currentOverload - coolingReduction(proficiency));
    }

    public static double airJetSpeed(float proficiency) {
        return lerp(1.35f, 2.25f, proficiency);
    }

    /** Volcanic Ball retains 35% of its damage at maximum range. */
    public static float volcanicDamage(float pointBlankDamage, double distance, double range) {
        if (!Float.isFinite(pointBlankDamage) || pointBlankDamage <= 0
                || !Double.isFinite(distance) || !Double.isFinite(range) || range <= 0) return 0;
        float travelled = clamp01((float) (Math.max(0, distance) / range));
        return pointBlankDamage * lerp(1.0f, 0.35f, travelled);
    }

    public static int separatorChargeTicks(float proficiency) {
        return Math.round(lerp(30.0f, 18.0f, proficiency));
    }

    public static float separatorRadius(float proficiency) {
        return lerp(3.0f, 5.0f, proficiency);
    }

    public static float separatorDamage(float proficiency) {
        return lerp(12.0f, 24.0f, proficiency);
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
        return lerp(0.80f, 0.55f, proficiency);
    }

    /** Number of ticks between vanilla air consumption steps below mastery. */
    public static int airflowConsumptionInterval(float proficiency) {
        return Math.max(1, Math.round(lerp(1.0f, 5.0f, proficiency)));
    }

    public static int cruiseBombOrbCount(float proficiency) {
        return Math.clamp(3 + (int) Math.floor(clamp01(proficiency) * 3.999f), 3, 6);
    }

    public static int cruiseBombDurationTicks(float proficiency) {
        return Math.round(lerp(200.0f, 400.0f, proficiency));
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
