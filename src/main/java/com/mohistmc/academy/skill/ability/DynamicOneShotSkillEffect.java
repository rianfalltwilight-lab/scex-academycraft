package com.mohistmc.academy.skill.ability;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import net.minecraft.server.level.ServerPlayer;

/** Shared atomic resource settlement for proficiency-scaled ExtraAcC one-shot skills. */
public interface DynamicOneShotSkillEffect extends SkillEffect {
    float rawCp(float proficiency);
    float rawOverload(float proficiency);

    @Override
    default boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        float proficiency = data.getProficiency(getId());
        return DynamicSkillRules.canPay(data, getId(), rawCp(proficiency), rawOverload(proficiency));
    }

    @Override
    default boolean executeAndReport(ServerPlayer player, PlayerAbilityData data) {
        float proficiency = data.getProficiency(getId());
        if (!DynamicSkillRules.tryPay(data, getId(), rawCp(proficiency), rawOverload(proficiency))) {
            return false;
        }
        execute(player, data);
        return true;
    }

    @Override default boolean appliesBaseResourceCost() { return false; }
    @Override default boolean grantsActivationProficiency() { return false; }
}
