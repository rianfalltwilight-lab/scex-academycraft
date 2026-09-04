package com.mohistmc.academy.skill.ability.meltdowner;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.skill.passive.PassiveDamageHelper;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import com.mohistmc.academy.world.entity.EntitySilbarn;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 1.0.7 key-down barrage, including the special Silbarn fan branch. */
public final class RayBarrageEffect implements SkillEffect {
    private static final double RANGE = 20.0D;
    private static final float HALF_YAW = 27.5F;
    private static final float HALF_PITCH = 55.0F;

    private record Lock(EntitySilbarn silbarn, Vec3 point) {}
    private record LookingHit(Entity entity, Vec3 point) {}

    @Override public String getId() { return "ray_barrage"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }

    private float cp(PlayerAbilityData data) { return lerpf(450, 380, data.getProficiency(getId())); }
    private float overload(PlayerAbilityData data) { return lerpf(300, 140, data.getProficiency(getId())); }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.canPay(data, getId(), cp(data), overload(data));
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        Lock lock = acquire(player);
        if (!DynamicSkillRules.tryPay(data, getId(), cp(data), overload(data))) return;
        perform(player, data, lock);
    }

    private static Lock acquire(ServerPlayer player) {
        LookingHit hit = lookingHit(player);
        EntitySilbarn special = hit.entity instanceof EntitySilbarn candidate && !candidate.isHit()
                ? candidate : null;
        return new Lock(special, special == null ? hit.point : special.position());
    }

    private static void perform(ServerPlayer player, PlayerAbilityData data, Lock lock) {
        // RBContext kept the lock it acquired in MSG_START; MSG_EXECUTE did
        // not silently demote it if another callback changed Silbarn state.
        EntitySilbarn silbarn = lock.silbarn;
        Vec3 point = silbarn == null ? lock.point : silbarn.position();
        float exp = data.getProficiency("ray_barrage");

        if (silbarn != null) {
            silbarn.breakByRayBarrage();
            float damage = lerpf(10, 18, exp);
            for (Entity target : player.serverLevel().getEntities(player, legacyFanBounds(player),
                    target -> target != player && target != silbarn)) {
                if (insideLegacyFan(player, target)) {
                    PassiveDamageHelper.meltdownerAttack(player, data, target, "ray_barrage", damage);
                }
            }
            EffectHelper.barrageFan(player.serverLevel(), point, player.getYRot(), player.getXRot());
            player.serverLevel().playSound(null, point.x, point.y, point.z,
                    AcademySounds.MD_RAY_SMALL, SoundSource.PLAYERS, 0.5F, 1.0F);
        } else {
            LookingHit plain = lookingHit(player);
            point = plain.point;
            if (plain.entity != null) {
                PassiveDamageHelper.meltdownerAttack(player, data, plain.entity,
                        "ray_barrage", lerpf(25, 60, exp));
            }
        }

        EffectHelper.barragePreRay(player.serverLevel(), player.position().add(0, 1.6, 0), point,
                silbarn == null ? 30 : 50);
        player.serverLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                AcademySounds.MD_RAY_SMALL, SoundSource.PLAYERS, 0.8F, 1.0F);
        DynamicSkillRules.addExp(player, data, "ray_barrage", 0.005F);
    }

    static boolean insideLegacyFan(ServerPlayer player, Entity target) {
        Vec3 delta = target.getEyePosition().subtract(player.getEyePosition());
        if (delta.lengthSqr() < 1.0E-8D) return false;
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        return Math.abs(Mth.wrapDegrees(targetYaw - player.getYRot())) <= HALF_YAW
                && Math.abs(targetPitch - player.getXRot()) <= HALF_PITCH;
    }

    private static Vec3 blockEnd(ServerPlayer player, Vec3 from, Vec3 to) {
        var hit = player.serverLevel().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
    }

    private static LookingHit lookingHit(ServerPlayer player) {
        Vec3 from = player.getEyePosition();
        Vec3 intended = from.add(player.getLookAngle().scale(RANGE));
        Vec3 block = blockEnd(player, from, intended);
        double bestDistance = from.distanceToSqr(block);
        Entity best = null;
        for (Entity candidate : player.serverLevel().getEntities(player,
                new AABB(from, intended).inflate(1.0D),
                entity -> entity != player && entity.isAlive() && entity.isPickable())) {
            var intercept = candidate.getBoundingBox().inflate(0.3D).clip(from, intended);
            if (intercept.isPresent() && from.distanceToSqr(intercept.get()) < bestDistance) {
                bestDistance = from.distanceToSqr(intercept.get());
                best = candidate;
            }
        }
        return best == null ? new LookingHit(null, block)
                : new LookingHit(best, best.position().add(0, best.getEyeHeight() * .6, 0));
    }

    private static AABB legacyFanBounds(ServerPlayer player) {
        Vec3 origin = player.position();
        float minYaw = player.getYRot() - HALF_YAW, maxYaw = player.getYRot() + HALF_YAW;
        float minPitch = player.getXRot() - HALF_PITCH, maxPitch = player.getXRot() + HALF_PITCH;
        Vec3[] points = {origin,
                origin.add(Vec3.directionFromRotation(minPitch, minYaw).scale(RANGE)),
                origin.add(Vec3.directionFromRotation(maxPitch, minYaw).scale(RANGE)),
                origin.add(Vec3.directionFromRotation(maxPitch, maxYaw).scale(RANGE)),
                origin.add(Vec3.directionFromRotation(minPitch, maxYaw).scale(RANGE))};
        double minX=origin.x,minY=origin.y,minZ=origin.z,maxX=origin.x,maxY=origin.y,maxZ=origin.z;
        for(Vec3 point:points){minX=Math.min(minX,point.x);minY=Math.min(minY,point.y);minZ=Math.min(minZ,point.z);maxX=Math.max(maxX,point.x);maxY=Math.max(maxY,point.y);maxZ=Math.max(maxZ,point.z);}
        return new AABB(minX,minY,minZ,maxX,maxY,maxZ);
    }

    @Override public int getCooldownTicks(float exp) { return (int) lerpf(100, 40, exp); }
}
