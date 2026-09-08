package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Public configuration receipt tied to exactly one viewer's submitted action. No plaintext password. */
public record NodeConfigResultPacket(MenuActionToken actionToken, BlockPos pos, boolean accepted,
                                     String name, boolean passwordConfigured, long revision) implements CustomPacketPayload {
    public static final Type<NodeConfigResultPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "node_config_result"));
    public static final StreamCodec<ByteBuf, NodeConfigResultPacket> STREAM_CODEC = StreamCodec.composite(
            MenuActionToken.STREAM_CODEC, NodeConfigResultPacket::actionToken,
            BlockPos.STREAM_CODEC, NodeConfigResultPacket::pos,
            ByteBufCodecs.BOOL, NodeConfigResultPacket::accepted,
            ByteBufCodecs.stringUtf8(NetworkInputLimits.NODE_NAME), NodeConfigResultPacket::name,
            ByteBufCodecs.BOOL, NodeConfigResultPacket::passwordConfigured,
            ByteBufCodecs.VAR_LONG, NodeConfigResultPacket::revision, NodeConfigResultPacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(NodeConfigResultPacket packet, IPayloadContext context) {
        var sourceConnection = context.connection();
        context.enqueueWork(() -> com.mohistmc.academy.client.ClientPacketBridge
                .nodeConfigResult(packet, sourceConnection));
    }
}
