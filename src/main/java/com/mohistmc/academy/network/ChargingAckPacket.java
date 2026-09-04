package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.SkillChargingManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Confirms that the client received the correlated charging start. */
public record ChargingAckPacket(long epoch, long generation) implements CustomPacketPayload {
    public static final Type<ChargingAckPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,"charging_ack"));
    public static final StreamCodec<ByteBuf,ChargingAckPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG,ChargingAckPacket::epoch,ByteBufCodecs.VAR_LONG,ChargingAckPacket::generation,ChargingAckPacket::new);
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(ChargingAckPacket packet, IPayloadContext context){context.enqueueWork(()->{
        ServerPlayer player=(ServerPlayer)context.player();
        SkillChargingManager.ChargingState state=SkillChargingManager.getState(player.getUUID());
        if(state!=null&&state.epoch==packet.epoch()&&state.generation==packet.generation())state.acknowledged=true;
    });}
}
