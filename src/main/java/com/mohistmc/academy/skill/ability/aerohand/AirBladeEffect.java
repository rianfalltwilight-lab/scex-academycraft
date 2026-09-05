package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.ability.SkillRaycast;

import com.mohistmc.academy.world.effect.EffectHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/**
 * 空气刃 —— 向前方发射空气刀刃
 */
public class AirBladeEffect implements com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect {

    @Override
    public String getId() {
        return "air_blade";
    }

    @Override public float rawCp(float proficiency) { return lerpf(100, 150, proficiency); }
    @Override public float rawOverload(float proficiency) { return lerpf(60, 40, proficiency); }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        float damage = lerpf(12.0f, 18.0f, exp);
        double range = lerpf(32.0f, 48.0f, exp);
        ServerLevel level = player.serverLevel();
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle().normalize();
        Vec3 intended = eyePos.add(lookVec.scale(range));
        var trace = SkillRaycast.trace(player, eyePos, intended);
        LivingEntity target = trace.firstEntity();
        Vec3 impact = trace.firstImpact();
        double travelled = eyePos.distanceTo(impact);
        for (double d = 0.5; d <= travelled; d += 0.5) {
            Vec3 point = eyePos.add(lookVec.scale(d));
            EffectHelper.glowBurst(level, point.x, point.y, point.z, 1, 0.15f,
                    0xAAFFFFFF, 10, 0.8);
        }
        if (target != null) {
            float retained = lerpf(1.0F, 0.75F, (float) Math.clamp(travelled / range, 0, 1));
            com.mohistmc.academy.skill.AcademyDamageHelper.hurt(player, target,
                    player.damageSources().magic(), DynamicSkillRules.damage(getId(), damage * retained));
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.5f);

        if (!data.isDevMode()) {
            DynamicSkillRules.addExp(player,data,getId(),lerpf(0.002f, 0.004f, exp));
        }
    }

    @Override public int getCooldownTicks(float proficiency) {
        return Math.round(lerpf(40, 30, Math.clamp(proficiency, 0.0f, 1.0f)));
    }
}

