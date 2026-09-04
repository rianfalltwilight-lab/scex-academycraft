package com.mohistmc.academy.skill.ability.vecmanip;

import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.passive.VecDefenseRuntime;
import net.minecraft.server.level.ServerPlayer;
import com.mohistmc.academy.config.DynamicSkillRules;

/** Key-controlled server-side lifetime for vector deviation. */
public final class VecDeviationEffect implements ChargingSkillEffect {
    @Override
    public String getId() { return "vec_deviation"; }

    @Override public boolean appliesBaseResourceCost(){return false;}
    @Override public boolean grantsActivationProficiency(){return false;}
    @Override public int getMinChargeTicks(){return 0;}
    @Override public int getMaxChargeTicks(){return 1;}
    @Override public int getSessionTimeoutTicks(PlayerAbilityData d){return Integer.MAX_VALUE;}
    @Override public TickResult getSessionTimeoutResult(ServerPlayer p,PlayerAbilityData d,int t){return TickResult.ABORT_RESOURCE;}
    private float startOverload(PlayerAbilityData d){return 80-30*d.getProficiency(getId());}
    @Override public boolean canStartCharging(ServerPlayer p,PlayerAbilityData d){return DynamicSkillRules.canPay(d,getId(),0,startOverload(d));}
    @Override public void onChargingStart(ServerPlayer p,PlayerAbilityData d){if(!DynamicSkillRules.tryPay(d,getId(),0,startOverload(d)))return;VecDefenseRuntime.start(p.getUUID(),VecDefenseRuntime.Mode.DEVIATION,d.getCurrentOverload());}
    @Override public boolean onChargingTick(ServerPlayer p,PlayerAbilityData d,int t){return VecDefenseRuntime.active(p.getUUID(),VecDefenseRuntime.Mode.DEVIATION);}
    @Override public TickResult getTickResult(ServerPlayer p,PlayerAbilityData d,int t){return onChargingTick(p,d,t)?TickResult.CONTINUE:TickResult.ABORT_RESOURCE;}
    @Override public void onChargingRelease(ServerPlayer p,PlayerAbilityData d,int t){VecDefenseRuntime.stop(p.getUUID());}
    @Override public boolean tryRelease(ServerPlayer p,PlayerAbilityData d,int t){onChargingRelease(p,d,t);return true;}
    @Override public void onChargingAbort(ServerPlayer p,PlayerAbilityData d){VecDefenseRuntime.stop(p.getUUID());}
    @Override public void execute(ServerPlayer p,PlayerAbilityData d){} @Override public int getCooldownTicks(float p){return 0;}
}
