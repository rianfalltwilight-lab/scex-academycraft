package com.mohistmc.academy.skill.ability.meltdowner;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Final 1.12.2 mark-and-release Jet Engine with a server-authoritative travel phase. */
public final class JetEngineEffect implements ChargingSkillEffect {
    private static final double RANGE = 12;

    @Override public String getId() { return "jet_engine"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }
    @Override public int getMinChargeTicks() { return 1; }
    @Override public int getMaxChargeTicks() { return 1; }
    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) { return Integer.MAX_VALUE; }
    @Override public TickResult getSessionTimeoutResult(
            ServerPlayer player, PlayerAbilityData data, int ticks) {
        return TickResult.ABORT_RESOURCE;
    }
    @Override public boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return ticks >= 1;
    }

    private float holdCpRequirement(PlayerAbilityData data) {
        return lerpf(170, 140, data.getProficiency(getId()));
    }

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.enabled(getId()) && (data.isDevMode()
                || data.getCurrentCp() >= DynamicSkillRules.cp(getId(), holdCpRequirement(data)));
    }

    @Override public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {}

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return data.isDevMode()
                || data.getCurrentCp() >= DynamicSkillRules.cp(getId(), holdCpRequirement(data));
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return onChargingTick(player, data, ticks) ? TickResult.CONTINUE : TickResult.ABORT_RESOURCE;
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float exp = data.getProficiency(getId());
        // Context.consume in final 1.12.2 took overload first and CP second.
        // Jet Engine's historically swapped variable names therefore debit
        // 60..50 CP and 170..140 overload on a successful release.
        float cpDebit = lerpf(60, 50, exp);
        float overloadDebit = lerpf(170, 140, exp);
        if (!DynamicSkillRules.enabled(getId())
                || !DynamicSkillRules.tryPay(data, getId(), cpDebit, overloadDebit)) return false;

        Vec3 destination = legacyDestination(player);
        DynamicSkillRules.addExp(player, data, getId(), .004F);
        JetEngineRuntime.start(player, destination, lerpf(7, 20, exp));
        return true;
    }

    /**
     * Commit 769f45c1 removed the old +1.65 Y adjustment.  The final target is
     * exactly Raytrace.getLookingPos(player, 12, EntitySelectors.nothing).
     */
    static Vec3 legacyDestination(ServerPlayer player) {
        Vec3 from = player.getEyePosition();
        Vec3 intended = from.add(player.getLookAngle().normalize().scale(RANGE));
        BlockHitResult hit = player.serverLevel().clip(new ClipContext(from, intended,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : intended;
    }

    @Override public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        tryRelease(player, data, ticks);
    }
    @Override public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {}
    @Override public void execute(ServerPlayer player, PlayerAbilityData data) {}
    @Override public int getCooldownTicks(float proficiency) {
        return (int) lerpf(60, 30, proficiency);
    }
}
