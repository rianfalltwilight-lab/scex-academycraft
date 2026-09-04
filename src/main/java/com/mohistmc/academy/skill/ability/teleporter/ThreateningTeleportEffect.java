package com.mohistmc.academy.skill.ability.teleporter;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.passive.PassiveDamageHelper;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.AcademyParticles;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** Final 1.12.2 Threatening Teleport: hold the item marker, then release one item at the ray endpoint. */
public final class ThreateningTeleportEffect implements ChargingSkillEffect {
    private record Target(Vec3 dropPosition, Entity entity) {}

    @Override public String getId() { return "threatening_teleport"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }
    @Override public int getMinChargeTicks() { return 0; }
    @Override public int getMaxChargeTicks() { return 1; }
    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) { return Integer.MAX_VALUE; }

    @Override public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.enabled(getId()) && !player.getMainHandItem().isEmpty();
    }
    @Override public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {}

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (player.getMainHandItem().isEmpty()) return false;
        return true;
    }

    @Override public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return onChargingTick(player, data, ticks) ? TickResult.CONTINUE : TickResult.ABORT_RESOURCE;
    }

    @Override
    public boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float exp = data.getProficiency(getId());
        return !player.getMainHandItem().isEmpty()
                && DynamicSkillRules.canPay(data, getId(), lerpf(35, 100, exp), lerpf(18, 10, exp));
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (!canRelease(player, data, ticks)) return false;
        ItemStack held = player.getMainHandItem();
        float exp = data.getProficiency(getId());
        Target target = target(player, lerpf(8, 15, exp));
        if (!DynamicSkillRules.tryPay(data, getId(), lerpf(35, 100, exp), lerpf(18, 10, exp))) return false;

        ItemStack one = held.copyWithCount(1);
        if (!player.getAbilities().instabuild) held.shrink(1);
        boolean targeted = target.entity != null;
        if (targeted) {
            float damage = lerpf(3, 6, exp) * (one.is(AcademyItems.NEEDLE.get()) ? 1.5f : 1f);
            AcademyDamageHelper.hurt(player, target.entity,
                    player.damageSources().source(net.minecraft.world.damagesource.DamageTypes.MAGIC, player),
                    PassiveDamageHelper.teleporter(player, data, target.entity, getId(), damage).damage());
        }
        if (!targeted || player.getRandom().nextFloat() < .3f)
            player.serverLevel().addFreshEntity(new ItemEntity(player.serverLevel(), target.dropPosition.x,
                    target.dropPosition.y, target.dropPosition.z, one));

        com.mohistmc.academy.network.SafePayloadSender.send(player,
                new com.mohistmc.academy.network.TeleporterTrailPacket(player.getX(),player.getY()-.5,player.getZ(),
                        target.dropPosition.x+.5,target.dropPosition.y+.5,target.dropPosition.z+.5,
                        com.mohistmc.academy.network.TeleporterTrailPacket.THREATENING));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                AcademySounds.TP_TP, SoundSource.PLAYERS, .5f, 1f);
        DynamicSkillRules.addExp(player, data, getId(), (targeted ? 1f : .2f) * .003f);
        return true;
    }

    @Override public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        tryRelease(player, data, ticks);
    }
    @Override public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {}
    @Override public void execute(ServerPlayer player, PlayerAbilityData data) {}
    @Override public int getCooldownTicks(float proficiency) { return (int) lerpf(30, 15, proficiency); }

    private static Target target(ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition();
        Vec3 intended = start.add(player.getLookAngle().scale(range));
        HitResult wall = player.serverLevel().clip(new ClipContext(start, intended,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = wall.getType() == HitResult.Type.MISS ? intended : wall.getLocation();
        // ThreateningTeleport deliberately used filEverything (which rejects
        // every block in LambdaLib), so a living target wins even through a wall.
        List<Entity> entities = player.serverLevel().getEntities(player,
                new AABB(start, intended).inflate(1), entity -> entity != player
                        && (entity instanceof LivingEntity || entity instanceof EnderDragonPart)
                        && entity.isPickable());
        Entity closest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : entities) {
            var clip = entity.getBoundingBox().inflate(.3).clip(start, intended);
            if (clip.isPresent() && start.distanceToSqr(clip.get()) < best) {
                best = start.distanceToSqr(clip.get());
                closest = entity;
            }
        }
        return closest == null ? new Target(end, null)
                : new Target(new Vec3(closest.getX(), closest.getY() + closest.getBbHeight(), closest.getZ()), closest);
    }
}
