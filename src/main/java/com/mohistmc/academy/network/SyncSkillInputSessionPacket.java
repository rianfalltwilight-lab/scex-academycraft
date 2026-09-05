package com.mohistmc.academy.network;
import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
public record SyncSkillInputSessionPacket(UUID session, long revision, ResourceLocation dimension) implements CustomPacketPayload {
    public static final Type<SyncSkillInputSessionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,"skill_input_session"));
    public static final StreamCodec<ByteBuf,SyncSkillInputSessionPacket> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SyncSkillInputSessionPacket::session,
            ByteBufCodecs.VAR_LONG, SyncSkillInputSessionPacket::revision,
            ResourceLocation.STREAM_CODEC, SyncSkillInputSessionPacket::dimension, SyncSkillInputSessionPacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(SyncSkillInputSessionPacket packet, IPayloadContext context) {
        var connection = context.connection();
        context.enqueueWork(() -> com.mohistmc.academy.client.SkillInputClientState.accept(connection, packet.session, packet.revision, packet.dimension));
    }
}