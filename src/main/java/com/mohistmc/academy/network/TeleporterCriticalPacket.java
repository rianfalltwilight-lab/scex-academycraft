package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authenticated 1.0.7 Teleporter critical-hit presentation. */
public record TeleporterCriticalPacket(int targetEntityId, byte tier) implements CustomPacketPayload {
    public static final Type<TeleporterCriticalPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "teleporter_critical"));
    public static final StreamCodec<ByteBuf, TeleporterCriticalPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TeleporterCriticalPacket::targetEntityId,
            ByteBufCodecs.BYTE, TeleporterCriticalPacket::tier,
            TeleporterCriticalPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TeleporterCriticalPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.targetEntityId >= 0 && packet.tier >= 0 && packet.tier < 3) {
                com.mohistmc.academy.client.ClientPacketBridge.teleporterCritical(packet);
            }
        });
    }
}
