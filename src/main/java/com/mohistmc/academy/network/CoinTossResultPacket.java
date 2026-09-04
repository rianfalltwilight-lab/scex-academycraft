package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Explicit normal-toss result, avoiding false messages when Railgun consumes the coin. */
public record CoinTossResultPacket(byte side) implements CustomPacketPayload {
    public static final Type<CoinTossResultPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "coin_toss_result"));
    public static final StreamCodec<ByteBuf, CoinTossResultPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, CoinTossResultPacket::side, CoinTossResultPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CoinTossResultPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.side == 0 || packet.side == 1) {
                com.mohistmc.academy.client.ClientPacketBridge.coinTossResult(packet.side);
            }
        });
    }
}
