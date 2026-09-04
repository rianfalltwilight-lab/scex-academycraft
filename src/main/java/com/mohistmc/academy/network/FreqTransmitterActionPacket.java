package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S control plane for a server-owned frequency-transmitter session. */
public record FreqTransmitterActionPacket(int action, UUID nonce, String password)
        implements CustomPacketPayload {
    public static final int OPEN = 0;
    public static final int AUTHORIZE = 1;
    public static final int CANCEL = 2;
    private static final UUID NO_NONCE = new UUID(0, 0);

    public static final Type<FreqTransmitterActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "freq_transmitter_action"));

    public static final StreamCodec<ByteBuf, FreqTransmitterActionPacket> STREAM_CODEC =
            StreamCodec.ofMember((packet, buf) -> {
                buf.writeByte(packet.action);
                UUID nonce = packet.nonce == null ? NO_NONCE : packet.nonce;
                buf.writeLong(nonce.getMostSignificantBits());
                buf.writeLong(nonce.getLeastSignificantBits());
                ByteBufCodecs.stringUtf8(NetworkInputLimits.PASSWORD).encode(buf,
                        packet.password == null ? "" : packet.password);
            }, buf -> new FreqTransmitterActionPacket(buf.readUnsignedByte(),
                    new UUID(buf.readLong(), buf.readLong()),
                    ByteBufCodecs.stringUtf8(NetworkInputLimits.PASSWORD).decode(buf)));

    public static FreqTransmitterActionPacket open(UUID requestNonce) {
        return new FreqTransmitterActionPacket(OPEN, requestNonce, "");
    }

    public static FreqTransmitterActionPacket authorize(UUID nonce, String password) {
        return new FreqTransmitterActionPacket(AUTHORIZE, nonce, password);
    }

    public static FreqTransmitterActionPacket cancel(UUID nonce) {
        return new FreqTransmitterActionPacket(CANCEL, nonce, "");
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(FreqTransmitterActionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            long now = player.serverLevel().getGameTime();
            if (!PayloadRateLimiter.allow(player.getUUID(), "freq_transmitter", now, 5, 6)) return;
            switch (packet.action) {
                case OPEN -> FreqTransmitterSessionManager.open(player, packet.nonce);
                case AUTHORIZE -> FreqTransmitterSessionManager.authorize(
                        player, packet.nonce, packet.password);
                case CANCEL -> FreqTransmitterSessionManager.cancel(player, packet.nonce);
                default -> { }
            }
        });
    }
}
