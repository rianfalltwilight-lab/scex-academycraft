package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LocationConsentRequestPacket(UUID caster, String casterName, long nonce, int locationIndex,
                                           String dimension, long expiresAt) implements CustomPacketPayload {
    public static final Type<LocationConsentRequestPacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,"location_consent_request"));
    public static final StreamCodec<ByteBuf,LocationConsentRequestPacket> STREAM_CODEC=StreamCodec.ofMember(
            (p,b)->{
                b.writeLong(p.caster.getMostSignificantBits());
                b.writeLong(p.caster.getLeastSignificantBits());
                net.minecraft.network.codec.ByteBufCodecs.stringUtf8(64).encode(b,p.casterName);
                b.writeLong(p.nonce);
                b.writeInt(p.locationIndex);
                net.minecraft.network.codec.ByteBufCodecs.stringUtf8(64).encode(b,p.dimension);
                b.writeLong(p.expiresAt);
            },
            b->new LocationConsentRequestPacket(
                    new UUID(b.readLong(),b.readLong()),
                    net.minecraft.network.codec.ByteBufCodecs.stringUtf8(64).decode(b),
                    b.readLong(),b.readInt(),
                    net.minecraft.network.codec.ByteBufCodecs.stringUtf8(64).decode(b),
                    b.readLong()));
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(LocationConsentRequestPacket p,IPayloadContext c){c.enqueueWork(()->com.mohistmc.academy.client.ClientPacketBridge.locationConsent(p));}
}
