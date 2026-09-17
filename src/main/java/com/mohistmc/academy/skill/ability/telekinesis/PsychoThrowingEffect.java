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
import net.minecraft.world.item.Items;

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
        var resourcesBefore = data.captureDynamicResources();
        if (!DynamicSkillRules.tryPay(data, getId(), rawCp(p) * multiplier,
                rawOverload(p) * multiplier)) return false;
        var resourcesPaid = data.captureDynamicResources();
        ItemStack returned = ammo.stack.isEmpty() ? new ItemStack(AcademyItems.ETCHED_COBBLESTONE.get())
                : ammo.stack.copyWithCount(1);
        if (!perform(player, data, ammo.etched, returned, ammo.stack)) {
            data.rollbackDynamicPayment(resourcesBefore, resourcesPaid);
            return false;
        }
        return true;
    }

    @Override public void execute(ServerPlayer player, PlayerAbilityData data) {
        executeAndReport(player, data);
    }

    private static Ammo findAmmo(ServerPlayer player) {
        ItemStack stack = PsychoAmmo.find(player, item -> item.is(AcademyItems.ETCHED_COBBLESTONE.get()) || item.is(Items.COBBLESTONE));
        return stack == null ? null : new Ammo(stack, stack.isEmpty() || stack.is(AcademyItems.ETCHED_COBBLESTONE.get()));
    }

    private static boolean perform(ServerPlayer player, PlayerAbilityData data, boolean etched,
                                   ItemStack returned, ItemStack consumed) {
        float p = data.getProficiency("psycho_throwing");
        ServerLevel level = player.serverLevel();
        if (!PsychoAmmo.launch(player, consumed, returned, p, false)) return false;
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(),
                SoundSource.PLAYERS, 0.8F, etched ? 1.2F : 0.9F);
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, "psycho_throwing",
                0.002F - 0.001F * p);
        return true;
    }

    @Override public int getCooldownTicks(float p) { return Math.round(40 - 20 * Math.clamp(p, 0, 1)); }
}
