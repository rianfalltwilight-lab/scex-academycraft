package com.mohistmc.academy.skill.ability.telekinesis;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.world.effect.EffectHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Converts a large amount of overload headroom into immediately restored CP. */
public final class OverloadThinkingEffect implements SkillEffect {
    @Override public String getId() { return "overload_thinking"; }
    @Override public boolean appliesBaseResourceCost() { return false; }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        float proficiency = data.getProficiency(getId());
        return data.getCurrentCp() < data.getMaxCp()
                && DynamicSkillRules.canPay(data, getId(), 0,
                TelekinesisRules.overloadThinkingCost(proficiency));
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        float proficiency = data.getProficiency(getId());
        float overload = TelekinesisRules.overloadThinkingCost(proficiency);
        if (!DynamicSkillRules.tryPay(data, getId(), 0, overload)) return;

        data.restoreCp(TelekinesisRules.overloadThinkingRestore(proficiency));
        var level = player.serverLevel();
        EffectHelper.psychoBurst(level, player.getX(), player.getY() + player.getBbHeight() * 0.65,
                player.getZ(), 24, 0.5);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 1.5f);
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, getId(),
                0.01F - 0.005F * Math.clamp(proficiency, 0, 1));
    }

    @Override public boolean grantsActivationProficiency() { return false; }

    @Override public int getCooldownTicks(float proficiency) {
        return Math.round(300 - 240 * Math.clamp(proficiency, 0, 1));
    }
}
