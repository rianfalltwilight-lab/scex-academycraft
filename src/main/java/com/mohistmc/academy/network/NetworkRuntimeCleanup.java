package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Integrated-server restart safety for process-static request and limiter ledgers. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class NetworkRuntimeCleanup {
    private NetworkRuntimeCleanup() {}

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        PayloadRateLimiter.clearAll();
        RequestNodesPacket.clearAll();
        RequestMatrixNetworksPacket.clearAll();
        SwitchPresetPacket.clearAll();
        LocationTeleportActionPacket.clearAll();
        ConnectToNodePacket.clearAll();
        MatrixNodesPacket.clearAll();
        DevLearningSessionManager.clearAll();
        FreqTransmitterSessionManager.clearAll();
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        RequestMatrixNetworksPacket.forgetPlayer(event.getEntity().getUUID());
        MatrixNodesPacket.clearPlayer(event.getEntity().getUUID());
        FreqTransmitterSessionManager.clearPlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        FreqTransmitterSessionManager.clearPlayer(event.getEntity().getUUID());
    }
}
