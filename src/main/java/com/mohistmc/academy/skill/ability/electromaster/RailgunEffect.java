package com.mohistmc.academy.skill.ability.electromaster;
import com.mohistmc.academy.skill.AcademyDamageHelper;

import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.world.effect.EffectHelper;
import com.mohistmc.academy.entity.RailgunBeamEntity;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.passive.PassiveSkillEventHandler;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.entity.CoinEntity;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

public class RailgunEffect implements ChargingSkillEffect {

    private static final int CHARGE_TICKS = 20;
    private static final double BEAM_RANGE = 45.0;
    private static final double MAX_INCREMENT = 50.0;
    private static final double RADIUS = 2.0;
    private static final double REFLECT_RANGE = 15.0;
    private static final Map<UUID, ShotSource> SOURCES = new HashMap<>();

    private enum ShotSource { COIN, ITEM }

    @Override
    public String getId() {
        return "railgun";
    }
    @Override public boolean appliesBaseResourceCost(){return false;}
    @Override public boolean grantsActivationProficiency(){return false;}

    @Override
    public int getMinChargeTicks() {
        // Coin QTE is released immediately; item shots enforce 20 ticks below.
        return 0;
    }

    @Override
    public int getMaxChargeTicks() {
        return CHARGE_TICKS;
    }

