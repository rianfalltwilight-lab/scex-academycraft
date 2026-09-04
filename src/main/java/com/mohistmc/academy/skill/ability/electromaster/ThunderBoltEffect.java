package com.mohistmc.academy.skill.ability.electromaster;
import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.world.effect.EffectHelper;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.world.AcademySounds;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 雷击之枪 —— 对视线方向发射闪电，命中目标+AOE伤害 */
public class ThunderBoltEffect implements SkillEffect {

    private static final double RANGE = 20.0;
    private static final double AOE_RANGE = 8.0;

    @Override
    public String getId() {
        return "thunder_bolt";
    }

    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        return DynamicSkillRules.canPay(data, getId(), (int) lerpf(280, 420, exp), lerpf(50, 27, exp));
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        ServerLevel level = player.serverLevel();
        float proficiency = data.getProficiency(getId());

        int cp = (int) lerpf(280, 420, proficiency);
        float overload = lerpf(50, 27, proficiency);
        if (!DynamicSkillRules.tryPay(data,getId(),cp,overload)) return;

        float damage = lerpf(10, 25, proficiency);
        float aoeDamage = lerpf(6, 15, proficiency);

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(RANGE));

        EntityHitResult entityHit = rayTraceEntities(player, eyePos, endPos);
        BlockHitResult blockHit = (BlockHitResult) player.pick(RANGE, 0, false);

        Vec3 impactPoint;
        final Entity targetEntity;
        boolean hitEntity = false;

        if (entityHit != null) {
            double entityDist = eyePos.distanceTo(entityHit.getLocation());
            double blockDist = eyePos.distanceTo(blockHit.getLocation());
            if (entityDist < blockDist) {
                targetEntity = entityHit.getEntity();
                impactPoint = targetEntity.position().add(0,targetEntity.getEyeHeight(),0);
                hitEntity = true;
            } else {
                targetEntity = null;
                impactPoint = blockHit.getLocation();
            }
        } else {
            targetEntity = null;
            impactPoint = blockHit.getLocation();
        }

        if (hitEntity && targetEntity != null && targetEntity.isAlive()) {
            ElectromasterDamageHelper.attack(player,targetEntity,player.damageSources().playerAttack(player),
                    DynamicSkillRules.damage(getId(),damage));

            if (proficiency > 0.2 && level.random.nextDouble() < 0.8 && targetEntity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
            }

        }

        List<Entity> aoes = level.getEntities(player,
                new AABB(impactPoint.x - AOE_RANGE, impactPoint.y - AOE_RANGE, impactPoint.z - AOE_RANGE,
                        impactPoint.x + AOE_RANGE, impactPoint.y + AOE_RANGE, impactPoint.z + AOE_RANGE),
                e -> e instanceof LivingEntity && e.isAlive() && e != player
                        && (targetEntity == null || e != targetEntity)
                        && ElectromasterRules.thunderBoltAoeContains(
                                e.getX()-impactPoint.x,e.getY()-impactPoint.y,e.getZ()-impactPoint.z));

        for (Entity e : aoes) {
            ElectromasterDamageHelper.attack(player,e,player.damageSources().playerAttack(player),
                    DynamicSkillRules.damage(getId(),aoeDamage));

            // Preserve the actual 1.0.7 branch: it checked and slowed ad.target
            // inside the AOE loop, rather than applying the effect to e.
            if (proficiency > 0.2 && level.random.nextDouble() < 0.8
                    && targetEntity instanceof LivingEntity primary) {
                primary.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3));
            }
        }

        AcademySounds.playSound(level, player.getX(), player.getY(), player.getZ(),
                AcademySounds.EM_ARC_STRONG, SoundSource.PLAYERS, 0.6f, 1.0f);

        // The official client spawned three overlapping strong main arcs.
        Vec3 fullArcEnd = eyePos.add(lookVec.scale(RANGE));
        for(int i=0;i<3;i++) EffectHelper.electricTether(level, eyePos, fullArcEnd, 20);
        for (Entity e : aoes) {
            EffectHelper.electricTether(level, impactPoint,
                    e.position().add(0,e.getEyeHeight(),0),15+level.random.nextInt(11));
        }

        boolean effective = hitEntity || !aoes.isEmpty();
        DynamicSkillRules.addExp(player,data, getId(), effective ? 0.005f : 0.003f);
    }

    private EntityHitResult rayTraceEntities(ServerPlayer player, Vec3 start, Vec3 end) {
        AABB searchArea = new AABB(start,end).inflate(1.0);
        List<Entity> entities = player.level().getEntities(player, searchArea,
                e -> e != player && e.isAlive() && e.isPickable());

        Entity closest = null;
        double closestDist = Double.MAX_VALUE;
        Vec3 closestHit = null;

        for (Entity entity : entities) {
            AABB box = entity.getBoundingBox().inflate(0.3);
            var result = box.clip(start, end);
            if (result.isPresent()) {
                double dist = start.distanceTo(result.get());
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = entity;
                    closestHit = result.get();
                }
            }
        }

        if (closest != null) {
            return new EntityHitResult(closest, closestHit);
        }
        return null;
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return (int) lerpf(120, 50, proficiency);
    }

    @Override public boolean cooldownUsesPreActivationProficiency() { return true; }
}

