package com.mohistmc.academy.skill.ability.telekinesis;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.DynamicOneShotSkillEffect;
import com.mohistmc.academy.world.AcademyItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

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
        var resourcesBefore = data.captureDynamicResources();
        if (!DynamicSkillRules.tryPay(data, getId(), rawCp(p), rawOverload(p))) return false;
        var resourcesPaid = data.captureDynamicResources();
        ItemStack returned = needle.isEmpty() ? new ItemStack(AcademyItems.NEEDLE.get()) : needle.copyWithCount(1);
        if (!perform(player, data, returned, needle)) {
            data.rollbackDynamicPayment(resourcesBefore, resourcesPaid);
            return false;
        }
        return true;
    }

    private static ItemStack findNeedle(ServerPlayer player) {
        return PsychoAmmo.find(player, stack -> stack.is(AcademyItems.NEEDLE.get()));
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        executeAndReport(player, data);
    }

    private boolean perform(ServerPlayer player, PlayerAbilityData data, ItemStack returned, ItemStack consumed) {
        float p = data.getProficiency(getId());
        ServerLevel level = player.serverLevel();
        if (!PsychoAmmo.launch(player, consumed, returned, p, true)) return false;
        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT,
                SoundSource.PLAYERS, 0.8F, 1.8F);
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, getId(), 0.002F - 0.001F * p);
        return true;
    }

    @Override public int getCooldownTicks(float p) { return Math.round(20 - 10 * Math.clamp(p, 0, 1)); }
}