    @Override
    public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        CoinEntity coin = CoinEntity.getPlayerCoinInAir(player);
        boolean source = coin != null && coin.isInRailgunWindow(player)
                || isAcceptedItem(player.getMainHandItem());
        return source && DynamicSkillRules.canPay(data, getId(),
                lerpf(200, 450, exp), lerpf(180, 120, exp));
    }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        CoinEntity coin = CoinEntity.getPlayerCoinInAir(player);
        if (coin != null && coin.isInRailgunWindow(player)) {
            SOURCES.put(player.getUUID(), ShotSource.COIN);
        } else if (isAcceptedItem(player.getMainHandItem())) {
            SOURCES.put(player.getUUID(), ShotSource.ITEM);
        } else {
            SOURCES.remove(player.getUUID());
        }
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        ShotSource source = SOURCES.get(player.getUUID());
        CoinEntity coin = CoinEntity.getPlayerCoinInAir(player);
        return source == ShotSource.COIN
                ? coin != null && coin.isInRailgunWindow(player)
                : source == ShotSource.ITEM && isAcceptedItem(player.getMainHandItem());
    }

    @Override
    public boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float cp = lerpf(200, 450, data.getProficiency(getId()));
        return ChargingSkillEffect.super.canRelease(player, data, ticks)
                && sourceIsValid(player, ticks)
                && DynamicSkillRules.canPay(data, getId(), cp,
                        lerpf(180, 120, data.getProficiency(getId())));
    }

    private boolean sourceIsValid(ServerPlayer player, int ticks) {
        ShotSource source = SOURCES.get(player.getUUID());
        CoinEntity coin = CoinEntity.getPlayerCoinInAir(player);
        if (source == ShotSource.COIN) return coin != null && coin.isInRailgunWindow(player);
        return source == ShotSource.ITEM && ticks >= CHARGE_TICKS && isAcceptedItem(player.getMainHandItem());
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (!onChargingTick(player, data, ticks)) return TickResult.ABORT_RESOURCE;
        // The thrown-coin QTE fired on the railgun key-down in 1.0.7; only
        // the item-ammunition branch owns the twenty-tick hold.
        return SOURCES.get(player.getUUID()) == ShotSource.COIN
                ? TickResult.RELEASE : TickResult.CONTINUE;
    }

    @Override
    public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        tryRelease(player, data, ticks);
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (!canRelease(player, data, ticks)) return false;
        float exp = data.getProficiency(getId());
        float cp = lerpf(200, 450, exp);

        float overload = lerpf(180, 120, exp);
        if (!DynamicSkillRules.canPay(data, getId(), cp, overload)) return false;

        ShotSource source = SOURCES.remove(player.getUUID());
        CoinEntity coin = CoinEntity.getPlayerCoinInAir(player);
        // Ammunition commit precedes resource settlement so a failed source cannot charge CP/OL.
        if (source == ShotSource.COIN) {
            if (coin == null || !coin.consumeForRailgun(player)) return false;
        } else if (source == ShotSource.ITEM) {
            ItemStack held = player.getMainHandItem();
            if (ticks < CHARGE_TICKS || !isAcceptedItem(held)) return false;
            if (!player.getAbilities().instabuild) held.shrink(1);
        } else {
            return false;
        }

        if (!DynamicSkillRules.tryPay(data,getId(),cp,overload)) return false;

        performRailgun(player, data, exp);
        return true;
    }

    @Override
    public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {
        SOURCES.remove(player.getUUID());
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
    }

    private static boolean isAcceptedItem(ItemStack stack) {
        return stack.is(Items.IRON_INGOT) || stack.is(Items.IRON_BLOCK);
    }

    private void performRailgun(ServerPlayer player, PlayerAbilityData data, float exp) {
        ServerLevel level = player.serverLevel();
        float damage = lerpf(60, 110, exp);
        double energy = lerpf(900, 2000, exp);

        Vec3 lookVec = player.getLookAngle();
        Vec3 eyePos = player.getEyePosition(0);
        Vec3 rightVec = lookVec.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 visualStart = eyePos.add(rightVec.scale(0.3))
                .add(0, -0.2, 0)
                .add(lookVec.scale(0.3));
        // RangedRayDamage starts 0.1 blocks along the player's eye ray. Keep
        // the hand offset presentation separate from authoritative targeting.
        Vec3 rayStart = eyePos.add(lookVec.scale(0.1));

        Vec3 end = rayStart.add(lookVec.scale(MAX_INCREMENT));
        List<LivingEntity> targets = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
                new AABB(rayStart, end).inflate(RADIUS * 1.2), e -> e != player && e.isAlive()));
        targets.removeIf(e -> perpendicularDistance(rayStart, lookVec, e.position()) >= RADIUS * 1.2
                || firstIntersection(rayStart, end, e.getBoundingBox().inflate(RADIUS)).isEmpty());
        targets.sort(Comparator.comparingDouble(e -> firstIntersection(rayStart, end, e.getBoundingBox().inflate(RADIUS))
                .map(p -> p.distanceTo(rayStart)).orElse(Double.MAX_VALUE)));
        boolean hitEntity = false;
        boolean mayDestroyBlocks = DynamicSkillRules.destroysBlocks(level, getId());
        // One authoritative reachable distance governs blocks, entity damage and FX.
        // A canceled/unbreakable/energy-exhausting central traversal is a hard barrier.
        double stopDistance = mayDestroyBlocks
                ? traceBarrier(level, player, rayStart, lookVec, MAX_INCREMENT, energy)
                : MAX_INCREMENT;
        for (LivingEntity target : targets) {
            var intersection = firstIntersection(rayStart, rayStart.add(lookVec.scale(stopDistance)), target.getBoundingBox().inflate(RADIUS));
            if (intersection.isEmpty()) continue;
            double distance = intersection.get().distanceTo(rayStart);
            // 1.0.7 RangedRayDamage attenuates by perpendicular displacement, not along-distance.
            float attenuated = damage * lerpf(1.0f, 0.2f,
                    (float)Math.min(1D, perpendicularDistance(rayStart, lookVec, target.position()) / MAX_INCREMENT));
            hitEntity = true;
            if (target instanceof ServerPlayer reflector
                    && PassiveSkillEventHandler.reflectSpecialRay(reflector, player, attenuated)) {
                stopDistance = distance;
                reflectDamage(reflector);
                break;
            }
            AcademyDamageHelper.hurt(player,target,player.damageSources().playerAttack(player), DynamicSkillRules.damage(getId(),attenuated));
            EffectHelper.glowBurst(level, target.getX(), target.getEyeY(), target.getZ(),
                    3, 0.3f, 0x88FFCC44, 10, 0.2f);
        }

        RailgunBeamEntity beam = new RailgunBeamEntity(AcademyEntities.RAILGUN_BEAM.get(), level);
        beam.setPos(visualStart.x, visualStart.y, visualStart.z);
        beam.setBeam(visualStart, lookVec, Math.min(BEAM_RANGE, stopDistance));
        level.addFreshEntity(beam);

        // Legacy radius-two beam is a bundle of independently depleted energy lines.
        Vec3 up = Math.abs(lookVec.y) > .95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = lookVec.cross(up).normalize();
        Vec3 vertical = right.cross(lookVec).normalize();
        List<Vec3> origins = new ArrayList<>();
        for (double x = -RADIUS; x <= RADIUS; x += .9) for (double y = -RADIUS; y <= RADIUS; y += .9)
            if (x * x + y * y <= RADIUS * RADIUS) origins.add(rayStart.add(right.scale(x)).add(vertical.scale(y)));
        double lineEnergy = energy / Math.max(1, origins.size());
        if (mayDestroyBlocks)
            for (Vec3 origin : origins) destroyLine(level, player, origin, lookVec, stopDistance, lineEnergy);

        AcademySounds.playSound(level, player.getX(), player.getY(), player.getZ(),
                AcademySounds.EM_RAILGUN, SoundSource.PLAYERS, 0.5f, 1.0f);

        if (hitEntity) {
            DynamicSkillRules.addExp(player,data, getId(), 0.01f);
        } else {
            DynamicSkillRules.addExp(player,data, getId(), 0.005f);
        }
    }

    private static double along(Vec3 start, Vec3 direction, Vec3 point) { return point.subtract(start).dot(direction); }
    private static double perpendicularDistance(Vec3 start, Vec3 direction, Vec3 point) {
        Vec3 delta = point.subtract(start); return delta.subtract(direction.scale(delta.dot(direction))).length();
    }

    static java.util.Optional<Vec3> firstIntersection(Vec3 start, Vec3 end, AABB box) {
        if (box.contains(start)) return java.util.Optional.of(start);
        return box.clip(start, end);
    }

    public static double traceBarrier(ServerLevel level, ServerPlayer player, Vec3 origin, Vec3 direction,
                                       double maxDistance, double energy) {
        BlockPos previous = null;
        for (double d = 0; d <= maxDistance && energy > 0; d += .2) {
            BlockPos pos = BlockPos.containing(origin.add(direction.scale(d)));
            if (pos.equals(previous)) continue;
            previous = pos;
            var state = level.getBlockState(pos);
            if (state.isAir()) continue;
            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0 || state.is(Blocks.BEDROCK) || energy < hardness) return Math.max(0, d - .01);
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCanceled()) return Math.max(0, d - .01);
            energy -= Math.max(0, hardness);
        }
        return maxDistance;
    }

    private static void destroyLine(ServerLevel level, ServerPlayer player, Vec3 origin, Vec3 direction,
                                    double maxDistance, double energy) {
        BlockPos previous = null;
        for (double d = 0; d <= maxDistance && energy > 0; d += .45) {
            BlockPos pos = BlockPos.containing(origin.add(direction.scale(d)));
            if (pos.equals(previous)) continue;
            previous = pos;
            var state = level.getBlockState(pos);
            if (state.isAir()) continue;
            float hardness = state.getDestroySpeed(level, pos);
            if (hardness < 0 || state.is(Blocks.BEDROCK) || energy < hardness) break;
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
            NeoForge.EVENT_BUS.post(event);
            if (event.isCanceled()) break;
            level.destroyBlock(pos, level.random.nextFloat() < .05f, player);
            energy -= Math.max(0, hardness);
            if (level.random.nextFloat() < .05f) {
                BlockPos neighbor = pos.relative(net.minecraft.core.Direction.getRandom(level.random));
                var neighborState = level.getBlockState(neighbor);
                float neighborHardness = neighborState.getDestroySpeed(level, neighbor);
                if (!neighborState.isAir() && neighborHardness >= 0 && energy >= neighborHardness) {
                    BlockEvent.BreakEvent neighborEvent = new BlockEvent.BreakEvent(level, neighbor, neighborState, player);
                    NeoForge.EVENT_BUS.post(neighborEvent);
                    if (!neighborEvent.isCanceled()) { level.destroyBlock(neighbor, level.random.nextFloat() < .05f, player); energy -= neighborHardness; }
                }
            }
        }
    }

    private void reflectDamage(ServerPlayer reflector) {
        ServerLevel level = reflector.serverLevel();
        Vec3 start = reflector.getEyePosition(), direction = reflector.getLookAngle().normalize(), end = start.add(direction.scale(REFLECT_RANGE));
        LivingEntity best = null; double bestDistance = Double.MAX_VALUE;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(.8),
                e -> e != reflector && e.isAlive())) {
            var clip = target.getBoundingBox().inflate(.3).clip(start, end);
            if (clip.isPresent() && start.distanceToSqr(clip.get()) < bestDistance) { bestDistance = start.distanceToSqr(clip.get()); best = target; }
        }
        if (best != null) PassiveSkillEventHandler.reflectedDamage(reflector, best, 14.0f);
        // Railgun.hReflectClient in 1.0.7 always emitted a second, 15-block
        // beam beginning at the reflector and following their look direction.
        RailgunBeamEntity reflectedBeam = new RailgunBeamEntity(AcademyEntities.RAILGUN_BEAM.get(), level);
        reflectedBeam.setPos(start.x, start.y, start.z);
        reflectedBeam.setBeam(start, direction, REFLECT_RANGE);
        level.addFreshEntity(reflectedBeam);
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return (int) lerpf(300, 160, proficiency);
    }
}

