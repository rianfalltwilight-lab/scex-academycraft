package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncChargingStatePacket(int ticks, int maxTicks, int slotIndex, String skillId, long epoch, long generation,
                                      boolean accepted) implements CustomPacketPayload {

    public static final Type<SyncChargingStatePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "sync_charging"));

    public static final StreamCodec<ByteBuf, SyncChargingStatePacket> STREAM_CODEC = StreamCodec.ofMember(
            (packet,buf)->{buf.writeInt(packet.ticks());buf.writeInt(packet.maxTicks());buf.writeInt(packet.slotIndex());
                ByteBufCodecs.stringUtf8(64).encode(buf,packet.skillId());ByteBufCodecs.VAR_LONG.encode(buf,packet.epoch());
                ByteBufCodecs.VAR_LONG.encode(buf,packet.generation());buf.writeBoolean(packet.accepted());},
            buf->new SyncChargingStatePacket(buf.readInt(),buf.readInt(),buf.readInt(),
                    ByteBufCodecs.stringUtf8(64).decode(buf),ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf),buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncChargingStatePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            com.mohistmc.academy.client.ClientPacketBridge.charging(packet);
        });
    }
}
