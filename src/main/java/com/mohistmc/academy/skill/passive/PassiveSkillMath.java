package com.mohistmc.academy.skill.passive;

/** Pure, deterministic formulas ported from AcademyCraft 1.0.7 passives. */
public final class PassiveSkillMath {
    private PassiveSkillMath() {}

    public static float radiationMultiplier(float proficiency) {
        return lerp(1.4f, 1.8f, clamp01(proficiency));
    }

    public static float radiationProficiency(float maxCp, float level5InitialCp) {
        if (level5InitialCp <= 0) throw new IllegalArgumentException("level5InitialCp must be positive");
        return clamp01(maxCp / level5InitialCp);
    }

    public static float teleportCritProbability(int tier, float dimFolding, float spaceFluct) {
        return switch (tier) {
            case 0 -> learnedLerp(0.10f, 0.20f, dimFolding) + learnedLerp(0.18f, 0.25f, spaceFluct);
            case 1 -> learnedLerp(0.10f, 0.15f, spaceFluct);
            case 2 -> learnedLerp(0.01f, 0.03f, spaceFluct);
            default -> throw new IllegalArgumentException("tier must be 0..2");
        };
    }

    public static float teleportCritMultiplier(int tier) {
        return switch (tier) { case 0 -> 1.3f; case 1 -> 1.6f; case 2 -> 2.6f;
            default -> throw new IllegalArgumentException("tier must be 0..2"); };
    }

    public static float deviationReduction(float proficiency) { return lerp(0.4f, 0.9f, clamp01(proficiency)); }
    public static float deviationTickCp(float proficiency) { return lerp(18.0f, 7.5f, clamp01(proficiency)); }
    public static float deviationTickOverload(float proficiency) { return lerp(0.5f, 0.2f, clamp01(proficiency)); }
    public static float deviationStartOverload(float proficiency) { return lerp(80f, 50f, clamp01(proficiency)); }
    public static float deviationEntityCp(float proficiency) { return lerp(15f, 12f, clamp01(proficiency)); }

    private static float learnedLerp(float a, float b, float proficiency) {
        return proficiency < 0 ? 0 : lerp(a, b, clamp01(proficiency));
    }
    private static float clamp01(float value) { return Math.max(0, Math.min(1, value)); }
    private static float lerp(float a, float b, float value) { return a + (b - a) * value; }
}
