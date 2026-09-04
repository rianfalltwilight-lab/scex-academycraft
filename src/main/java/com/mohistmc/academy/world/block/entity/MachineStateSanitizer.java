package com.mohistmc.academy.world.block.entity;

/** Pure persistence boundary helpers shared by machine block entities and unit tests. */
public final class MachineStateSanitizer {
    private MachineStateSanitizer() {}

    public static int clampCounter(int value, int duration) {
        if (duration <= 0) return 0;
        return Math.clamp(value, 0, duration - 1);
    }

    public static int clampAmount(int value, int maximum) {
        return Math.clamp(value, 0, Math.max(0, maximum));
    }

    public static float clampFinite(float value, float maximum) {
        if (!Float.isFinite(value)) return 0;
        return Math.clamp(value, 0, Math.max(0, maximum));
    }
}
