package com.mohistmc.academy.skill.ability.vecmanip;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.entity.PlasmaOrbEntity;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademySounds;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** 1.0.7 PlasmaCannonContext: a visible fixed overhead body while held, then a 1-block/tick projectile. */
public final class PlasmaCannonEffect implements ChargingSkillEffect {
    private record State(float overloadFloor, UUID orbId) {}
    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    @Override public String getId() { return "plasma_cannon"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }

    @Override public int getMinChargeTicks() { return 60; }
    @Override public int getMaxChargeTicks() { return 60; }
    @Override public int getMinChargeTicks(PlayerAbilityData data) { return chargeTime(data); }
    @Override public int getMaxChargeTicks(PlayerAbilityData data) { return chargeTime(data); }
    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) { return Integer.MAX_VALUE; }

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.canPay(data, getId(), 0, overloadToKeep(data));
    }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        if (!DynamicSkillRules.tryPay(data, getId(), 0, overloadToKeep(data))) return;

        State stale = STATES.remove(player.getUUID());
        discard(player.serverLevel(), stale);

        PlasmaOrbEntity orb = AcademyEntities.PLASMA_ORB.get().create(player.serverLevel());
        if (orb == null) return;
        Vec3 chargePosition = player.position().add(0, 15, 0);
        orb.configureCharging(player, chargePosition);
        if (!player.serverLevel().addFreshEntity(orb)) return;

        STATES.put(player.getUUID(), new State(data.getCurrentOverload(), orb.getUUID()));
        // The legacy FollowEntitySound was attached to the caster, while the
        // visible plasma body was fifteen blocks overhead.
        AcademySounds.playSound(player.serverLevel(), player.getX(), player.getY(), player.getZ(),
                AcademySounds.VM_PLASMA_CANNON, SoundSource.PLAYERS, .5f, 1f);
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        State state = STATES.get(player.getUUID());
        PlasmaOrbEntity orb = resolve(player.serverLevel(), state);
        if (state == null || orb == null || orb.isArmed()) return false;

        if (!data.isDevMode() && data.getCurrentOverload() < state.overloadFloor()) {
            data.setCurrentOverload(state.overloadFloor());
        }

        if (ticks < chargeTime(data)
                && !DynamicSkillRules.tryPay(data, getId(), lerpf(18, 25, data.getProficiency(getId())), 0)) {
            return false;
        }

        if (ticks == chargeTime(data)) {
            AcademySounds.playSound(player.serverLevel(), player.getX(), player.getY(), player.getZ(),
                    AcademySounds.VM_PLASMA_CANNON_T, SoundSource.PLAYERS, .5f, 1f);
        }
        return true;
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return onChargingTick(player, data, ticks) ? TickResult.CONTINUE : TickResult.ABORT_RESOURCE;
    }

    @Override
    public boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        State state = STATES.get(player.getUUID());
        return ticks >= chargeTime(data) && resolve(player.serverLevel(), state) != null;
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (!canRelease(player, data, ticks)) return false;
        State state = STATES.remove(player.getUUID());
        PlasmaOrbEntity orb = resolve(player.serverLevel(), state);
        if (orb == null) return false;

        float exp = data.getProficiency(getId());
        Vec3 destination = lookingDestination(player, 100);
        if (!orb.arm(destination, DynamicSkillRules.damage(getId(), lerpf(80, 150, exp)), lerpf(12, 15, exp))) {
            orb.discard();
            return false;
        }
        DynamicSkillRules.addExp(player, data, getId(), .008f);
        return true;
    }

    @Override public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        tryRelease(player, data, ticks);
    }

    @Override
    public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {
        discard(player.serverLevel(), STATES.remove(player.getUUID()));
    }

    @Override public void execute(ServerPlayer player, PlayerAbilityData data) {}
    @Override public int getCooldownTicks(float proficiency) { return (int) lerpf(1000, 600, proficiency); }

    private int chargeTime(PlayerAbilityData data) {
        return (int) lerpf(60, 30, data.getProficiency(getId()));
    }

    private float overloadToKeep(PlayerAbilityData data) {
        return lerpf(500, 400, data.getProficiency(getId()));
    }

    private static PlasmaOrbEntity resolve(ServerLevel level, State state) {
        if (state == null) return null;
        Entity entity = level.getEntity(state.orbId());
        return entity instanceof PlasmaOrbEntity orb && orb.isAlive() ? orb : null;
    }

    private static void discard(ServerLevel level, State state) {
        PlasmaOrbEntity orb = resolve(level, state);
        if (orb != null) orb.discard();
    }

    /** Equivalent to Raytrace.getLookingPos(player, 100, EntitySelectors.living). */
    private static Vec3 lookingDestination(ServerPlayer player, double range) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        HitResult blockHit = player.pick(range, 0, false);
        double nearest = blockHit.getType() == HitResult.Type.MISS
                ? range : eye.distanceTo(blockHit.getLocation());
        Vec3 destination = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

        for (Entity entity : level.getEntities(player, new AABB(eye, end).inflate(1),
                entity -> (entity instanceof LivingEntity || entity instanceof EnderDragonPart)
                        && entity.isAlive() && entity.isPickable())) {
            var clipped = entity.getBoundingBox().inflate(.3).clip(eye, end);
            if (clipped.isPresent()) {
                double distance = eye.distanceTo(clipped.get());
                if (distance < nearest) {
                    nearest = distance;
                    // Raytrace.getLookingPos used the entity position plus
                    // 60% of its eye height after selecting the intersected
                    // entity; the box intercept itself was not the endpoint.
                    destination = entity.position().add(0, entity.getEyeHeight() * .6, 0);
                }
            }
        }
        return destination;
    }
}
