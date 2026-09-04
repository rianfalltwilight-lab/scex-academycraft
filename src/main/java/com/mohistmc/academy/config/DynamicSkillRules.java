package com.mohistmc.academy.config;

import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.AbilityMutationService;
import net.minecraft.server.level.ServerPlayer;

/** Single settlement boundary for effects which intentionally bypass registry base costs. */
public final class DynamicSkillRules {
 private DynamicSkillRules(){}
 public static LegacyAbilityRules.SkillTuning tuning(String id){return ACConfig.Server.skill(id);}
 public static boolean enabled(String id){return tuning(id).enabled();}
 public static float cp(String id,float raw){return scale(raw,tuning(id).cp());}
 public static float overload(String id,float raw){return scale(raw,tuning(id).overload());}
 public static float damage(String id,float raw){return scale(scale(raw,tuning(id).damage()),(float)ACConfig.Server.damageMul());}
 public static float exp(String id,float raw){return scale(scale(raw,tuning(id).exp()),ACConfig.Server.legacyRules().proficiencyGrowth());}
 public static boolean destroysBlocks(String id){return tuning(id).destroyBlocks();}
 public static boolean destroysBlocks(net.minecraft.world.level.Level level,String id){
  return tuning(id).destroyBlocks()&&ACConfig.Server.mayDestroyBlocks(level);
 }
 public static boolean canPay(PlayerAbilityData data,String id,float rawCp,float rawOverload){
  if(!enabled(id))return false;if(data.isDevMode())return true;float cp=cp(id,rawCp),ol=overload(id,rawOverload);
  return cp>=0&&ol>=0&&data.getCurrentCp()>=cp&&data.getCurrentOverload()+ol<=data.getMaxOverload();
 }
 public static void addExp(ServerPlayer player,PlayerAbilityData data,String id,float raw){
  AbilityMutationService.addSkillExp(player,data,id,exp(id,raw));
 }
 public static boolean tryPay(PlayerAbilityData data,String id,float rawCp,float rawOverload){
  if(!canPay(data,id,rawCp,rawOverload))return false;if(data.isDevMode())return true;float cp=cp(id,rawCp),ol=overload(id,rawOverload);
  return data.tryConsumeDynamic(cp,ol);
 }
 public static boolean payForced(PlayerAbilityData data,String id,float rawCp,float rawOverload){
  if(!enabled(id)||!Float.isFinite(rawCp)||!Float.isFinite(rawOverload)||rawCp<0||rawOverload<0)return false;
  if(data.isDevMode())return true;
  return data.consumeDynamicForced(cp(id,rawCp),overload(id,rawOverload));
 }
 static float scale(float raw,float multiplier){if(!Float.isFinite(raw)||raw<0)return 0;float v=raw*multiplier;return Float.isFinite(v)?Math.max(0,v):0;}
}
