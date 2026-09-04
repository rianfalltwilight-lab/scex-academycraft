package com.mohistmc.academy.skill.ability.vecmanip;
import com.mohistmc.academy.skill.AcademyDamageHelper;

import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.world.effect.EffectHelper;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademySounds;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import com.mohistmc.academy.entity.BloodSprayDecalEntity;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 血液回流 —— 操纵目标血液，造成高额伤害 */
public class BloodRetroEffect implements ChargingSkillEffect {
    private static final ResourceLocation SLOW_ID = ResourceLocation.fromNamespaceAndPath("academy", "blood_retro_slow");

    private static final int AUTO_RELEASE_TICKS = 30;
    private static final double RANGE = 2.0;

    @Override
    public String getId() {
        return "blood_retro";
    }
    @Override public boolean appliesBaseResourceCost(){return false;}
    @Override public boolean grantsActivationProficiency(){return false;}

    @Override
    public int getMinChargeTicks() {
        return 0;
    }

    @Override
    public int getMaxChargeTicks() {
        return AUTO_RELEASE_TICKS;
    }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        applyLegacySlow(player, 0);
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        applyLegacySlow(player, ticks);
        return ticks < AUTO_RELEASE_TICKS;
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return onChargingTick(player, data, ticks) ? TickResult.CONTINUE : TickResult.RELEASE;
    }

    @Override
    public boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float cp = lerpf(280, 350, data.getProficiency(getId()));
        float overload = lerpf(55, 40, data.getProficiency(getId()));
        return ChargingSkillEffect.super.canRelease(player, data, ticks)
                && rayTraceEntity(player, RANGE) != null
                && DynamicSkillRules.canPay(data, getId(), cp, overload);
    }

    @Override
    public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        cleanupSlow(player);
        float exp = data.getProficiency(getId());
        float damage = lerpf(30, 60, exp);
        float cp = lerpf(280, 350, exp);
        float overload = lerpf(55, 40, exp);

        ServerLevel level = player.serverLevel();
        EntityHitResult hit = rayTraceEntity(player, RANGE);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity || hit.getEntity() instanceof EnderDragonPart)) return;
        if (!DynamicSkillRules.tryPay(data,getId(),cp,overload)) return;

        Entity target = hit.getEntity();
        {
            AcademyDamageHelper.hurt(player,target,player.damageSources().playerAttack(player), DynamicSkillRules.damage(getId(),damage));
            int splashes = 6 + level.random.nextInt(4);
            Vec3 lookOffset = player.getLookAngle().scale(.2);
            for (int i = 0; i < splashes; i++) {
                double ox = (level.random.nextDouble() * 2 - 1) * target.getBbWidth();
                double oy = level.random.nextDouble() * target.getBbHeight();
                double oz = (level.random.nextDouble() * 2 - 1) * target.getBbWidth();
                EffectHelper.bloodSplash(level, target.getX() + ox + lookOffset.x,
                        target.getY() + oy + lookOffset.y, target.getZ() + oz + lookOffset.z,
                        1.4f + level.random.nextFloat() * .4f);
            }
            Vec3 head = target.position().add(0, target.getBbHeight() * .6, 0);
            for (int pitch : new int[]{0,30,45,60,80,-30,-45,-60,-80}) {
                float yaw = player.getYHeadRot() + level.random.nextFloat() * 40 - 20;
                Vec3 look = Vec3.directionFromRotation(pitch, yaw);
                BlockHitResult decalHit = level.clip(new ClipContext(head.subtract(look.scale(.5)),
                        head.add(look.scale(5)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
                if (decalHit.getType() == HitResult.Type.BLOCK) {
                    BloodSprayDecalEntity.spawn(level, decalHit.getLocation(), decalHit.getDirection());
                    BloodSprayDecalEntity.spawn(level, decalHit.getLocation(), decalHit.getDirection());
                }
            }

            DynamicSkillRules.addExp(player,data, getId(), 0.002f);
            if (data.getProficiency(getId()) >= 1.0f) {
                com.mohistmc.academy.advancement.LegacyAdvancementBridge.award(player,"vecmanip/blood_retro");
            }

            AcademySounds.playSound(level, player.getX(), player.getY(), player.getZ(),
                    AcademySounds.VM_BLOOD_RETRO, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (!canRelease(player, data, ticks)) return false;
        onChargingRelease(player, data, ticks);
        return true;
    }

    @Override
    public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {
        cleanupSlow(player);
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
    }

    private static void cleanupSlow(ServerPlayer player) {
        var movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) movement.removeModifier(SLOW_ID);
    }

    private static void applyLegacySlow(ServerPlayer player, int ticks) {
        var movement = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) return;
        movement.removeModifier(SLOW_ID);
        float progress = Math.clamp(ticks / 20.0f, 0.0f, 1.0f);
        double walkScale = lerpf(0.1f, 0.007f, progress) / 0.1f;
        movement.addTransientModifier(new AttributeModifier(SLOW_ID, walkScale - 1.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private EntityHitResult rayTraceEntity(ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(range));
        var block=player.level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        double wallDist=block.getType()==HitResult.Type.MISS?Double.MAX_VALUE:start.distanceTo(block.getLocation());
        List<Entity> entities = player.level().getEntities(player,
                player.getBoundingBox().inflate(range),
                e -> (e instanceof LivingEntity || e instanceof EnderDragonPart)
                        && e.isAlive() && e.isPickable());

        Entity closest = null;
        double closestDist = Double.MAX_VALUE;
        Vec3 closestHit = null;

        for (Entity e : entities) {
            var result = e.getBoundingBox().inflate(0.3).clip(start, end);
            if (result.isPresent()) {
                double dist = start.distanceTo(result.get());
                if (dist < closestDist && dist < wallDist) {
                    closestDist = dist;
                    closest = e;
                    closestHit = result.get();
                }
            }
        }
        return closest != null ? new EntityHitResult(closest, closestHit) : null;
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return (int) lerpf(90, 40, proficiency);
    }
}

