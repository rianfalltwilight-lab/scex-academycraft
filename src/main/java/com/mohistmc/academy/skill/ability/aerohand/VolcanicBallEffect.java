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
 * 火山球 —— 发射被均匀压缩的空气球，击退首个命中目标。
 */
public class VolcanicBallEffect implements com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect {

    @Override
    public String getId() {
        return "volcanic_ball";
    }

    @Override public float rawCp(float proficiency) { return lerpf(40, 100, proficiency); }
    @Override public float rawOverload(float proficiency) { return lerpf(40, 30, proficiency); }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        float pointBlankDamage = lerpf(10.0f, 20.0f, exp);
        double range = lerpf(24.0f, 48.0f, exp);
        ServerLevel level = player.serverLevel();
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 intendedEnd = start.add(direction.scale(range));
        var trace = SkillRaycast.trace(player, start, intendedEnd);
        LivingEntity target = trace.firstEntity();
        Vec3 impact = trace.firstImpact();

        double travelled = start.distanceTo(impact);
        for (double distance = 0.5; distance <= travelled; distance += 0.5) {
            Vec3 point = start.add(direction.scale(distance));
            EffectHelper.windBurst(level, point.x, point.y, point.z, 1, 0.12);
        }
        EffectHelper.glowBurst(level, impact.x, impact.y, impact.z, 8, 0.18f,
                0xAAE8F8FF, 10, 0.45);

        level.playSound(null, impact.x, impact.y, impact.z,
                SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0f, 0.75f);

        if (target != null) {
            float damage = DynamicSkillRules.damage(getId(),
                    AeroBehaviorMath.volcanicDamage(pointBlankDamage, travelled, range));
            if (com.mohistmc.academy.skill.AcademyDamageHelper.hurt(player, target,
                    player.damageSources().playerAttack(player), damage)) {
                double knockback = lerpf(3.0f, 6.0f, exp) / Math.max(0.5, target.getBbHeight());
                target.setDeltaMovement(target.getDeltaMovement().add(
                        direction.x * knockback, 0.25 + Math.max(0, direction.y) * 0.35,
                        direction.z * knockback));
                target.setAirSupply(target.getMaxAirSupply());
                target.hurtMarked = true;
            }
        }

        if (!data.isDevMode()) {
            DynamicSkillRules.addExp(player,data,getId(),lerpf(0.002f, 0.001f, exp));
        }
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return Math.round(lerpf(20, 5, Math.clamp(proficiency, 0.0f, 1.0f)));
    }
}

