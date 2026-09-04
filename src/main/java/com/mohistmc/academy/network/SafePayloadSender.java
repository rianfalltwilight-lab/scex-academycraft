package com.mohistmc.academy.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.slf4j.Logger;

/**
 * Sends optional S2C state only after the player's PLAY channel is negotiated.
 * Synthetic GameTest players and players still crossing the login/configuration
 * boundary deliberately have no usable custom-payload channel and are skipped.
 */
public final class SafePayloadSender {
    private static final Logger LOGGER = LogUtils.getLogger();

    private SafePayloadSender() {}

    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        if (player == null || player.connection == null || player.hasDisconnected()) return false;
        try {
            return NetworkRegistry.hasChannel(player.connection, type.id());
        } catch (RuntimeException ex) {
            // Test doubles and incomplete login connections may not own a channel yet.
            LOGGER.debug("Skipping {} before payload negotiation for {}", type.id(), player.getScoreboardName());
            return false;
        }
    }

    public static boolean send(ServerPlayer player, CustomPacketPayload payload) {
        if (!canSend(player, payload.type())) return false;
        PacketDistributor.sendToPlayer(player, payload);
        return true;
    }
}
