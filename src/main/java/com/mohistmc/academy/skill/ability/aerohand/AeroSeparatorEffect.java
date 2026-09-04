package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.effect.EffectHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 空气分离 —— 短暂蓄力后在目标点制造瞬时真空区。
 * Every living entity inside the volume is subject to suffocation, including
 * the caster; only the global PvP boundary protects other players.
 */
public class AeroSeparatorEffect implements ChargingSkillEffect {
    private static final float START_CP = 80.0f;
    private static final float START_OVERLOAD = 60.0f;

    @Override
    public String getId() {
        return "aero_separator";
    }

    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.canPay(data, getId(), START_CP, START_OVERLOAD);
    }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        DynamicSkillRules.tryPay(data, getId(), START_CP, START_OVERLOAD);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 0.9f, 0.55f);
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        int required = getMaxChargeTicks(data);
        if (ticks % 3 == 0) {
            double progress = Math.min(1.0, ticks / (double) required);
            EffectHelper.windBurst(player.serverLevel(), player.getX(),
                    player.getY() + player.getBbHeight() * 0.6, player.getZ(),
                    3, 0.35 + progress * 0.65);
        }
        return ticks < required;
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return onChargingTick(player, data, ticks) ? TickResult.CONTINUE : TickResult.RELEASE;
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (!canRelease(player, data, ticks)) return false;
        onChargingRelease(player, data, ticks);
        return true;
    }

    @Override
    public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        // The description explicitly promises that the caster is inside the
        // vacuum, so this is a self-centred area rather than a remote ray hit.
        Vec3 centre = player.position().add(0, player.getBbHeight() * 0.5, 0);
        detonate(player, data, centre);
        DynamicSkillRules.addExp(player, data, getId(), 0.008f);
    }

    static int detonate(ServerPlayer player, PlayerAbilityData data, Vec3 centre) {
        ServerLevel level = player.serverLevel();
        float proficiency = data.getProficiency("aero_separator");
        float radius = AeroBehaviorMath.separatorRadius(proficiency);
        float damage = DynamicSkillRules.damage("aero_separator",
                AeroBehaviorMath.separatorDamage(proficiency));

        EffectHelper.glowBurst(level, centre.x, centre.y, centre.z,
                Math.round(radius * 10), 0.18f, 0xAAEAFBFF, 12, radius * 0.55);
        level.playSound(null, centre.x, centre.y, centre.z,
                SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 1.5f, 0.45f);

        int affected = 0;
        AABB area = new AABB(centre, centre).inflate(radius, radius * 0.75, radius);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area,
                LivingEntity::isAlive)) {
            // The skill description explicitly calls out self-suffocation. The
            // normal Academy PvP gate still applies to other player targets.
            boolean hurt = living == player
                    ? AcademyDamageHelper.hurtSelf(player, living,
                            level.damageSources().inWall(), damage)
                    : AcademyDamageHelper.hurt(player, living, level.damageSources().inWall(), damage);
            if (hurt) {
                living.setAirSupply(Math.min(living.getAirSupply(), 0));
                affected++;
            }
        }
        return affected;
    }

    @Override public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {}
    @Override public void execute(ServerPlayer player, PlayerAbilityData data) {}

    @Override public int getMinChargeTicks() { return AeroBehaviorMath.separatorChargeTicks(0); }
    @Override public int getMaxChargeTicks() { return AeroBehaviorMath.separatorChargeTicks(0); }
    @Override public int getMinChargeTicks(PlayerAbilityData data) {
        return AeroBehaviorMath.separatorChargeTicks(data.getProficiency(getId()));
    }
    @Override public int getMaxChargeTicks(PlayerAbilityData data) { return getMinChargeTicks(data); }

    @Override
    public int getCooldownTicks(float proficiency) {
        return Math.round(300 - 100 * Math.clamp(proficiency, 0.0f, 1.0f));
    }
}
