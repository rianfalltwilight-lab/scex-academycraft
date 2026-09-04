package com.mohistmc.academy.skill.ability.telekinesis;

/** Pure, testable balance and boundary rules for the Telekinesis category. */
public final class TelekinesisRules {
    public static final int PAPER_DRILL_REQUIRED_PAPER = 64;
    public static final int PAPER_DRILL_PULSE_INTERVAL = 1;

    private TelekinesisRules() {}

    public static float overloadThinkingCost(float proficiency) {
        return 100.0f;
    }

    public static float overloadThinkingRestore(float proficiency) {
        return lerp(1000.0f, 2000.0f, proficiency);
    }

    public static float insulationReduction(float proficiency, boolean electromasterOrMeltdowner) {
        return lerp(0.10f, 0.20f, proficiency) * (electromasterOrMeltdowner ? 2.0F : 1.0F);
    }

    public static float mitigateAbilityDamage(float amount, float proficiency,
                                              boolean electromasterOrMeltdowner) {
        if (!Float.isFinite(amount) || amount <= 0) return 0;
        return amount * (1.0f - insulationReduction(proficiency, electromasterOrMeltdowner));
    }

    public static boolean mayEnterHardenedStance(boolean passiveAvailable,
                                                  boolean crouching, boolean onGround) {
        return passiveAvailable && crouching && onGround;
    }

    public static float paperDrillDamage(float proficiency) {
        return lerp(10.0f, 15.0f, proficiency);
    }

    public static double paperDrillRange(float proficiency) {
        return 5.0;
    }

    public static double psychoTransmissionRange(float proficiency) {
        return lerp(8.0f, 12.0f, proficiency);
    }

    private static float lerp(float from, float to, float proficiency) {
        float p = Float.isFinite(proficiency) ? Math.max(0, Math.min(1, proficiency)) : 0;
        return from + (to - from) * p;
    }
}
