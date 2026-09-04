package com.mohistmc.academy.skill.ability.electromaster;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 电磁牵引 —— 将玩家拉向准星对准的金属方块或实体 */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public class MagMovementEffect implements ChargingSkillEffect {

    private static final double ACCEL = 0.08;
    private static final double VELOCITY = 1.0;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private record Session(Vec3 fixedTarget, UUID entityTarget, Vec3 start, String dimension,
                           float overloadFloor) {
        Vec3 resolve(ServerLevel level) {
            if (entityTarget == null) return fixedTarget;
            Entity entity = level.getEntity(entityTarget);
            return entity != null && entity.isAlive() ? entity.getEyePosition() : null;
        }
    }

    @Override
    public String getId() {
        return "mag_movement";
    }
    @Override public boolean appliesBaseResourceCost(){return false;}
    @Override public boolean grantsActivationProficiency(){return false;}

    @Override public int getMinChargeTicks() { return 1; }
    @Override public int getMaxChargeTicks() { return 1; }
    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) { return Integer.MAX_VALUE; }
    @Override public TickResult getSessionTimeoutResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return TickResult.ABORT_RESOURCE;
    }
    @Override public boolean canRelease(ServerPlayer player,PlayerAbilityData data,int ticks){return ticks>=1&&SESSIONS.containsKey(player.getUUID());}

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        return findTarget(player, 25.0, exp) != null
                && DynamicSkillRules.canPay(data, getId(), 0, lerpf(60, 30, exp));
    }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        HitResult hit = findTarget(player, 25.0, exp);
        if (hit == null) return;
        Vec3 target = hit instanceof BlockHitResult blockHit ? blockHit.getLocation() : null;
        UUID entityTarget = hit instanceof EntityHitResult entityHit ? entityHit.getEntity().getUUID() : null;
        if (!DynamicSkillRules.tryPay(data,getId(),0,lerpf(60,30,exp))) return;
        SESSIONS.put(player.getUUID(), new Session(target, entityTarget, player.position(),
                player.level().dimension().location().toString(), data.getCurrentOverload()));
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.dimension.equals(player.level().dimension().location().toString())) return false;
        Vec3 target = session.resolve(player.serverLevel());
        if (target == null) return false;
        if (!data.isDevMode() && data.getCurrentOverload() < session.overloadFloor) {
            data.setCurrentOverload(session.overloadFloor);
        }
        float cp = lerpf(15, 8, data.getProficiency(getId()));
        if (!DynamicSkillRules.tryPay(data,getId(),cp,0)) return false;
        moveTowards(player, target);
        if (ticks % 3 == 0) {
            EffectHelper.electricTether(player.serverLevel(), player.getEyePosition(), target, 3);
        }
        // MovementContext in 1.0.7 remains held after reaching the anchor and
        // continues paying CP until key-up or exhaustion. Reaching one block
        // from the target is not a synthetic successful release.
        return true;
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return TickResult.ABORT_RESOURCE;
        float cp = lerpf(15, 8, data.getProficiency(getId()));
        if (!DynamicSkillRules.canPay(data,getId(),cp,0)) return TickResult.ABORT_RESOURCE;
        return onChargingTick(player, data, ticks) ? TickResult.CONTINUE : TickResult.RELEASE;
    }

    @Override public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) { finish(player, data); }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (!canRelease(player, data, ticks)) return false;
        finish(player, data);
        return true;
    }

    @Override public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) { finish(player, data); }

    private void finish(ServerPlayer player, PlayerAbilityData data) {
        Session s = SESSIONS.remove(player.getUUID());
        player.fallDistance = 0;
        if (s != null && !data.isDevMode()) {
            double distance = s.start.distanceTo(player.position());
            DynamicSkillRules.addExp(player,data, getId(), (float) Math.max(0.005, 0.0011 * distance));
        }
        if (s != null) {
            com.mohistmc.academy.advancement.LegacyAdvancementBridge.award(
                    player, "electromaster/mag_movement");
        }
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        // Sustained skill: activation is handled through ChargingSkillEffect.
    }

    private void moveTowards(ServerPlayer player, Vec3 targetPos) {
        Vec3 playerPos = player.position();
        Vec3 dir = targetPos.subtract(playerPos);
        double dist = dir.length();

        if (dist < 1.0e-6) {
            // Avoid NaN at the exact anchor. At every non-zero distance the
            // old client normalized to velocity one and naturally oscillated.
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            return;
        }

        dir = dir.normalize();
        Vec3 currentMotion = player.getDeltaMovement();

        double newMx = tryAdjust(currentMotion.x, dir.x * VELOCITY);
        double newMy = tryAdjust(currentMotion.y, dir.y * VELOCITY);
        double newMz = tryAdjust(currentMotion.z, dir.z * VELOCITY);

        player.setDeltaMovement(newMx, newMy, newMz);
        player.hurtMarked = true;

    }

    /**
     * 尝试将值向目标调整一步（每次最多移动 ACCEL）
     */
    private double tryAdjust(double from, double to) {
        double d = to - from;
        if (Math.abs(d) < ACCEL) return to;
        return d > 0 ? from + ACCEL : from - ACCEL;
    }

    /**
     * 寻找前方可磁化的目标（方块优先）
     */
    private HitResult findTarget(ServerPlayer player, double range, float proficiency) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();

        BlockHitResult blockHit = (BlockHitResult) player.pick(range, 0, false);
        Vec3 entityEnd = blockHit != null && blockHit.getType() != HitResult.Type.MISS
                ? blockHit.getLocation() : eyePos.add(lookVec.scale(range));
        EntityHitResult entityHit = rayTraceEntities(player, eyePos, entityEnd);

        if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
            BlockState state = player.serverLevel().getBlockState(blockHit.getBlockPos());
            if (isMetalBlock(state, proficiency)) {
                // 如果实体更近，优先选择实体
                if (entityHit != null && isMetalEntity(entityHit.getEntity())) {
                    double blockDist = eyePos.distanceTo(blockHit.getLocation());
                    double entityDist = eyePos.distanceTo(entityHit.getLocation());
                    if (entityDist < blockDist) {
                        return entityHit;
                    }
                }
                return blockHit;
            }
        }

        if (entityHit != null && isMetalEntity(entityHit.getEntity())) {
            return entityHit;
        }

        return null;
    }

    private EntityHitResult rayTraceEntities(ServerPlayer player, Vec3 start, Vec3 end) {
        double range = start.distanceTo(end);
        Entity closest = null;
        Vec3 closestHit = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : player.level().getEntities(player,
                player.getBoundingBox().inflate(range),
                e -> e != player && e.isAlive() && e.isPickable())) {
            var result = entity.getBoundingBox().inflate(0.3).clip(start, end);
            if (result.isPresent()) {
                double distance = start.distanceToSqr(result.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = entity;
                    closestHit = result.get();
                }
            }
        }
        return closest == null ? null : new EntityHitResult(closest, closestHit);
    }

    /**
     * 判断方块是否为可用金属（低熟练度时只能吸附强金属方块）
     */
    private boolean isMetalBlock(BlockState state, float proficiency) {
        if (isStrongMetal(state)) return true;
        // 熟练度 >= 0.6 时可以使用弱金属
        if (proficiency >= 0.6f && isWeakMetal(state)) return true;
        return false;
    }

    private boolean isStrongMetal(BlockState state) {
        return ElectromasterMetalTargets.isNormal(state);
    }

    private boolean isWeakMetal(BlockState state) {
        return ElectromasterMetalTargets.isWeak(state);
    }

    private boolean isMetalEntity(Entity entity) {
        return ElectromasterMetalTargets.isMetalEntity(entity);
    }

    @Override public int getCooldownTicks(float proficiency) { return 0; }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { SESSIONS.clear(); }
}
