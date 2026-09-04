package com.mohistmc.academy.skill.ability.telekinesis;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.Comparator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Throws one cobblestone projectile; etched stone is faster, stronger and cheaper. */
public final class PsychoThrowingEffect implements DynamicOneShotSkillEffect {
    private record Ammo(ItemStack stack, boolean etched) {}

    @Override public String getId() { return "psycho_throwing"; }
    @Override public float rawCp(float p) { return 400 - 200 * Math.clamp(p, 0, 1); }
    @Override public float rawOverload(float p) { return 30 - 10 * Math.clamp(p, 0, 1); }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        Ammo ammo = findAmmo(player);
        if (ammo == null) return false;
        float p = data.getProficiency(getId());
        float multiplier = ammo.etched ? 1.0F : 1.5F;
        return DynamicSkillRules.canPay(data, getId(), rawCp(p) * multiplier,
                rawOverload(p) * multiplier);
    }

    @Override
    public boolean executeAndReport(ServerPlayer player, PlayerAbilityData data) {
        Ammo ammo = findAmmo(player);
        if (ammo == null) return false;
        float p = data.getProficiency(getId());
        float multiplier = ammo.etched ? 1.0F : 1.5F;
        if (!DynamicSkillRules.tryPay(data, getId(), rawCp(p) * multiplier,
                rawOverload(p) * multiplier)) return false;
        if (!player.getAbilities().instabuild) ammo.stack.shrink(1);
        perform(player, data, ammo.etched);
        return true;
    }

    @Override public void execute(ServerPlayer player, PlayerAbilityData data) { perform(player, data, true); }

    private static Ammo findAmmo(ServerPlayer player) {
        if (player.getAbilities().instabuild) return new Ammo(ItemStack.EMPTY, true);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(AcademyItems.ETCHED_COBBLESTONE.get())) return new Ammo(stack, true);
            if (stack.is(Items.COBBLESTONE)) return new Ammo(stack, false);
        }
        return null;
    }

    private static void perform(ServerPlayer player, PlayerAbilityData data, boolean etched) {
        float p = data.getProficiency("psycho_throwing");
        ServerLevel level = player.serverLevel();
        Vec3 from = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 intended = from.add(direction.scale(etched ? 40 : 32));
        var blockHit = level.clip(new ClipContext(from, intended, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        Vec3 to = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : intended;
        LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(0.7),
                        entity -> entity != player && entity.isAlive() && !player.isAlliedTo(entity)
                                && AcademyDamageHelper.allowsTarget(entity)
                                && entity.getBoundingBox().inflate(0.45).clip(from, to).isPresent())
                .stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(player))).orElse(null);
        Vec3 impact = target == null ? to : target.getBoundingBox().getCenter();
        float damage = (12 + 4 * p) * (etched ? 1.25F : 1.0F);
        if (target != null && AcademyDamageHelper.hurt(player, target,
                player.damageSources().playerAttack(player), DynamicSkillRules.damage("psycho_throwing", damage))) {
            double acceleration = (0.1 + 0.05 * p) * (etched ? 1.5 : 1.0) * 20;
            target.push(direction.x * acceleration, direction.y * acceleration, direction.z * acceleration);
            target.hurtMarked = true;
        }
        ItemStack returned = new ItemStack(etched ? AcademyItems.ETCHED_COBBLESTONE.get() : Items.COBBLESTONE);
        ItemEntity drop = new ItemEntity(level, impact.x, impact.y, impact.z, returned);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
        EffectHelper.psychoBurst(level, impact.x, impact.y, impact.z, 12, 0.35);
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(),
                SoundSource.PLAYERS, 0.8F, etched ? 1.2F : 0.9F);
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, "psycho_throwing",
                0.002F - 0.001F * p);
    }

    @Override public int getCooldownTicks(float p) { return Math.round(40 - 20 * Math.clamp(p, 0, 1)); }
}
