package com.mohistmc.academy.skill.ability.teleporter;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademyParticles;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** Final 1.12.2 wall scanner: hold a marker, tune distance with the wheel, then release. */
public final class PenetrateTeleportEffect implements ChargingSkillEffect {
    private record Destination(Vec3 position, boolean available) {}

    @Override public String getId() { return "penetrate_teleport"; }
    @Override public boolean appliesBaseResourceCost() { return false; }
    @Override public boolean grantsActivationProficiency() { return false; }
    @Override public int getMinChargeTicks() { return 0; }
    @Override public int getMaxChargeTicks() { return 1; }
    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) { return Integer.MAX_VALUE; }

    @Override public boolean canStartCharging(ServerPlayer player, PlayerAbilityData data) {
        return DynamicSkillRules.enabled(getId());
    }
    @Override public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {}

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return true;
    }

    @Override public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return TickResult.CONTINUE;
    }
    @Override public boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        Destination destination = destination(player, data);
        BlockPos pos = BlockPos.containing(destination.position);
        return destination.available && player.serverLevel().hasChunkAt(pos)
                && player.serverLevel().getWorldBorder().isWithinBounds(pos);
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return tryRelease(player, data, ticks, Float.NaN);
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks, float releaseValue) {
        float exp = data.getProficiency(getId());
        Destination destination = destination(player, data, releaseValue);
        if (!destination.available) return false;
        BlockPos pos = BlockPos.containing(destination.position);
        if (!player.serverLevel().hasChunkAt(pos)
                || !player.serverLevel().getWorldBorder().isWithinBounds(pos)) return false;
        double distance = player.position().distanceTo(destination.position);
        if (!DynamicSkillRules.payForced(data, getId(),
                (float) (distance * lerpf(14, 9, exp)), lerpf(80, 50, exp))) return false;

        ServerLevel level = player.serverLevel();
        TeleportSkillHelper.teleport(player, destination.position);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                AcademySounds.TP_TP, SoundSource.PLAYERS, .5f, 1f);
        DynamicSkillRules.addExp(player, data, getId(), (float) (.00014 * distance));
        return true;
    }

    @Override public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        tryRelease(player, data, ticks);
    }
    @Override public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {}
    @Override public void execute(ServerPlayer player, PlayerAbilityData data) {}
    @Override public int getCooldownTicks(float proficiency) { return (int) lerpf(50, 30, proficiency); }

    private Destination destination(ServerPlayer player, PlayerAbilityData data) {
        return destination(player, data, Float.NaN);
    }

    private Destination destination(ServerPlayer player, PlayerAbilityData data, float requestedDistance) {
        float exp = data.getProficiency(getId());
        double perBlock = Math.max(.0001, DynamicSkillRules.cp(getId(), lerpf(14, 9, exp)));
        double maxDistance = lerpf(10, 35, exp);
        if (!data.isDevMode()) maxDistance = Math.min(maxDistance, data.getCurrentCp() / perBlock);
        if (Float.isFinite(requestedDistance)) maxDistance = Math.max(0, Math.min(requestedDistance, maxDistance));
        final double step = .8;
        int stage = 0, counter = 0;
        double travelled = 0;
        Vec3 direction = player.getLookAngle().normalize();
        // The final 1.12.2 scanner and actual destination use player.posY
        // (feet); its client marker alone is lifted by eye height.
        Vec3 cursor = player.position();
        while (travelled <= maxDistance) {
            boolean free = hasPlace(player.serverLevel(), cursor);
            if (stage == 0) {
                if (!free) stage = 1;
            } else if (stage == 1) {
                if (free) stage = 2;
            } else if (!free || ++counter > 4) {
                break;
            }
            travelled += step;
            cursor = cursor.add(direction.scale(step));
        }
        return new Destination(cursor, stage != 1);
    }

    private static boolean hasPlace(ServerLevel level, Vec3 point) {
        BlockPos feet = new BlockPos((int) point.x, (int) point.y, (int) point.z);
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }
}
