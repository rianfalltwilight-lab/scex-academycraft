package com.mohistmc.academy.skill;

import net.minecraft.server.level.ServerPlayer;

public interface ChargingSkillEffect extends SkillEffect {

    enum TickResult { CONTINUE, RELEASE, ABORT_RESOURCE }

    /** Server-side transactional preflight before a charging state is created. */
    default boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) { return true; }

    void onChargingStart(ServerPlayer player, PlayerAbilityData data);

    boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks);

    void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks);

    default boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return ticks >= getMinChargeTicks(data) && ticks <= getMaxChargeTicks(data);
    }

    boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks);

    /**
     * Optional client-selected release value. The server-side effect must clamp
     * and validate it; the default keeps ordinary held skills parameter-free.
     */
    default boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks, float releaseValue) {
        return tryRelease(player, data, ticks);
    }

    /** Most held skills fire on key-up; a few legacy contexts (ThunderClap) cancel instead. */
    default boolean releasesOnKeyUp() { return true; }

    /** Explicit: implementations must distinguish normal release from resource abort. */
    TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks);

    void onChargingAbort(ServerPlayer player, PlayerAbilityData data);

    int getMinChargeTicks();

    default int getMinChargeTicks(PlayerAbilityData data) {
        return getMinChargeTicks();
    }

    int getMaxChargeTicks();

    /** Runtime-duration-aware cooldown; fixed-duration skills keep the legacy hook. */
    default int getCooldownTicks(float proficiency, int chargedTicks) {
        return getCooldownTicks(proficiency);
    }

    /** Some legacy contexts apply cooldown only after a context-specific success (for example, a melee hit). */
    default boolean shouldApplyCooldownAfterRelease(ServerPlayer player, PlayerAbilityData data, int chargedTicks) {
        return true;
    }

    default int getMaxChargeTicks(PlayerAbilityData data) {
        return getMaxChargeTicks();
    }

    /**
     * Hard server-side lifetime of the held context. In final 1.12.2 the charge meter
     * often reached its cap long before key-up; that cap must not itself fire
     * the skill. Implementations with an over-hold penalty can use a longer
     * timeout while keeping {@link #getMaxChargeTicks(PlayerAbilityData)} as
     * the client meter's effective charge cap.
     */
    default int getSessionTimeoutTicks(PlayerAbilityData data) {
        return getMaxChargeTicks(data);
    }

    /** Result used when the hard lifetime is reached while normal ticking still continues. */
    default TickResult getSessionTimeoutResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return TickResult.RELEASE;
    }
}
