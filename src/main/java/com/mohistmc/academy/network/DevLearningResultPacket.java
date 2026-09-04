package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Immediate authoritative result and resource snapshot for the open developer tree. */
public record DevLearningResultPacket(UUID nonce, boolean success, int energy, int maxEnergy,
                                      String reason) implements CustomPacketPayload {
    public static final Type<DevLearningResultPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "dev_learning_result"));
    private static final StreamCodec<ByteBuf,String> TEXT = ByteBufCodecs.stringUtf8(160);
    public static final StreamCodec<ByteBuf,DevLearningResultPacket> STREAM_CODEC = StreamCodec.ofMember(
            (p,b)->{b.writeLong(p.nonce.getMostSignificantBits());b.writeLong(p.nonce.getLeastSignificantBits());
                b.writeBoolean(p.success);b.writeInt(p.energy);b.writeInt(p.maxEnergy);TEXT.encode(b,p.reason);},
            b->new DevLearningResultPacket(new UUID(b.readLong(),b.readLong()),b.readBoolean(),b.readInt(),b.readInt(),TEXT.decode(b)));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    public static void handle(DevLearningResultPacket p, IPayloadContext c){c.enqueueWork(()->{
        if(c.player().level().isClientSide()) com.mohistmc.academy.client.gui.SkillTreeGui.acceptServerResult(p);
    });}
}
