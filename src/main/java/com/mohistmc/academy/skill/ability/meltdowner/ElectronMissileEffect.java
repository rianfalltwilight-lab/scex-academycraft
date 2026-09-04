package com.mohistmc.academy.skill.ability.meltdowner;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.entity.MdBallEntity;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.passive.PassiveDamageHelper;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** 1.0.7 held five-ball missile context with zero-based spawn/attack cadence. */
public final class ElectronMissileEffect implements ChargingSkillEffect {
    private static final float START_OVERLOAD = 200;
    private static final int MAX_ORBS = 5;

    private static final class State {
        final List<UUID> orbs = new ArrayList<>();
        final float overloadFloor;
        State(float overloadFloor) { this.overloadFloor = overloadFloor; }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    @Override public String getId() { return "electron_missile"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }
    @Override public int getMinChargeTicks() { return 0; }
    @Override public int getMaxChargeTicks() { return 200; }
    @Override public int getMaxChargeTicks(PlayerAbilityData data) {
        return (int) lerpf(80, 200, data.getProficiency(getId()));
    }
    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) {
        // Server state ticks begin at one; the old context's first update was
        // tick zero and terminated only after its zero-based time limit.
        return getMaxChargeTicks(data) + 2;
    }

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.canPay(data, getId(), 0, START_OVERLOAD);
    }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        if (!DynamicSkillRules.tryPay(data, getId(), 0, START_OVERLOAD)) return;
        STATES.put(player.getUUID(), new State(data.getCurrentOverload()));
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        State state = STATES.get(player.getUUID());
        if (state == null) return false;
        float exp = data.getProficiency(getId());
        if (!DynamicSkillRules.tryPay(data, getId(), lerpf(12, 5, exp), 0)) return false;
        if (!data.isDevMode() && data.getCurrentOverload() < state.overloadFloor) {
            data.setCurrentOverload(state.overloadFloor);
        }

        int legacyTick = ticks - 1;
        // EMContextC received MSG_EFFECT_UPDATE on every successful upkeep
        // tick, including the final over-limit tick which terminated it.
        EffectHelper.meltdownBurst(player.serverLevel(), player.getX(), player.getY() + .4,
                player.getZ(), 1 + player.getRandom().nextInt(3), .7);
        if (legacyTick > getMaxChargeTicks(data)) return true;
        if (legacyTick % 10 == 0 && state.orbs.size() < MAX_ORBS) spawnOrb(player, state);
        if (legacyTick != 0 && legacyTick % 8 == 0 && !state.orbs.isEmpty()) {
            attackNearest(player, data, state, exp);
        }
        return true;
    }

    private static void spawnOrb(ServerPlayer player, State state) {
        MdBallEntity ball = AcademyEntities.MD_BALL.get().create(player.serverLevel());
        if (ball == null) return;
        ball.bind(player.getUUID(), state.orbs.size(), false);
        ball.setPos(player.position());
        if (player.serverLevel().addFreshEntity(ball)) state.orbs.add(ball.getUUID());
    }

    private void attackNearest(ServerPlayer player, PlayerAbilityData data, State state, float exp) {
        float cp = lerpf(60, 25, exp);
        float overload = lerpf(9, 4, exp);
        if (!DynamicSkillRules.canPay(data, getId(), cp, overload)) return;
        float range = lerpf(5, 13, exp);
        LivingEntity target = player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(range),
                        entity -> entity != player && entity.isAlive()
                                && entity.distanceToSqr(player) <= range * range)
                .stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
        if (target == null || !DynamicSkillRules.tryPay(data, getId(), cp, overload)) return;

        UUID id = state.orbs.remove(player.getRandom().nextInt(state.orbs.size()));
        Entity ball = player.serverLevel().getEntity(id);
        var from = ball == null ? player.getEyePosition() : ball.position();
        target.invulnerableTime = -1;
        PassiveDamageHelper.meltdownerAttack(player, data, target, getId(), lerpf(10, 18, exp));
        EffectHelper.mdRaySmall(player.serverLevel(), from, target.getEyePosition());
        player.serverLevel().playSound(null, from.x, from.y, from.z,
                AcademySounds.MD_RAY_SMALL, SoundSource.PLAYERS, .5F, 1.0F);
        if (ball != null) ball.discard();
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, getId(), .001F);
    }

    @Override public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return onChargingTick(player, data, ticks) ? TickResult.CONTINUE : TickResult.ABORT_RESOURCE;
    }

    @Override public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        State state = STATES.remove(player.getUUID());
        if (state == null) return false;
        clear(player, state);
        return true;
    }

    private static void clear(ServerPlayer player, State state) {
        for (UUID id : state.orbs) {
            Entity entity = player.serverLevel().getEntity(id);
            if (entity != null) entity.discard();
        }
        state.orbs.clear();
    }

    @Override public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        tryRelease(player, data, ticks);
    }

    @Override public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {
        State state = STATES.remove(player.getUUID());
        if (state == null) return;
        clear(player, state);
        if (!data.isDevMode()) data.setCooldown(getId(), getCooldownTicks(data.getProficiency(getId())));
    }

    @Override public void execute(ServerPlayer player, PlayerAbilityData data) {}
    @Override public int getCooldownTicks(float exp) {
        // Source calls clampi(700, 400, exp.toInt); LambdaLib's
        // clamp(min,max,value) therefore returns the lower bound, 700.
        return 700;
    }
}
