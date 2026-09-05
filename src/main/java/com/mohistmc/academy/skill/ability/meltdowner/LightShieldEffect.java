package com.mohistmc.academy.skill.ability.meltdowner;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.entity.ShieldEffectEntity;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.passive.PassiveDamageHelper;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import com.mohistmc.academy.skill.AcceptedAbilityDamage;

/** Server-authoritative final 1.12.2 shield surface; the entity is visual-only. */
public final class LightShieldEffect implements ChargingSkillEffect {
    private static final int ACTION_INTERVAL = 18;

    private static final class State {
        final float exp;
        final float overloadKeep;
        final ShieldEffectEntity visual;
        int ticks;
        int lastAbsorb = -1000;

        State(PlayerAbilityData data, float exp, ShieldEffectEntity visual) {
            this.exp = exp;
            this.overloadKeep = data.getCurrentOverload();
            this.visual = visual;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    @Override public String getId() { return "light_shield"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }
    @Override public int getMinChargeTicks() { return 0; }
    @Override public int getMaxChargeTicks() { return 180; }
    @Override public int getMaxChargeTicks(PlayerAbilityData data) {
        return (int) lerpf(120, 180, data.getProficiency(getId()));
    }
    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) {
        return getMaxChargeTicks(data) + 1;
    }
    @Override public TickResult getSessionTimeoutResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return TickResult.ABORT_RESOURCE;
    }

    private float startOverload(PlayerAbilityData data) {
        return lerpf(110, 60, data.getProficiency(getId()));
    }

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.canPay(data, getId(), 0, startOverload(data));
    }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        if (!canStartCharging(player, data)) return;
        float exp = data.getProficiency(getId());
        if (!DynamicSkillRules.tryPay(data, getId(), 0, startOverload(data))) return;
        ShieldEffectEntity visual = new ShieldEffectEntity(
                AcademyEntities.SHIELD_EFFECT.get(), player.serverLevel()).bind(player.getUUID());
        player.serverLevel().addFreshEntity(visual);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                AcademySounds.MD_SHIELD_STARTUP, SoundSource.PLAYERS, .5F, 1F);
        ACTIVE.put(player.getUUID(), new State(data, exp, visual));
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData ignored, int ticks) {
        State state = ACTIVE.get(player.getUUID());
        if (state == null) return false;
        PlayerAbilityData data = authority(player);
        state.ticks = ticks;
        if (!data.isDevMode() && data.getCurrentOverload() < state.overloadKeep) {
            data.setCurrentOverload(state.overloadKeep);
        }

        float cp = lerpf(9, 4, state.exp);
        boolean maintained = pay(data, cp, 0);
        if (player.getRandom().nextFloat() < .3F) {
            var position = player.getEyePosition().add(player.getLookAngle()).add(
                    player.getRandom().nextDouble() - .5,
                    player.getRandom().nextDouble() - .5,
                    player.getRandom().nextDouble() - .5);
            var velocity = new net.minecraft.world.phys.Vec3(
                    (player.getRandom().nextDouble() * 2 - 1) * .02,
                    player.getRandom().nextDouble() * .06 - .01,
                    (player.getRandom().nextDouble() * 2 - 1) * .02);
            EffectHelper.meltdownMovingParticle(player.serverLevel(), position, velocity);
        }
        DynamicSkillRules.addExp(player, data, getId(), 1e-6F);

        for (Entity entity : player.serverLevel().getEntities(player,
                player.getBoundingBox().inflate(3), candidate -> candidate != player
                        && candidate.distanceToSqr(player) <= 9 && frontal(player, candidate))) {
            if (entity.invulnerableTime > 0) continue;
            float touchCp = lerpf(50, 30, state.exp);
            float touchOverload = lerpf(5, 3, state.exp);
            if (!pay(data, touchCp, touchOverload)) continue;
            PassiveDamageHelper.meltdownerAttack(player, data, entity, getId(), lerpf(2, 6, state.exp));
            DynamicSkillRules.addExp(player, data, getId(), .001F);
        }
        return maintained;
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return onChargingTick(player, data, ticks) ? TickResult.CONTINUE : TickResult.ABORT_RESOURCE;
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData ignored, int ticks) {
        State state = ACTIVE.remove(player.getUUID());
        if (state == null) return false;
        finishAndCooldown(player, state, ticks);
        return true;
    }

    @Override public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        tryRelease(player, data, ticks);
    }

    @Override public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {
        State state = ACTIVE.remove(player.getUUID());
        if (state != null) finishAndCooldown(player, state, state.ticks);
    }

    private static void finishAndCooldown(ServerPlayer player, State state, int ticks) {
        state.visual.discard();
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
        PlayerAbilityData data = authority(player);
        if (!data.isDevMode()) {
            data.setCooldown("light_shield", (int) lerpf(2 * ticks, ticks, state.exp));
        }
    }

    @Override public void execute(ServerPlayer player, PlayerAbilityData data) {}
    @Override public int getCooldownTicks(float proficiency) { return 0; }

    private static boolean pay(PlayerAbilityData data, float cp, float overload) {
        return DynamicSkillRules.tryPay(data, "light_shield", cp, overload);
    }

    private static boolean frontal(ServerPlayer player, Entity entity) {
        double dx = entity.getX() - player.getX();
        double dz = entity.getZ() - player.getZ();
        double yaw = -Math.toDegrees(Math.atan2(dx, dz));
        return Math.abs(yaw - player.getYRot()) % 360 < 60;
    }

    static boolean shouldAbsorb(ServerPlayer player, DamageSource source) {
        Entity direct = source.getDirectEntity();
        // The final Java LightShield explicitly accepts a null immediate source,
        // which is the 1.12.2 fix that makes falling/environmental damage absorbable.
        return direct == null || frontal(player, direct);
    }

    private static PlayerAbilityData authority(ServerPlayer player) {
        return player.getData(AcademyAttachments.PLAYER_ABILITY);
    }

    /** Accept only hits which passed public vetoes, shield and hurt cooldown. */
    public static void damage(AcceptedAbilityDamage event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        State state = ACTIVE.get(player.getUUID());
        if (state == null || event.getAmount() <= 0
                || state.ticks - state.lastAbsorb <= ACTION_INTERVAL) return;

        PlayerAbilityData data = authority(player);
        if (shouldAbsorb(player, event.getSource())) {
            state.lastAbsorb = state.ticks;
            // Final 1.12.2 handleAttacked passed these in the opposite order
            // from touch damage: 3..5 CP and 30..50 overload.
            if (pay(data, lerpf(5, 3, state.exp), lerpf(50, 30, state.exp))) {
                float left = Math.max(0, event.getAmount()
                        - DynamicSkillRules.damage("light_shield", lerpf(15, 50, state.exp)));
                event.setAmount(left);
                if (left == 0) event.setCanceled(true);
            }
        }
        DynamicSkillRules.addExp(player, data, "light_shield", .001F);
    }
}
