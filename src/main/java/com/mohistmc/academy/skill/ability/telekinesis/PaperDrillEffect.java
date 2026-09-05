package com.mohistmc.academy.skill.ability.telekinesis;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.ability.SkillRaycast;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Held paper drill: consumes one stack once, then follows the server-authoritative look ray. */
public final class PaperDrillEffect implements ChargingSkillEffect {
    private static float startCp(float proficiency) {
        return 20F * (200F - 100F * Math.clamp(proficiency, 0, 1));
    }
    private static float startOverload(float proficiency) {
        return 20F * (6F - 2F * Math.clamp(proficiency, 0, 1));
    }
    /**
     * The key-down packet creates this unpaid intent.  Payment is deliberately delayed until
     * the first acknowledged charging tick; otherwise a lost start acknowledgement can consume
     * a full stack of paper even though the client never entered the charging state.
     */
    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private static final class State {
        private final float proficiency;
        private boolean paid;

        private State(float proficiency) {
            this.proficiency = proficiency;
        }
    }

    @Override public String getId() { return "paper_drill"; }
    @Override public boolean appliesBaseResourceCost() { return false; }

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        float proficiency = data.getProficiency(getId());
        return DynamicSkillRules.enabled(getId())
                && data.getCurrentAbility() == AbilityCategory.TELEKINESIS
                && data.hasLearnedSkill("perfect_paper")
                && DynamicSkillRules.canPay(data, getId(), startCp(proficiency), startOverload(proficiency))
                && (player.getAbilities().instabuild
                || countPaper(player) >= TelekinesisRules.PAPER_DRILL_REQUIRED_PAPER);
    }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        ACTIVE.remove(player.getUUID());
        if (canStartCharging(player, data)) {
            ACTIVE.put(player.getUUID(), new State(data.getProficiency(getId())));
        }
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) return false;
        if (!state.paid) {
            if (!canStartCharging(player, data)
                    || !DynamicSkillRules.tryPay(data, getId(), startCp(state.proficiency),
                    startOverload(state.proficiency))) {
                ACTIVE.remove(player.getUUID());
                return false;
            }
            if (!consumePaper(player, TelekinesisRules.PAPER_DRILL_REQUIRED_PAPER)) {
                data.refundDynamic(DynamicSkillRules.cp(getId(), startCp(state.proficiency)),
                        DynamicSkillRules.overload(getId(), startOverload(state.proficiency)));
                ACTIVE.remove(player.getUUID());
                return false;
            }
            state.paid = true;
            DynamicSkillRules.addExp(player, data, getId(), 0.002F);
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0f, 0.65f);
        }
        if (!DynamicSkillRules.tryPay(data, getId(), 1F, 0)) {
            ACTIVE.remove(player.getUUID());
            givePaper(player);
            return false;
        }
        DynamicSkillRules.addExp(player, data, getId(), 0.0001F);
        float proficiency = state.proficiency;
        Vec3 from = player.getEyePosition();
        Vec3 intended = from.add(player.getLookAngle().normalize()
                .scale(TelekinesisRules.paperDrillRange(proficiency)));
        ServerLevel level = player.serverLevel();
        var trace = SkillRaycast.trace(player, from, intended);
        Vec3 to = trace.end();

        if ((ticks & 1) == 0) renderDrill(level, from, to);
        if (ticks % TelekinesisRules.PAPER_DRILL_PULSE_INTERVAL == 0) {
            float damage = DynamicSkillRules.damage(getId(),
                    TelekinesisRules.paperDrillDamage(proficiency));
            int hits = 0;
            for (var hit : trace.hits()) {
                LivingEntity target = hit.entity();
                if (!DynamicSkillRules.tryPay(data, getId(), 100F * target.getBbHeight(), 0)) {
                    ACTIVE.remove(player.getUUID());
                    givePaper(player);
                    return false;
                }
                // The drill's configured pulse is intentional; vanilla's ten-tick hurt window
                // would otherwise silently discard every other continuous-damage pulse.
                target.invulnerableTime = 0;
                if (AcademyDamageHelper.hurt(player, target,
                        player.damageSources().playerAttack(player), damage)) {
                    hits++;
                    DynamicSkillRules.addExp(player, data, getId(), 0.002F);
                }
            }
            if (hits > 0 && !data.isDevMode()) {
                DynamicSkillRules.addExp(player, data, getId(), 0.0005f * hits);
            }
        }
        return true;
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return onChargingTick(player, data, ticks) ? TickResult.CONTINUE : TickResult.ABORT_RESOURCE;
    }

    @Override public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        State state = ACTIVE.remove(player.getUUID());
        if (state != null && state.paid) givePaper(player);
        return state != null && state.paid;
    }

    @Override public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        tryRelease(player, data, ticks);
    }

    @Override public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {
        State state = ACTIVE.remove(player.getUUID());
        if (state != null && state.paid) givePaper(player);
    }

    @Override public int getMinChargeTicks() { return 1; }
    @Override public int getMaxChargeTicks() { return 72_000; }
    @Override public int getCooldownTicks(float proficiency) {
        return 100;
    }
    @Override public int getCooldownTicks(float proficiency, int chargedTicks) {
        return 100;
    }
    @Override public void execute(ServerPlayer player, PlayerAbilityData data) {}

    static int countPaper(ServerPlayer player) {
        int total = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(Items.PAPER)) total += stack.getCount();
        }
        return total;
    }

    static boolean consumePaper(ServerPlayer player, int amount) {
        if (player.getAbilities().instabuild) return true;
        if (amount <= 0 || countPaper(player) < amount) return false;
        int remaining = amount;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(Items.PAPER)) continue;
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
        }
        inventory.setChanged();
        return remaining == 0;
    }

    private static void givePaper(ServerPlayer player) {
        ItemStack paper = new ItemStack(Items.PAPER, TelekinesisRules.PAPER_DRILL_REQUIRED_PAPER);
        if (!player.getInventory().add(paper)) player.drop(paper, false);
        player.getInventory().setChanged();
    }

    private static void renderDrill(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 0.1) return;
        Vec3 step = delta.normalize().scale(1.25);
        for (double distance = 0.5; distance < length; distance += 1.25) {
            Vec3 at = from.add(step.scale(distance / 1.25));
            EffectHelper.glowBurst(level, at.x, at.y, at.z, 1, 0.45,
                    0xCCF2F2FF, 6, 0.18);
        }
        EffectHelper.psychoBurst(level, to.x, to.y, to.z, 3, 0.28);
    }
}
