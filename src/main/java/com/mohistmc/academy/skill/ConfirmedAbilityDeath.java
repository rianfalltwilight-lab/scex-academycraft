package com.mohistmc.academy.skill;

import com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime;
import com.mohistmc.academy.skill.ability.meltdowner.JetEngineRuntime;
import com.mohistmc.academy.skill.ability.telekinesis.CruiseBombEffect;
import com.mohistmc.academy.skill.ability.telekinesis.PsychoTransmissionEffect;
import com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler;
import com.mohistmc.academy.skill.ability.teleporter.FlashingSessionManager;
import com.mohistmc.academy.skill.passive.PassiveSkillEventHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Exactly the accepted death branch, before vanilla removes or drops player inventory. */
public final class ConfirmedAbilityDeath {
    private ConfirmedAbilityDeath() {}

    public static void accept(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        SkillEventHandler.onConfirmedDeath(entity);
        PassiveSkillEventHandler.onConfirmedDeath(entity);
        AeroPassiveRuntime.onConfirmedDeath(entity);
        TelekinesisPassiveHandler.onConfirmedDeath(entity);
        PsychoTransmissionEffect.onConfirmedDeath(entity);
        CruiseBombEffect.onConfirmedDeath(entity);
        JetEngineRuntime.onConfirmedDeath(entity);
        FlashingSessionManager.onConfirmedDeath(entity);
        if (entity instanceof ServerPlayer player)
            com.mohistmc.academy.network.SkillInputSessionManager.onConfirmedDeath(player);
    }
}
