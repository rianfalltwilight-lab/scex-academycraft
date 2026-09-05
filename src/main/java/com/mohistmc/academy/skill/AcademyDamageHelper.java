package com.mohistmc.academy.skill;

import com.mohistmc.academy.config.ACConfig;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Final boundary for damage caused by AcademyCraft abilities only. */
public final class AcademyDamageHelper {
    private static final class Call {
        final ServerPlayer attacker;
        final Entity target;
        final DamageSource source;
        final boolean ability;
        AbilityDamageFrames.Frame frame;
        Call(ServerPlayer attacker, Entity target, DamageSource source, boolean ability) {
            this.attacker = attacker; this.target = target; this.source = source; this.ability = ability;
        }
    }
    private static final ThreadLocal<Deque<Call>> CALLS = ThreadLocal.withInitial(ArrayDeque::new);
    private AcademyDamageHelper() {}

    public static boolean hurt(ServerPlayer attacker, Entity target, DamageSource source, float amount) {
        return allowsTarget(target) && valid(target, source, amount)
                && apply(attacker, target, source, amount, true);
    }

    /** Academy-owned item damage keeps the PvP policy without inheriting a surrounding ability call. */
    public static boolean hurtNonAbility(Entity target, DamageSource source, float amount) {
        return allowsTarget(target) && valid(target, source, amount) && apply(null, target, source, amount, false);
    }

    /** Explicit caster-only ability damage bypasses PvP and hostile Insulation. */
    public static boolean hurtSelf(ServerPlayer attacker, Entity target, DamageSource source, float amount) {
        return attacker != null && target == attacker && valid(target, source, amount)
                && apply(null, target, source, amount, true);
    }

    public static boolean isAbilityDamageInProgress() {
        Call call = CALLS.get().peek();
        return call != null && call.ability;
    }

    /** A nested ordinary damage callback must never inherit its caller's ability armour rules. */
    public static boolean isAbilityDamageInProgress(Entity target, DamageSource source) {
        Call call = CALLS.get().peek();
        return call != null && call.ability && call.frame != null && call.frame == AbilityDamageFrames.peek()
                && call.target == target && call.source == source;
    }
    static ServerPlayer hostileAbilityAttacker(Entity target, DamageSource source) {
        Call call = CALLS.get().peek();
        return call != null && call.ability && call.frame != null && call.frame == AbilityDamageFrames.peek()
                && call.target == target && call.source == source
                && call.attacker != target ? call.attacker : null;
    }

    static void bindDamageFrame(AbilityDamageFrames.Frame frame, DamageSource source) {
        Call call = CALLS.get().peek();
        if (call != null && call.frame == null && call.target == frame.player && call.source == source)
            call.frame = frame;
    }
    private static boolean valid(Entity target, DamageSource source, float amount) {
        return target != null && source != null && amount > 0 && Float.isFinite(amount);
    }

    private static boolean apply(ServerPlayer attacker, Entity target, DamageSource source,
                                 float amount, boolean ability) {
        var calls = CALLS.get();
        calls.push(new Call(attacker, target, source, ability));
        try {
            // Defensive payment is deferred until the accepted-hit mixin, after all rejection gates.
            return target.hurt(source, amount);
        } finally {
            calls.pop();
            if (calls.isEmpty()) CALLS.remove();
        }
    }

    public static boolean allowsTarget(Entity target) {
        return allowsTarget(target, ACConfig.Server.pvpEnabled());
    }

    static boolean allowsTarget(Entity target, boolean pvpEnabled) {
        return pvpEnabled || !(target instanceof Player);
    }
}
