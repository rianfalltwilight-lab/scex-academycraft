package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Caster-only 1.0.7 coordinate-projection trail. */
public record TeleporterTrailPacket(double startX, double startY, double startZ,
                                    double endX, double endY, double endZ, byte kind)
        implements CustomPacketPayload {
    public static final byte THREATENING = 0, SHIFT = 1;
    public static final Type<TeleporterTrailPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "teleporter_trail"));
    public static final StreamCodec<ByteBuf, TeleporterTrailPacket> STREAM_CODEC = StreamCodec.ofMember(
            (packet, buffer) -> {
                buffer.writeDouble(packet.startX); buffer.writeDouble(packet.startY); buffer.writeDouble(packet.startZ);
                buffer.writeDouble(packet.endX); buffer.writeDouble(packet.endY); buffer.writeDouble(packet.endZ);
                ByteBufCodecs.BYTE.encode(buffer, packet.kind);
            }, buffer -> new TeleporterTrailPacket(buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), ByteBufCodecs.BYTE.decode(buffer)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TeleporterTrailPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if ((packet.kind == THREATENING || packet.kind == SHIFT) && packet.finite()
                    && packet.distanceSqr() <= 128 * 128) {
                com.mohistmc.academy.client.ClientPacketBridge.teleporterTrail(packet);
            }
        });
    }

    private boolean finite() {
        return Double.isFinite(startX) && Double.isFinite(startY) && Double.isFinite(startZ)
                && Double.isFinite(endX) && Double.isFinite(endY) && Double.isFinite(endZ);
    }

    private double distanceSqr() {
        double x=endX-startX,y=endY-startY,z=endZ-startZ;return x*x+y*y+z*z;
    }
}
