package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server to client snapshot for a node's nearby Matrix networks. */
public record MatrixNetworkListSyncPacket(CompoundTag data) implements CustomPacketPayload {
    public static final Type<MatrixNetworkListSyncPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "matrix_network_list_sync"));
    public static final StreamCodec<ByteBuf, MatrixNetworkListSyncPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.COMPOUND_TAG,
                    MatrixNetworkListSyncPacket::data, MatrixNetworkListSyncPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MatrixNetworkListSyncPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().isClientSide()) {
                com.mohistmc.academy.client.ClientPacketBridge.matrixNetworkList(packet);
            }
        });
    }
}
