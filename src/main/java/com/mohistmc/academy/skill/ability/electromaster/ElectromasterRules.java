package com.mohistmc.academy.skill.ability.electromaster;

/** Pure legacy-compatible rules shared by effects and unit tests. */
public final class ElectromasterRules {
    private ElectromasterRules() {}

    public static boolean shouldConsumeBodyIntensifyTick(int tick) {
        return tick <= 40;
    }

    public static int bodyIntensifyEffectiveTicks(int tick) {
        return Math.min(40, Math.max(0, tick));
    }

    public static double bodyIntensifyProbability(int tick) {
        return (bodyIntensifyEffectiveTicks(tick) - 10.0) / 18.0;
    }

    public static float thunderClapChargeFactor(int ticks) {
        return 1.0f + 0.2f * ((ticks - 40.0f) / 60.0f);
    }

    public static boolean shouldConsumeThunderClapTick(int tick) {
        return tick <= 40;
    }

    /** MineDetect's client handler capped the interpolated 15-30 block range at 28. */
    public static float mineDetectRange(float proficiency) {
        return Math.min(28.0f, 15.0f + 15.0f * proficiency);
    }

    /**
     * Legacy advanced MineDetect rendered harvest level + 1, capped at color index 3.
     * The two modern tag flags describe whether wood and stone are too weak.
     */
    public static int mineDetectColorLevel(boolean incorrectForWooden, boolean incorrectForStone) {
        if (!incorrectForWooden) return 1;
        return incorrectForStone ? 3 : 2;
    }

    /** Old WorldUtils.getEntities used a spherical rather than cubic range query. */
    public static boolean thunderBoltAoeContains(double dx, double dy, double dz) {
        return dx * dx + dy * dy + dz * dz <= 8.0 * 8.0;
    }

    /** AbilityContext.attackRange linearly faded ThunderClap damage to zero at the edge. */
    public static float thunderClapDamageAtDistance(float baseDamage, double distance, double range) {
        if (!Float.isFinite(baseDamage) || !Double.isFinite(distance)
                || !Double.isFinite(range) || baseDamage <= 0 || range <= 0) return 0;
        double factor = 1.0 - Math.clamp(distance / range, 0.0, 1.0);
        return (float) (baseDamage * factor);
    }

    /** Client walk-speed curve used by ThunderClapContextC for its 60-tick charge. */
    public static float thunderClapWalkSpeed(float baseSpeed, float chargeProgress) {
        float progress = Math.clamp(chargeProgress, 0.0f, 1.0f);
        return Math.max(baseSpeed * 0.01f, baseSpeed * (1.0f - 0.99f * progress));
    }

    /** A charging tick is effective only when the target actually accepts energy. */
    public static boolean acceptedCharge(int acceptedAmount) {
        return acceptedAmount > 0;
    }

    /** Treat commit as authoritative and reject hostile negative/oversized capability returns. */
    public static int committedCharge(int simulated, int committed) {
        if (simulated <= 0 || committed <= 0) return 0;
        return Math.min(simulated, committed);
    }
}
