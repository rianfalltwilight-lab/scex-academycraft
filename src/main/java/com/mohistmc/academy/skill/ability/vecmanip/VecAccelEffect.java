package com.mohistmc.academy.skill.ability.vecmanip;

import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademySounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 矢量加速 —— 蓄力后向前方高速冲刺 */
public class VecAccelEffect implements ChargingSkillEffect {

    private static final int MAX_CHARGE = 20;
    private static final double MAX_VELOCITY = 2.5;

    @Override
    public String getId() {
        return "vec_accel";
    }
    @Override public boolean appliesBaseResourceCost(){return false;}
    @Override public boolean grantsActivationProficiency(){return false;}

    @Override
    public int getMinChargeTicks() {
        return 0;
    }

    @Override
    public int getMaxChargeTicks() {
        return MAX_CHARGE;
    }

    @Override public int getSessionTimeoutTicks(PlayerAbilityData data) { return Integer.MAX_VALUE; }

    @Override
    public void onChargingStart(ServerPlayer player, PlayerAbilityData data) {
        // 不预先消耗
    }

    @Override
    public boolean onChargingTick(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return true;
    }

    @Override
    public TickResult getTickResult(ServerPlayer player, PlayerAbilityData data, int ticks) {
        return TickResult.CONTINUE;
    }

    @Override
    public boolean canRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float exp = data.getProficiency(getId());
        float cp = lerpf(120, 80, exp);
        float overload = lerpf(30, 15, exp);
        return ticks >= 0 && (exp > 0.5f || hasGroundWithinTwoBlocks(player))
                && DynamicSkillRules.canPay(data, getId(), cp, overload);
    }

    @Override
    public void onChargingRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        float exp = data.getProficiency(getId());

        // 检查是否在地面（熟练度<=0.5时要求地面）
        boolean ignoreGround = exp > 0.5f;
        if (!ignoreGround && !hasGroundWithinTwoBlocks(player)) {
            return;
        }

        float cp = lerpf(120, 80, exp);
        float overload = lerpf(30, 15, exp);

        if (!DynamicSkillRules.tryPay(data,getId(),cp,overload)) return;

        int effectiveTicks = Math.min(ticks, MAX_CHARGE);
        double prog = Math.max(0, Math.min(1, effectiveTicks / (double) MAX_CHARGE));
        double speed = Math.sin(lerpf(0.4f, 1.0f, (float) prog)) * MAX_VELOCITY;

        // EntityLook(yaw, pitch - 10) in 1.0.7 deliberately lifts the launch
        // vector by ten degrees.  Subtracting from Y would invert that arc.
        Vec3 dir = Vec3.directionFromRotation(player.getXRot() - 10.0F, player.getYRot()).scale(speed);

        player.setDeltaMovement(dir);
        player.hurtMarked = true;
        player.fallDistance = 0;
        if (player.getVehicle() != null) {
            player.stopRiding();
        }

        DynamicSkillRules.addExp(player,data, getId(), 0.002f);

        ServerLevel level = player.serverLevel();
        AcademySounds.playSound(level, player.getX(), player.getY(), player.getZ(),
                AcademySounds.VM_VEC_ACCEL, SoundSource.PLAYERS, 0.35f, 1.0f);
    }

    @Override
    public boolean tryRelease(ServerPlayer player, PlayerAbilityData data, int ticks) {
        if (!canRelease(player, data, ticks)) return false;
        onChargingRelease(player, data, ticks);
        return true;
    }

    @Override
    public void onChargingAbort(ServerPlayer player, PlayerAbilityData data) {
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return (int) lerpf(80, 50, proficiency);
    }

    private static boolean hasGroundWithinTwoBlocks(ServerPlayer player) {
        Vec3 from = player.position();
        return player.serverLevel().clip(new ClipContext(from, from.add(0, -2, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.BLOCK;
    }
}
