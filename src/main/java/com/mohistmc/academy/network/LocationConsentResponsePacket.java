package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record LocationConsentResponsePacket(long nonce, boolean accepted) implements CustomPacketPayload {
    public static final Type<LocationConsentResponsePacket> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,"location_consent_response"));
    public static final StreamCodec<ByteBuf,LocationConsentResponsePacket> STREAM_CODEC=StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,LocationConsentResponsePacket::nonce,
            ByteBufCodecs.BOOL,LocationConsentResponsePacket::accepted,LocationConsentResponsePacket::new);
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(LocationConsentResponsePacket p,IPayloadContext c){c.enqueueWork(()->{
        if(c.player() instanceof net.minecraft.server.level.ServerPlayer sp) LocationTeleportActionPacket.answerConsent(sp,p.nonce(),p.accepted());
    });}
}
