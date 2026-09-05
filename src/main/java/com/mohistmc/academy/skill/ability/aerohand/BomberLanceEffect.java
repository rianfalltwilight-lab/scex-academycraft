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
 * 轰炸长矛 —— 向前方发射高速风压长矛
 */
public class BomberLanceEffect implements com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect {

    @Override
    public String getId() {
        return "bomber_lance";
    }

    @Override public float rawCp(float proficiency) { return lerpf(600, 900, proficiency); }
    @Override public float rawOverload(float proficiency) { return lerpf(240, 160, proficiency); }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        float damage = lerpf(48.0f, 72.0f, exp);
        double range = lerpf(20.0f, 30.0f, exp);
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
            EffectHelper.shockwaveRing(level, point.x, point.y, point.z, 3, 0.45F);
        }
        if (target != null && com.mohistmc.academy.skill.AcademyDamageHelper.hurt(player, target,
                player.damageSources().playerAttack(player), DynamicSkillRules.damage(getId(), damage))) {
            double velocity = lerpf(10F, 15F, exp) / Math.max(0.5F, target.getBbHeight());
            target.push(lookVec.x * velocity, lookVec.y * velocity, lookVec.z * velocity);
            target.setAirSupply(target.getMaxAirSupply());
            target.hurtMarked = true;
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0f, 1.0f);

        if (!data.isDevMode()) {
            DynamicSkillRules.addExp(player,data,getId(),lerpf(0.005f, 0.01f, exp));
        }
    }

    @Override public int getCooldownTicks(float proficiency) {
        return Math.round(lerpf(80, 60, Math.clamp(proficiency, 0.0f, 1.0f)));
    }
}

