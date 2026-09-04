package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.world.effect.EffectHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * 空气冷却 —— 用强制对流为施术者散热并降低过载。
 *
 * <p>The Mohist placeholder healed nearby entities and added fire resistance,
 * which was the opposite of the bundled skill description.  Overload is an
 * authoritative player attachment, so the mutation is performed only from
 * this server-side effect.</p>
 */
public class AirCoolingEffect implements com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect {

    @Override
    public String getId() {
        return "air_cooling";
    }

    @Override public float rawCp(float proficiency) { return 400 + 1200 * Math.clamp(proficiency, 0, 1); }
    @Override public float rawOverload(float proficiency) { return 0; }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        return (data.isDevMode() || data.getCurrentOverload() > 0.0f)
                && com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect.super.canActivate(player, data);
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        ServerLevel level = player.serverLevel();

        data.setCurrentOverload(AeroBehaviorMath.cooledOverload(data.getCurrentOverload(), exp));
        EffectHelper.windBurst(level, player.getX(), player.getY() + player.getBbHeight() / 2,
                player.getZ(), 36, 1.4 + exp);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0f, 1.5f);

        if (!data.isDevMode()) {
            DynamicSkillRules.addExp(player,data,getId(),0.002f - 0.001f * exp);
        }
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return Math.round(300 - 240 * Math.clamp(proficiency, 0.0f, 1.0f));
    }
}

