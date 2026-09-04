package com.mohistmc.academy.skill;

import com.mohistmc.academy.config.ACConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Final boundary for damage caused by AcademyCraft abilities only. */
public final class AcademyDamageHelper {
    private static final ThreadLocal<Integer> ABILITY_DAMAGE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private AcademyDamageHelper() {}

    public static boolean hurt(ServerPlayer attacker, Entity target, DamageSource source, float amount) {
        if (!allowsTarget(target)) return false;
        float finalAmount = com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler
                .mitigateAbilityDamage(attacker, target, amount);
        return finalAmount > 0 && Float.isFinite(finalAmount) && apply(target, source, finalAmount);
    }

    /** Academy-owned item damage: keeps the PvP policy but does not masquerade as an ability. */
    public static boolean hurtNonAbility(Entity target, DamageSource source, float amount) {
        return allowsTarget(target) && source != null && amount > 0 && Float.isFinite(amount)
                && target.hurt(source, amount);
    }

    /**
     * Explicit self-damage boundary for skills whose documented cost includes
     * their caster. It intentionally bypasses the PvP option and hostile
     * Telekinesis mitigation, but can never be redirected to another entity.
     */
    public static boolean hurtSelf(ServerPlayer attacker, Entity target,
                                   DamageSource source, float amount) {
        return attacker != null && target == attacker && source != null
                && amount > 0 && Float.isFinite(amount) && apply(target, source, amount);
    }

    /** True only during the synchronous damage event raised by an Academy ability. */
    public static boolean isAbilityDamageInProgress() { return ABILITY_DAMAGE_DEPTH.get() > 0; }

    private static boolean apply(Entity target, DamageSource source, float amount) {
        ABILITY_DAMAGE_DEPTH.set(ABILITY_DAMAGE_DEPTH.get() + 1);
        try {
            return target.hurt(source, amount);
        } finally {
            int next = ABILITY_DAMAGE_DEPTH.get() - 1;
            if (next <= 0) ABILITY_DAMAGE_DEPTH.remove();
            else ABILITY_DAMAGE_DEPTH.set(next);
        }
    }

    /** Exposed so integrations can use exactly the same live target policy before expensive effects. */
    public static boolean allowsTarget(Entity target) {
        return allowsTarget(target, ACConfig.Server.pvpEnabled());
    }

    static boolean allowsTarget(Entity target, boolean pvpEnabled) {
        return pvpEnabled || !(target instanceof Player);
    }
}
