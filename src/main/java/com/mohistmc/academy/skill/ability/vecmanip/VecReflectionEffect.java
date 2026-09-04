package com.mohistmc.academy.skill.ability.vecmanip;
import com.mohistmc.academy.config.DynamicSkillRules; import com.mohistmc.academy.skill.ChargingSkillEffect; import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.passive.VecDefenseRuntime; import net.minecraft.server.level.ServerPlayer;
/** Held-key reflection context; all authority is server-side. */
public final class VecReflectionEffect implements ChargingSkillEffect {
 public String getId(){return "vec_reflection";} public boolean appliesBaseResourceCost(){return false;} public boolean grantsActivationProficiency(){return false;} public int getMinChargeTicks(){return 0;} public int getMaxChargeTicks(){return 1;} public int getSessionTimeoutTicks(PlayerAbilityData d){return Integer.MAX_VALUE;} public TickResult getSessionTimeoutResult(ServerPlayer p,PlayerAbilityData d,int t){return TickResult.ABORT_RESOURCE;}
 private float startOverload(PlayerAbilityData d){return 350-100*d.getProficiency(getId());} public boolean canStartCharging(ServerPlayer p,PlayerAbilityData d){return DynamicSkillRules.canPay(d,getId(),0,startOverload(d));}
 public void onChargingStart(ServerPlayer p,PlayerAbilityData d){if(!DynamicSkillRules.tryPay(d,getId(),0,startOverload(d)))return;VecDefenseRuntime.start(p.getUUID(),VecDefenseRuntime.Mode.REFLECTION,d.getCurrentOverload());}
 public boolean onChargingTick(ServerPlayer p,PlayerAbilityData d,int t){return VecDefenseRuntime.active(p.getUUID(),VecDefenseRuntime.Mode.REFLECTION);}
 public TickResult getTickResult(ServerPlayer p,PlayerAbilityData d,int t){return onChargingTick(p,d,t)?TickResult.CONTINUE:TickResult.ABORT_RESOURCE;}
 public void onChargingRelease(ServerPlayer p,PlayerAbilityData d,int t){VecDefenseRuntime.stop(p.getUUID());} public boolean tryRelease(ServerPlayer p,PlayerAbilityData d,int t){onChargingRelease(p,d,t);return true;}
 public void onChargingAbort(ServerPlayer p,PlayerAbilityData d){VecDefenseRuntime.stop(p.getUUID());} public void execute(ServerPlayer p,PlayerAbilityData d){} public int getCooldownTicks(float p){return 0;}
}
