package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.world.effect.EffectHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/**
 * 火山球 —— 发射被均匀压缩的空气球，击退首个命中目标。
 */
public class VolcanicBallEffect implements SkillEffect {

    @Override
    public String getId() {
        return "volcanic_ball";
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        float pointBlankDamage = lerpf(8.0f, 15.0f, exp);
        double range = lerpf(10.0f, 18.0f, exp);
        ServerLevel level = player.serverLevel();
        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 intendedEnd = start.add(direction.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(start, intendedEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = blockHit.getType() == HitResult.Type.MISS ? intendedEnd : blockHit.getLocation();

        LivingEntity target = null;
        Vec3 impact = end;
        double nearest = start.distanceToSqr(end);
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(start, end).inflate(1.25), candidate -> candidate != player
                        && candidate.isAlive() && candidate.isPickable()
                        && !player.isAlliedTo(candidate)
                        && com.mohistmc.academy.skill.AcademyDamageHelper.allowsTarget(candidate))) {
            var clipped = candidate.getBoundingBox().inflate(0.35).clip(start, end);
            if (clipped.isEmpty()) continue;
            double distance = start.distanceToSqr(clipped.get());
            if (distance < nearest) {
                nearest = distance;
                target = candidate;
                impact = clipped.get();
            }
        }

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
                double knockback = lerpf(1.0f, 2.0f, exp);
                target.setDeltaMovement(target.getDeltaMovement().add(
                        direction.x * knockback, 0.25 + Math.max(0, direction.y) * 0.35,
                        direction.z * knockback));
                target.hurtMarked = true;
            }
        }

        if (!data.isDevMode()) {
            DynamicSkillRules.addExp(player,data,getId(),0.005f);
        }
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return Math.round(50 - 20 * Math.clamp(proficiency, 0.0f, 1.0f));
    }
}

