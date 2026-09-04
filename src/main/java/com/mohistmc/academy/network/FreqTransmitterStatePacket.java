package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C state for the frequency-transmitter UI/targeting overlay. */
public record FreqTransmitterStatePacket(UUID nonce, int state, int sourceKind,
                                         String sourceLabel, String message)
        implements CustomPacketPayload {
    public static final int SELECT_SOURCE = 0;
    public static final int PASSWORD_REQUIRED = 1;
    public static final int SELECT_TARGET = 2;
    public static final int CLOSED = 3;
    private static final int MAX_LABEL = 96;
    private static final int MAX_MESSAGE = 192;

    public static final Type<FreqTransmitterStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "freq_transmitter_state"));

    public static final StreamCodec<ByteBuf, FreqTransmitterStatePacket> STREAM_CODEC =
            StreamCodec.ofMember((packet, buf) -> {
                buf.writeLong(packet.nonce.getMostSignificantBits());
                buf.writeLong(packet.nonce.getLeastSignificantBits());
                buf.writeByte(packet.state);
                buf.writeByte(packet.sourceKind);
                ByteBufCodecs.stringUtf8(MAX_LABEL).encode(buf, packet.sourceLabel);
                ByteBufCodecs.stringUtf8(MAX_MESSAGE).encode(buf, packet.message);
            }, buf -> new FreqTransmitterStatePacket(
                    new UUID(buf.readLong(), buf.readLong()),
                    buf.readUnsignedByte(), buf.readUnsignedByte(),
                    ByteBufCodecs.stringUtf8(MAX_LABEL).decode(buf),
                    ByteBufCodecs.stringUtf8(MAX_MESSAGE).decode(buf)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FreqTransmitterStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().isClientSide()) {
                com.mohistmc.academy.client.ClientPacketBridge.freqTransmitter(packet);
            }
        });
    }
}
