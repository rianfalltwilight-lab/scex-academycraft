package com.mohistmc.academy.skill.ability.electromaster;

import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 生物电强化 —— 蓄力后获得多种药水效果 */
public class BodyIntensifyEffect implements ChargingSkillEffect {

    private static final int MIN_TICKS = 10;
    private static final int MAX_TICKS = 40;
    private static final int MAX_TOLERANT_TICKS = 100;
    private static final Map<UUID, Float> OVERLOAD_FLOORS = new ConcurrentHashMap<>();

    private static final List<MobEffectInstance> BASE_EFFECTS = List.of(
            new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 0, 3),
            new MobEffectInstance(MobEffects.JUMP, 0, 1),
            new MobEffectInstance(MobEffects.REGENERATION, 0, 1),
            new MobEffectInstance(MobEffects.DAMAGE_BOOST, 0, 1),
            new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 0, 1)
    );

    @Override
    public String getId() {
        return "body_intensify";
    }
    @Override public boolean appliesBaseResourceCost(){return false;}
    @Override public boolean grantsActivationProficiency(){return false;}

    @Override
    public int getMinChargeTicks() {
        return MIN_TICKS;
    }

    @Override
    public int getMaxChargeTicks() {
        return MAX_TICKS;
    }

    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) { return MAX_TOLERANT_TICKS; }
    @Override public TickResult getSessionTimeoutResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return TickResult.ABORT_RESOURCE;
    }

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.canPay(data, getId(), 0,
                lerpf(200, 120, data.getProficiency(getId())));
    }

    @Override
    public boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return ticks >= MIN_TICKS && ticks < MAX_TOLERANT_TICKS;
    }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        float overload = lerpf(200, 120, exp);
        if (!DynamicSkillRules.tryPay(data,getId(),0,overload)) return;
        OVERLOAD_FLOORS.put(player.getUUID(), data.getCurrentOverload());
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float exp = data.getProficiency(getId());
        float consumption = lerpf(20, 15, exp);
        Float overloadFloor = OVERLOAD_FLOORS.get(player.getUUID());
        if (overloadFloor == null) return false;
        if (!data.isDevMode() && data.getCurrentOverload() < overloadFloor) {
            data.setCurrentOverload(overloadFloor);
        }

        if (ElectromasterRules.shouldConsumeBodyIntensifyTick(ticks)
                && !DynamicSkillRules.tryPay(data,getId(),consumption,0)) return false;

        // 到达最大容忍时间，自动释放
        if (ticks >= MAX_TOLERANT_TICKS) {
            return false;
        }

        return ticks < MAX_TOLERANT_TICKS;
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (ticks >= MAX_TOLERANT_TICKS) return TickResult.ABORT_RESOURCE;
        float required = ElectromasterRules.shouldConsumeBodyIntensifyTick(ticks)
                ? lerpf(20, 15, data.getProficiency(getId())) : 0;
        boolean enough = DynamicSkillRules.canPay(data,getId(),required,0);
        boolean continuing = onChargingTick(player, data, ticks);
        // Legacy MSG_EFFECT_END(false): resource exhaustion is never a successful release,
        // even after the minimum charge time has elapsed.
        return !enough ? TickResult.ABORT_RESOURCE
                : (continuing ? TickResult.CONTINUE : TickResult.RELEASE);
    }

    @Override
    public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        OVERLOAD_FLOORS.remove(player.getUUID());
        if (ticks < MIN_TICKS) {
            return;
        }

        float exp = data.getProficiency(getId());
        int effectiveTicks = ElectromasterRules.bodyIntensifyEffectiveTicks(ticks);

        double probability = ElectromasterRules.bodyIntensifyProbability(effectiveTicks);
        int buffTime = (int) ((1.0f + player.getRandom().nextFloat()) * effectiveTicks
                * lerpf(1.5f, 2.5f, exp));
        int buffLevel = (int) Math.floor(probability);

        // 1.0.7 calls Random.shuffle(effects) but discards the returned immutable
        // Vector. It then increments i before indexing, so the observable sequence
        // is Jump Boost followed by Regeneration, never the nominal first entry.
        double p = probability;
        int idx = 0;
        while (p > 0 && idx + 1 < BASE_EFFECTS.size()) {
            if (player.getRandom().nextDouble() < p) {
                idx++;
                MobEffectInstance template = BASE_EFFECTS.get(idx);
                int level = Math.min(buffLevel, template.getAmplifier());
                int duration = buffTime;
                player.addEffect(new MobEffectInstance(
                        template.getEffect(), duration, level,
                        template.isAmbient(), true, true
                ));
            }
            p -= 1.0;
        }

        // 饥饿 debuff（副作用）
        int hungerTime = (int) (1.25f * effectiveTicks);
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, hungerTime, 2));

        if (!data.isDevMode()) {
            DynamicSkillRules.addExp(player,data, getId(), 0.01f);
            // IntensifyContext queried ctx.getSkillExp after addSkillExp.
            data.setCooldown(getId(), getCooldownTicks(data.getProficiency(getId())));
        }
        AcademySounds.playSound(player, AcademySounds.EM_INTENSIFY_ACTIVATE, 0.5f, 1.0f);
        EffectHelper.intensifyActivation(player.serverLevel(), player.getX(), player.getY(), player.getZ());
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (!canRelease(player, data, ticks)) return false;
        onChargingRelease(player, data, ticks);
        return true;
    }

    @Override
    public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {
        OVERLOAD_FLOORS.remove(player.getUUID());
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        // 蓄力技能通过 Charging 接口执行
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return (int) lerpf(900, 600, proficiency);
    }

    @Override
    public boolean shouldApplyCooldownAfterRelease(ServerPlayer player, PlayerAbilityData data, int chargedTicks) {
        return false;
    }
}
