package com.mohistmc.academy.skill.ability.meltdowner;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.teleporter.TeleportDestinations;
import com.mohistmc.academy.skill.passive.PassiveDamageHelper;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-authoritative reproduction of the legacy client-driven Jet Engine phase. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class JetEngineRuntime {
    static final int TRAVEL_DIVISOR = 8;
    static final int LIFETIME_TICKS = 16;

    private record State(Vec3 start, Vec3 target, float damage, int tick, float priorWalkSpeed) {}
    private static final Map<UUID, State> ACTIVE = new ConcurrentHashMap<>();

    private JetEngineRuntime() {}

    static void start(ServerPlayer player, Vec3 target, float damage) {
        State previous = ACTIVE.get(player.getUUID());
        float prior = previous == null ? player.getAbilities().getWalkingSpeed() : previous.priorWalkSpeed;
        ACTIVE.put(player.getUUID(), new State(player.position(), target, damage, 0, prior));
        if (player.isPassenger()) player.stopRiding();
        player.getAbilities().setWalkingSpeed(.07f);
        player.onUpdateAbilities();
    }

    private static void stop(ServerPlayer player, boolean cancelMotion) {
        State state = ACTIVE.remove(player.getUUID());
        if (state == null) return;
        player.getAbilities().setWalkingSpeed(state.priorWalkSpeed);
        player.onUpdateAbilities();
        if (cancelMotion) player.setDeltaMovement(Vec3.ZERO);
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        State state = ACTIVE.get(player.getUUID());
        if (state == null) return;
        if (!player.isAlive() || player.isRemoved() || AbilityInterferenceService.isInterfered(player)) {
            stop(player, true);
            return;
        }
        if (player.isPassenger()) player.stopRiding();

        int next = state.tick + 1;
        Vec3 velocity = state.target.subtract(state.start).scale(1d / TRAVEL_DIVISOR);
        // VecUtils.lerp was intentionally not clamped. The legacy context
        // continued through its terminating tick and ended at factor 16/8.
        Vec3 wanted = state.start.add(velocity.scale(next));
        Vec3 before = player.position();
        Vec3 movement = wanted.subtract(before);
        if (!canTravel(player, wanted, movement)) {
            stop(player, true);
            return;
        }

        player.teleportTo(wanted.x, wanted.y, wanted.z);
        player.setDeltaMovement(velocity);
        player.fallDistance = 0;

        LivingEntity target = firstLiving(player, before, wanted);
        if (target != null) {
            PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            PassiveDamageHelper.meltdownerAttack(player, data, target, "jet_engine", state.damage);
        }

        // Damage hooks can synchronously terminate or replace this context (death/dimension change).
        // Never emit its remaining effects or resurrect it after a lifecycle callback removed it.
        if (ACTIVE.get(player.getUUID()) != state) return;

        EffectHelper.jetMesh(player.serverLevel(), before, wanted);
        EffectHelper.meltdownBurst(player.serverLevel(), player.getX(), player.getY() + .8,
                player.getZ(), 11, .3);

        if (next >= LIFETIME_TICKS) {
            stop(player, false);
        } else {
            ACTIVE.put(player.getUUID(), new State(state.start, state.target,
                    state.damage, next, state.priorWalkSpeed));
        }
    }

    private static boolean canTravel(ServerPlayer player, Vec3 wanted, Vec3 movement) {
        var level = player.serverLevel();
        if (!TeleportDestinations.isWithinBounds(player, level, wanted)) return false;
        AABB box = player.getBoundingBox();
        AABB swept = box.expandTowards(movement);
        // Also require neighboring chunks consulted for protruding block shapes. Refuse an
        // unexpected long jump instead of letting a changed player position trigger an unbounded scan.
        if (swept.getXsize() > 32 || swept.getZsize() > 32
                || !level.hasChunksAt(BlockPos.containing(swept.minX - 1, swept.minY - 1, swept.minZ - 1),
                        BlockPos.containing(swept.maxX + 1, swept.maxY + 1, swept.maxZ + 1))) return false;
        Vec3 center = box.getCenter();
        Vec3 end = center.add(movement);
        // Sweep the player's full box along the straight teleport segment against each voxel box.
        // The tiny inset allows contact with a floor without treating it as penetration.
        for (var shape : level.getBlockCollisions(player, swept)) {
            for (AABB solid : shape.toAabbs()) {
                AABB obstacle = solid.inflate(box.getXsize() / 2 - 1.0E-7,
                        box.getYsize() / 2 - 1.0E-7, box.getZsize() / 2 - 1.0E-7);
                if (obstacle.contains(center) || obstacle.clip(center, end).isPresent()) return false;
            }
        }
        return level.noCollision(player, box.move(movement));
    }

    private static LivingEntity firstLiving(ServerPlayer player, Vec3 from, Vec3 to) {
        HitResult block = player.serverLevel().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double best = block.getType() == HitResult.Type.MISS
                ? from.distanceToSqr(to) : from.distanceToSqr(block.getLocation());
        LivingEntity nearest = null;
        for (LivingEntity candidate : player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                new AABB(from, to).inflate(1), candidate -> candidate != player && candidate.isAlive())) {
            var intercept = candidate.getBoundingBox().inflate(.3).clip(from, to);
            if (intercept.isEmpty()) continue;
            double distance = from.distanceToSqr(intercept.get());
            if (distance <= best) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player, true);
    }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player, true);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) stop(player, true);
    }
    public static void onConfirmedDeath(net.minecraft.world.entity.LivingEntity entity) {
        if (entity instanceof ServerPlayer player) stop(player, true);
    }
    @SubscribeEvent public static void serverStopping(ServerStoppingEvent event) {
        // stopServer saves players before its asynchronous disconnect callbacks finish.
        // Restore only active contexts, using each player's captured pre-skill speed.
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            stop(player, true);
        }
    }
    @SubscribeEvent public static void serverStopped(ServerStoppedEvent event) { ACTIVE.clear(); }
}
