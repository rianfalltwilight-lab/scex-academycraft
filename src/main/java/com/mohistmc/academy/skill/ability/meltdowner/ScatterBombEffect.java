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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Final 1.12.2 seven-orb scatter context, including mastery auto-aim and over-hold behavior. */
public final class ScatterBombEffect implements ChargingSkillEffect {
    private static final int ACTIVE_TICKS = 80;
    private static final int OVERHOLD_TICKS = 200;
    private static final double RAY_RANGE = 15;

    private static final class State {
        final List<UUID> balls = new ArrayList<>();
        final float overloadFloor;
        State(float overloadFloor) { this.overloadFloor = overloadFloor; }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    @Override public String getId() { return "scatter_bomb"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }
    @Override public int getMinChargeTicks() { return 1; }
    @Override public int getMaxChargeTicks() { return ACTIVE_TICKS; }
    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) { return OVERHOLD_TICKS; }

    private float startOverload(PlayerAbilityData data) {
        return lerpf(80, 60, data.getProficiency(getId()));
    }

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.canPay(data, getId(), 0, startOverload(data));
    }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        if (!DynamicSkillRules.tryPay(data, getId(), 0, startOverload(data))) return;
        STATES.put(player.getUUID(), new State(data.getCurrentOverload()));
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        State state = STATES.get(player.getUUID());
        if (state == null) return false;
        if (!data.isDevMode() && data.getCurrentOverload() < state.overloadFloor) {
            data.setCurrentOverload(state.overloadFloor);
        }

        // The final 1.12.2 context stops consuming and spawning after tick 80, but it
        // remains armed until key-up. At tick 200 it hurts the caster, then
        // terminates and fires every ball accumulated so far.
        if (ticks <= ACTIVE_TICKS) {
            // SBContext spawned before attempting this tick's CP debit, so a
            // failed cadence tick still contributed and fired its new ball.
            if (ticks >= 20 && ticks % 10 == 0) spawnBall(player, state);
            float cp = lerpf(3, 6, data.getProficiency(getId()));
            if (!DynamicSkillRules.tryPay(data, getId(), cp, 0)) return false;
        }
        if (ticks == OVERHOLD_TICKS) {
            AcademyDamageHelper.hurtSelf(player, player, player.damageSources().playerAttack(player), 6.0F);
        }
        return true;
    }

    private static void spawnBall(ServerPlayer player, State state) {
        MdBallEntity ball = AcademyEntities.MD_BALL.get().create(player.serverLevel());
        if (ball == null) return;
        ball.bind(player.getUUID(), state.balls.size(), false);
        // bind() already applies EntityMdBall's final-1.12.2 random orbit.
        // Overwriting it with the caster's feet made an immediate key-up ray
        // collide with the floor before it could reach an auto-target.
        if (player.serverLevel().addFreshEntity(ball)) state.balls.add(ball.getUUID());
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (!STATES.containsKey(player.getUUID())) return TickResult.ABORT_RESOURCE;
        // Context termination in final 1.12.2 still ran s_onEnd and fired all
        // already-created balls when the just-attempted upkeep payment failed.
        return onChargingTick(player, data, ticks) ? TickResult.CONTINUE : TickResult.RELEASE;
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        State state = STATES.remove(player.getUUID());
        if (state == null) return false;
        fire(player, data, state);
        return true;
    }

    private void fire(ServerPlayer player, PlayerAbilityData data, State state) {
        float exp = data.getProficiency(getId());
        float damage = lerpf(5, 9, exp);
        int fired = 0;
        int autoCount = exp > .5F ? (int) (state.balls.size() * exp) : 0;
        List<Mob> autoTargets = exp > .5F
                ? player.serverLevel().getEntitiesOfClass(Mob.class,
                        player.getBoundingBox().inflate(5), Mob::isAlive)
                : List.of();

        for (UUID id : state.balls) {
            Vec3 destination = legacyRandomDestination(player);
            if (autoCount > 0 && !autoTargets.isEmpty()) {
                Mob target = autoTargets.get(player.getRandom().nextInt(autoTargets.size()));
                destination = target.position().add(0, target.getEyeHeight(), 0);
                autoCount--;
            }
            Entity raw = player.serverLevel().getEntity(id);
            Vec3 from = raw == null ? player.getEyePosition() : raw.position();
            Entity target = nearestVisibleTarget(player, from, destination);
            if (target != null) {
                target.invulnerableTime = -1;
                PassiveDamageHelper.meltdownerAttack(player, data, target, getId(), damage);
            }
            EffectHelper.mdRaySmall(player.serverLevel(), from, destination);
            player.serverLevel().playSound(null, from.x, from.y, from.z,
                    AcademySounds.MD_RAY_SMALL, SoundSource.PLAYERS, .5F, 1.0F);
            if (raw != null) raw.discard();
            fired++;
        }
        if (!data.isDevMode()) DynamicSkillRules.addExp(player, data, getId(), .001F * fired);
    }

    /**
     * ScatterBomb.newDest in final 1.12.2 starts at the ordinary 15-block look
     * endpoint and then adds another 15-block vector rotated by up to 12.5
     * degrees on each axis.  It is deliberately not a simple 15-block ray.
     */
    static Vec3 legacyRandomDestination(ServerPlayer player) {
        Vec3 look = player.getLookAngle().normalize();
        Vec3 begin = player.getEyePosition().add(look.scale(RAY_RANGE));
        float pitch = (float) Math.toRadians((player.getRandom().nextFloat() - .5F) * 25F);
        float yaw = (float) Math.toRadians((player.getRandom().nextFloat() - .5F) * 25F);
        return begin.add(look.xRot(pitch).yRot(yaw).scale(RAY_RANGE));
    }

    static Entity nearestVisibleTarget(ServerPlayer player, Vec3 from, Vec3 destination) {
        BlockHitResult wall = player.serverLevel().clip(new ClipContext(from, destination,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double maxDistance = wall.getType() == HitResult.Type.BLOCK
                ? from.distanceToSqr(wall.getLocation()) : from.distanceToSqr(destination);
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity candidate : player.serverLevel().getEntities(player,
                new AABB(from, destination).inflate(1),
                entity -> entity != player && !(entity instanceof MdBallEntity)
                        && entity.isAlive() && entity.isPickable())) {
            var hit = candidate.getBoundingBox().inflate(.3).clip(from, destination);
            if (hit.isEmpty()) continue;
            double distance = from.distanceToSqr(hit.get());
            if (distance < bestDistance && distance <= maxDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    @Override public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        tryRelease(player, data, ticks);
    }

    @Override public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {
        State state = STATES.remove(player.getUUID());
        if (state == null) return;
        // Every final 1.12.2 context termination path invokes s_onEnd, including
        // key-abort/interference, and therefore fires already-created balls.
        fire(player, data, state);
    }

    @Override public void execute(ServerPlayer player, PlayerAbilityData data) {}
    @Override public int getCooldownTicks(float proficiency) { return 0; }
}
