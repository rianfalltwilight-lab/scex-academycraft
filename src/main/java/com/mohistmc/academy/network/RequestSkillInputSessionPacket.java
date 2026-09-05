package com.mohistmc.academy.network;
import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
/** Retries readiness after a client level unload; never rotates an existing session. */
public record RequestSkillInputSessionPacket() implements CustomPacketPayload {
    public static final RequestSkillInputSessionPacket INSTANCE = new RequestSkillInputSessionPacket();
    public static final Type<RequestSkillInputSessionPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,"request_skill_input_session"));
    public static final StreamCodec<ByteBuf,RequestSkillInputSessionPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(RequestSkillInputSessionPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer p && SkillInputSessionManager.isCurrentPlayer(p)
                    && PayloadRateLimiter.allow(p.getUUID(),"skill_input_session",p.serverLevel().getGameTime(),20,2))
                SkillInputSessionManager.sendCurrent(p);
        });
    }
}