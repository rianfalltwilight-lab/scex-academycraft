package com.mohistmc.academy.skill.ability.teleporter;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shared client-preview/server-execution reconstruction of 1.0.7 Flashing#getDest.
 * It intentionally permits mid-air destinations; the old skill's forty-tick
 * gravity canceller exists specifically to make that aerial movement playable.
 */
public final class FlashingTargeting {
    private FlashingTargeting() {}

    /** Exact 1.0.7 A/D/W/S basis: pitch affects forward/back, never left/right. */
    public static Vec3 direction(Player player, int key) {
        if (player == null || key < 0 || key > 3) return Vec3.ZERO;
        Vec3 forward = player.getLookAngle();
        double yaw = Math.toRadians(player.getYRot());
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        return switch (key) {
            case 0 -> right.scale(-1);
            case 1 -> right;
            case 2 -> forward;
            default -> forward.scale(-1);
        };
    }

    public static Vec3 destination(Player player, Vec3 rawDirection, double distance) {
        return player == null ? null : destination(player, player.getEyePosition(), rawDirection, distance);
    }

    /** Variant used by interpolated client markers; execution still recomputes on the server. */
    public static Vec3 destination(Player player, Vec3 start, Vec3 rawDirection, double distance) {
        if (player == null || rawDirection == null || !Double.isFinite(distance) || distance <= 0
                || rawDirection.lengthSqr() < 1.0e-8) return null;
        Level level = player.level();
        if (start == null || !finite(start)) return null;
        Vec3 end = start.add(rawDirection.normalize().scale(distance));

        BlockHitResult blockHit = level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double blockDistance = blockHit.getType() == HitResult.Type.MISS
                ? Double.POSITIVE_INFINITY : start.distanceToSqr(blockHit.getLocation());

        Entity entityHit = closestLiving(player, start, end, blockDistance);
        Vec3 result;
        if (entityHit != null) {
            // 1.0.7's MovingObjectPosition(Entity) stores entity position rather
            // than the bounding-box intercept before adding getEyeHeight().
            result = entityHit.position().add(0, entityHit.getEyeHeight(), 0);
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            result = offsetFromBlock(level, blockHit);
        } else {
            // Motion3D(player, true) starts at eye height in the fixed baseline.
            result = end;
        }

        if (!finite(result)) return null;
        BlockPos destination = BlockPos.containing(result);
        // Keep the legacy coordinates without force-loading chunks or crossing
        // the world border. These are transport boundaries, not floor checks.
        if (!level.hasChunkAt(destination) || !level.getWorldBorder().isWithinBounds(destination)) return null;
        return result;
    }

    private static Entity closestLiving(Player player, Vec3 start, Vec3 end, double blockDistance) {
        List<Entity> entities = player.level().getEntities(player,
                new AABB(start, end).inflate(1), entity -> entity != player && entity.isAlive()
                        && entity.isPickable()
                        && (entity instanceof LivingEntity || entity instanceof EnderDragonPart));
        Entity closest = null;
        double closestDistance = blockDistance;
        for (Entity entity : entities) {
            var intercept = entity.getBoundingBox().inflate(.3).clip(start, end);
            if (intercept.isEmpty()) continue;
            // LambdaLib chose the nearest intercept, then compared the entity's
            // stored position against the block hit. Preserve the observable
            // nearest-hit ordering without trusting a client-supplied target.
            double hitDistance = start.distanceToSqr(intercept.get());
            if (hitDistance <= closestDistance) {
                closestDistance = hitDistance;
                closest = entity;
            }
        }
        return closest;
    }

    private static Vec3 offsetFromBlock(Level level, BlockHitResult hit) {
        Vec3 location = hit.getLocation();
        double x = location.x, y = location.y, z = location.z;
        Direction face = hit.getDirection();
        switch (face) {
            case DOWN -> y -= 1.0;
            case UP -> y += 1.8;
            case NORTH -> { z -= .6; y = hit.getBlockPos().getY() + 1.7; }
            case SOUTH -> { z += .6; y = hit.getBlockPos().getY() + 1.7; }
            case WEST -> { x -= .6; y = hit.getBlockPos().getY() + 1.7; }
            case EAST -> { x += .6; y = hit.getBlockPos().getY() + 1.7; }
        }
        if (face.getAxis().isHorizontal()) {
            // Java's cast-to-int truncation is deliberate: this matches the
            // exact 1.0.7 head-clearance check, including negative coordinates.
            BlockPos head = new BlockPos((int) x, (int) (y + 1), (int) z);
            if (level.hasChunkAt(head) && !level.getBlockState(head).isAir()) y -= 1.25;
        }
        return new Vec3(x, y, z);
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y)
                && Double.isFinite(value.z);
    }
}
