package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CloseDevLearningSessionPacket(UUID nonce) implements CustomPacketPayload {
    public static final Type<CloseDevLearningSessionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "close_dev_learning_session"));
    public static final StreamCodec<ByteBuf, CloseDevLearningSessionPacket> STREAM_CODEC = StreamCodec.ofMember(
            (packet, buf) -> {
                buf.writeLong(packet.nonce.getMostSignificantBits());
                buf.writeLong(packet.nonce.getLeastSignificantBits());
            },
            buf -> new CloseDevLearningSessionPacket(new UUID(buf.readLong(), buf.readLong())));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CloseDevLearningSessionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> DevLearningSessionManager.clear(context.player().getUUID(), packet.nonce));
    }
}
