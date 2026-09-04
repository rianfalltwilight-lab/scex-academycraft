package com.mohistmc.academy.skill.ability.teleporter;
import com.mohistmc.academy.skill.*;import net.minecraft.server.level.ServerPlayer;
import com.mohistmc.academy.config.DynamicSkillRules;
/** Activation is intentionally routed through the bounded location CRUD/perform payload. */
public final class LocationTeleportEffect implements SkillEffect{
 public String getId(){return"location_teleport";} public void execute(ServerPlayer p,PlayerAbilityData d){DynamicSkillRules.enabled(getId());}
 public boolean appliesBaseResourceCost(){return false;} public boolean grantsActivationProficiency(){return false;} public int getCooldownTicks(float e){return 0;}
}
