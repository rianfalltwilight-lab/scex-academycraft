package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Single-player-only server settings exposed by the legacy terminal app. */
public record SettingsConfigPacket(byte setting, boolean enabled) implements CustomPacketPayload {
    public static final byte PVP = 0;
    public static final byte DESTROY_BLOCKS = 1;
    public static final Type<SettingsConfigPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "settings_config"));
    public static final StreamCodec<ByteBuf, SettingsConfigPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, SettingsConfigPacket::setting,
            ByteBufCodecs.BOOL, SettingsConfigPacket::enabled,
            SettingsConfigPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SettingsConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            apply(player, packet.setting, packet.enabled);
        });
    }

    /** Public deterministic boundary used by dedicated-server denial tests. */
    public static boolean apply(ServerPlayer player, byte setting, boolean enabled) {
        if (player == null || player.getServer() == null) return false;
        var server = player.getServer();
        if (!server.isSingleplayer() || !server.isSingleplayerOwner(player.getGameProfile())) return false;
        if (!PayloadRateLimiter.allow(player.getUUID(), "settings-config",
                player.serverLevel().getGameTime(), 1, 4)) return false;
        return switch (setting) {
            case PVP -> {
                ACConfig.Server.PVP_ENABLED.set(enabled);
                yield true;
            }
            case DESTROY_BLOCKS -> {
                ACConfig.Server.DESTROY_BLOCKS.set(enabled);
                yield true;
            }
            default -> false;
        };
    }
}
