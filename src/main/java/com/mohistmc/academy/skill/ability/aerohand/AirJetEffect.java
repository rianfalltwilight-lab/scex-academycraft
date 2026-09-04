package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.world.effect.EffectHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillEffect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * 空气喷射 —— 向后喷气，使施术者向视线方向短距离飞跃。
 */
public class AirJetEffect implements SkillEffect {

    @Override
    public String getId() {
        return "air_jet";
    }

    @Override
    public boolean canActivate(ServerPlayer player, PlayerAbilityData data) {
        return !player.isPassenger() && !player.isSleeping()
                && !AeroPassiveRuntime.isOffenseArmourEngaged(player);
    }

    @Override
    public void execute(ServerPlayer player, PlayerAbilityData data) {
        float exp = data.getProficiency(getId());
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle().normalize();
        double speed = AeroBehaviorMath.airJetSpeed(exp);
        // Preserve a little existing momentum while making the server's new
        // impulse authoritative.  A small vertical floor prevents the common
        // case where a horizontal cast is immediately eaten by ground friction.
        Vec3 impulse = look.scale(speed);
        impulse = new Vec3(impulse.x, Math.max(0.28, impulse.y + 0.18), impulse.z);
        player.setDeltaMovement(player.getDeltaMovement().scale(0.15).add(impulse));
        player.resetFallDistance();
        player.hurtMarked = true;

        Vec3 exhaust = player.getEyePosition().subtract(look.scale(0.8));
        EffectHelper.windBurst(level, exhaust.x, exhaust.y, exhaust.z, 28, 0.8);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);

        if (!data.isDevMode()) {
            DynamicSkillRules.addExp(player,data,getId(),0.005f);
        }
    }

    @Override
    public int getCooldownTicks(float proficiency) {
        return Math.round(30 - 15 * Math.clamp(proficiency, 0.0f, 1.0f));
    }
}

