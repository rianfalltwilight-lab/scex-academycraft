package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Selects the wireless page after the server has opened the authenticated developer menu. */
public record OpenDevNetworkPagePacket(BlockPos pos, int containerId) implements CustomPacketPayload {
    public static final Type<OpenDevNetworkPagePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "open_dev_network_page"));
    public static final StreamCodec<ByteBuf, OpenDevNetworkPagePacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenDevNetworkPagePacket::pos,
            ByteBufCodecs.VAR_INT, OpenDevNetworkPagePacket::containerId,
            OpenDevNetworkPagePacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenDevNetworkPagePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().isClientSide()) {
                com.mohistmc.academy.client.ClientPacketBridge.openDevNetworkPage(packet);
            }
        });
    }
}
