package com.mohistmc.academy.skill.ability.meltdowner;

/** Atomic server-thread resource settlement shared by shield contact and absorption. */
public final class LightShieldResourceLedger {
    private LightShieldResourceLedger() {}

    /** Pure Java resource seam; the attachment implements it, while unit tests need no game runtime. */
    public interface ResourceAccount {
        boolean isDevMode();
        float getCurrentCp();
        void setCurrentCp(float cp);
        float getCurrentOverload();
        void setCurrentOverload(float overload);
        void addOverload(float overload);
        float getMaxCp();
        float getMaxOverload();
    }

    public static boolean tryDebit(ResourceAccount data, float cp, float overload) {
        if (data.isDevMode()) return true;
        if (data.getCurrentCp() < cp || data.getCurrentOverload() + overload > data.getMaxOverload()) return false;
        data.setCurrentCp(data.getCurrentCp() - cp);
        data.addOverload(overload);
        return true;
    }

    public static void refund(ResourceAccount data, float cp, float overload) {
        if (data.isDevMode()) return;
        data.setCurrentCp(Math.min(data.getMaxCp(), data.getCurrentCp() + cp));
        data.setCurrentOverload(Math.max(0, data.getCurrentOverload() - overload));
    }
}
