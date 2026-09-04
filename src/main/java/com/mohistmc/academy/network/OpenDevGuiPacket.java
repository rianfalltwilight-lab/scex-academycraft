package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端→客户端：打开开发机技能树界面。
 */
public record OpenDevGuiPacket(int typeOrdinal, int energy, int maxEnergy, Optional<BlockPos> mainPos,
                               UUID nonce, String nodeName) implements CustomPacketPayload {

    public static final int MAX_NODE_NAME_LENGTH = 32;

    public static final Type<OpenDevGuiPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "open_dev_gui"));

    public static final StreamCodec<ByteBuf, OpenDevGuiPacket> STREAM_CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeInt(packet.typeOrdinal());
                buf.writeInt(packet.energy());
                buf.writeInt(packet.maxEnergy());
                buf.writeBoolean(packet.mainPos().isPresent());
                packet.mainPos().ifPresent(pos -> BlockPos.STREAM_CODEC.encode(buf, pos));
                buf.writeLong(packet.nonce().getMostSignificantBits());
                buf.writeLong(packet.nonce().getLeastSignificantBits());
                ByteBufCodecs.stringUtf8(MAX_NODE_NAME_LENGTH).encode(buf, packet.nodeName());
            },
            buf -> new OpenDevGuiPacket(buf.readInt(), buf.readInt(), buf.readInt(),
                    buf.readBoolean() ? Optional.of(BlockPos.STREAM_CODEC.decode(buf)) : Optional.empty(),
                    new UUID(buf.readLong(), buf.readLong()),
                    ByteBufCodecs.stringUtf8(MAX_NODE_NAME_LENGTH).decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenDevGuiPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().isClientSide()) com.mohistmc.academy.client.ClientPacketBridge.openDev(packet);
        });
    }
}
