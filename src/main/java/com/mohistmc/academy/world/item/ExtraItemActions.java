package com.mohistmc.academy.world.item;

import java.util.Comparator;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class ExtraItemActions {
    private ExtraItemActions() {}

    static boolean has(Player player, Item item) {
        if (player.getAbilities().instabuild) return true;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
            if (player.getInventory().getItem(i).is(item)) return true;
        return player.getOffhandItem().is(item);
    }

    static boolean consumeOne(Player player, Item item) {
        if (player.getAbilities().instabuild) return true;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                stack.shrink(1);
                return true;
            }
        }
        if (player.getOffhandItem().is(item)) {
            player.getOffhandItem().shrink(1);
            return true;
        }
        return false;
    }

    static LivingEntity firstLivingOnRay(ServerPlayer player, double range, double radius) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().normalize().scale(range));
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class,
                        new AABB(start, end).inflate(radius), entity -> entity != player && entity.isAlive())
                .stream()
                .filter(entity -> entity.getBoundingBox().inflate(radius).clip(start, end).isPresent())
                .min(Comparator.comparingDouble(entity -> entity.getBoundingBox().inflate(radius)
                        .clip(start, end).orElse(end).distanceToSqr(start)))
                .orElse(null);
    }

    static void beam(ServerLevel level, Vec3 start, Vec3 direction, double length) {
        Vec3 unit = direction.normalize();
        for (double distance = 0.25; distance <= length; distance += 0.45) {
            Vec3 point = start.add(unit.scale(distance));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z,
                    1, 0, 0, 0, 0);
        }
    }
}
