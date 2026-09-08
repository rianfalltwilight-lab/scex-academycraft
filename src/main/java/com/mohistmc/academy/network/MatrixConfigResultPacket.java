package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Public, action-scoped receipt; passwords never travel back to the client. */
public record MatrixConfigResultPacket(MenuActionToken actionToken, BlockPos pos, boolean accepted,
                                       String ssid) implements CustomPacketPayload {
    public static final Type<MatrixConfigResultPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "matrix_config_result"));
    public static final StreamCodec<ByteBuf, MatrixConfigResultPacket> STREAM_CODEC = StreamCodec.composite(
            MenuActionToken.STREAM_CODEC, MatrixConfigResultPacket::actionToken,
            BlockPos.STREAM_CODEC, MatrixConfigResultPacket::pos,
            ByteBufCodecs.BOOL, MatrixConfigResultPacket::accepted,
            ByteBufCodecs.stringUtf8(NetworkInputLimits.SSID), MatrixConfigResultPacket::ssid,
            MatrixConfigResultPacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(MatrixConfigResultPacket packet, IPayloadContext context) {
        var connection = context.connection();
        context.enqueueWork(() -> com.mohistmc.academy.client.ClientPacketBridge.matrixConfigResult(packet, connection));
    }
}
