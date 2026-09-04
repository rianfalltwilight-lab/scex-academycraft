package com.mohistmc.academy.skill;

/** Pure AcademyCraft 1.0.7 ability-level progress formula. */
public final class LegacyLevelProgress {
    private LegacyLevelProgress() {}

    public static float threshold(int level, int controllableSkillsAtLevel) {
        if (controllableSkillsAtLevel <= 0) return 0.0f;
        return controllableSkillsAtLevel * (level == 4 ? 1.333f : 0.666f);
    }

    public static float fraction(float accumulatedExp, float threshold) {
        if (threshold <= 0.0f) return 1.0f;
        if (!Float.isFinite(accumulatedExp)) return 0.0f;
        return Math.clamp(accumulatedExp / threshold, 0.0f, 1.0f);
    }

    public static boolean canLevelUp(boolean hasAbility, int level, float accumulatedExp, float threshold) {
        return hasAbility && level >= 1 && level < 5 && fraction(accumulatedExp, threshold) >= 1.0f;
    }
}
