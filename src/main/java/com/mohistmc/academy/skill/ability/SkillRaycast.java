package com.mohistmc.academy.skill.ability;

import com.mohistmc.academy.skill.AcademyDamageHelper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-owned intersections against actual entity boxes, clipped by solid block shapes. */
public final class SkillRaycast {
    private static final double EPSILON = 1.0e-9;
    private SkillRaycast() {}

    public record Hit<T extends Entity>(T entity, Vec3 location, double distanceSquared) {}

    public record Trace<T extends Entity>(Vec3 end, List<Hit<T>> hits) {
        public T firstEntity() { return hits.isEmpty() ? null : hits.getFirst().entity(); }
        public Vec3 firstImpact() { return hits.isEmpty() ? end : hits.getFirst().location(); }
    }

    public static Trace<LivingEntity> trace(ServerPlayer player, Vec3 from, Vec3 intendedEnd) {
        return traceEntities(player, LivingEntity.class, from, intendedEnd,
                entity -> entity.isPickable() && !player.isAlliedTo(entity)
                        && AcademyDamageHelper.allowsTarget(entity));
    }

    /** Item transport and damaging skills share the same block and exact-box intersection policy. */
    public static <T extends Entity> Trace<T> traceEntities(ServerPlayer player, Class<T> type,
            Vec3 from, Vec3 intendedEnd, Predicate<? super T> eligible) {
        var level = player.serverLevel();
        var block = level.clip(new ClipContext(from, intendedEnd, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        boolean blocked = block.getType() == HitResult.Type.BLOCK;
        Vec3 end = blocked ? block.getLocation() : intendedEnd;
        double limit = from.distanceToSqr(end);
        var hits = new ArrayList<Hit<T>>();
        // Inflation belongs only to the broad-phase query. Inflating the actual hitbox
        // makes a target behind a thin wall appear to extend in front of that wall.
        for (T candidate : level.getEntitiesOfClass(type,
                new AABB(from, end).inflate(1.0e-7), entity -> entity != player
                        && entity.isAlive() && eligible.test(entity))) {
            AABB box = candidate.getBoundingBox();
            Vec3 intersection = box.contains(from) ? from : box.clip(from, end).orElse(null);
            if (intersection == null) continue;
            double distance = from.distanceToSqr(intersection);
            // Blocks win ties. An entity whose real box starts at/behind the block is occluded.
            if (blocked ? distance >= limit - EPSILON : distance > limit + EPSILON) continue;
            hits.add(new Hit<>(candidate, intersection, distance));
        }
        hits.sort(Comparator.<Hit<T>>comparingDouble(Hit::distanceSquared)
                .thenComparingInt(hit -> hit.entity().getId()));
        return new Trace<>(end, List.copyOf(hits));
    }

    /** Melee requires both entities in the same level and an unobstructed eye-to-eye segment. */
    public static boolean hasClearPath(LivingEntity from, LivingEntity to) {
        return from.level() == to.level() && from.level().clip(new ClipContext(
                from.getEyePosition(), to.getEyePosition(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, from)).getType() == HitResult.Type.MISS;
    }
}
