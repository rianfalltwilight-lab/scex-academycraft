package com.mohistmc.academy.skill.ability.teleporter;

import com.mohistmc.academy.skill.PlayerAbilityData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-side invariants shared by the 1.0.7 teleporter ports. */
final class TeleportSkillHelper {
    private TeleportSkillHelper() {}

    static boolean consume(PlayerAbilityData data, String skillId, float cp, float overload) {
        return com.mohistmc.academy.config.DynamicSkillRules.tryPay(data,skillId,cp,overload);
    }

    static boolean safe(ServerPlayer player, Vec3 feet) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = BlockPos.containing(feet);
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        AABB moved = player.getBoundingBox().move(feet.subtract(player.position()));
        return level.noCollision(player, moved) && !level.getBlockState(pos.below()).isAir();
    }

    static Vec3 furthestSafe(ServerPlayer player, Vec3 start, Vec3 direction, double maxDistance) {
        Vec3 result = null;
        for (double distance = 0.5; distance <= maxDistance; distance += 0.5) {
            Vec3 candidate = start.add(direction.scale(distance));
            Vec3 feet = candidate.subtract(0, player.getEyeHeight(), 0);
            if (safe(player, feet)) result = feet;
        }
        return result;
    }

    static void teleport(ServerPlayer player, Vec3 feet) {
        if (player.isPassenger()) player.stopRiding();
        player.teleportTo(feet.x, feet.y, feet.z);
        com.mohistmc.academy.advancement.LegacyAdvancementBridge.teleported(player);
        player.fallDistance = 0;
    }
}
