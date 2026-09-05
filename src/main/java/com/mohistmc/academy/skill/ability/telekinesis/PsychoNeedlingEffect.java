package com.mohistmc.academy.skill.ability.telekinesis;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.ability.SkillRaycast;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.effect.EffectHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Consumes and returns one Academy needle while firing an armor-bypassing psycho projectile. */
public final class PsychoNeedlingEffect implements DynamicOneShotSkillEffect {
    @Override public String getId() { return "psycho_needling"; }
    @Override public float rawCp(float p) { return 800 - 400 * Math.clamp(p, 0, 1); }
    @Override public float rawOverload(float p) { return 20 - 10 * Math.clamp(p, 0, 1); }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        return findNeedle(player) != null && DynamicOneShotSkillEffect.super.canActivate(player, data);
    }

    @Override
    public boolean executeAndReport(ServerPlayer player, PlayerAbilityData data) {
        ItemStack needle = findNeedle(player);
        if (needle == null) return false;
        float p = data.getProficiency(getId());
        if (!DynamicSkillRules.tryPay(data, getId(), rawCp(p), rawOverload(p))) return false;
        if (!player.getAbilities().instabuild) needle.shrink(1);
        execute(player, data);
        return true;
    }

    private static ItemStack findNeedle(ServerPlayer player) {
        if (player.getAbilities().instabuild) return ItemStack.EMPTY;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(AcademyItems.NEEDLE.get())) return stack;
        }
        return null;
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        float p = data.getProficiency(getId());
        ServerLevel level = player.serverLevel();
        Vec3 from = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 intended = from.add(direction.scale(32 + 16 * p));
        var trace = SkillRaycast.trace(player, from, intended);
        LivingEntity target = trace.firstEntity();
        Vec3 impact = trace.firstImpact();
        if (target != null) AcademyDamageHelper.hurt(player, target, player.damageSources().magic(),
                DynamicSkillRules.damage(getId(), 4 + 4 * p));
        ItemEntity returned = new ItemEntity(level, impact.x, impact.y, impact.z,
                new ItemStack(AcademyItems.NEEDLE.get()));
        returned.setDefaultPickUpDelay();
        level.addFreshEntity(returned);
        EffectHelper.psychoBurst(level, impact.x, impact.y, impact.z, 8, 0.2);
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT,
                SoundSource.PLAYERS, 0.8F, 1.8F);
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, getId(), 0.002F - 0.001F * p);
    }

    @Override public int getCooldownTicks(float p) { return Math.round(20 - 10 * Math.clamp(p, 0, 1)); }
}
