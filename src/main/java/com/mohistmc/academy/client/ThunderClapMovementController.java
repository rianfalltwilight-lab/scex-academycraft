package com.mohistmc.academy.client;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.ability.electromaster.ElectromasterRules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Restores ThunderClapContextC's local 0.1 -> 0.001 walk-speed charge curve. */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class ThunderClapMovementController {
    private static LocalPlayer slowedPlayer;
    private static float originalSpeed;

    private ThunderClapMovementController() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean charging = player != null && ChargingHudOverlay.isCharging("thunder_clap");

        if (!charging) {
            restore();
            return;
        }

        if (slowedPlayer != player) {
            restore();
            slowedPlayer = player;
            originalSpeed = player.getAbilities().getWalkingSpeed();
        }
        player.getAbilities().setWalkingSpeed(ElectromasterRules.thunderClapWalkSpeed(
                originalSpeed, ChargingHudOverlay.chargeProgress()));
    }

    private static void restore() {
        if (slowedPlayer != null) slowedPlayer.getAbilities().setWalkingSpeed(originalSpeed);
        slowedPlayer = null;
        originalSpeed = 0;
    }
}
