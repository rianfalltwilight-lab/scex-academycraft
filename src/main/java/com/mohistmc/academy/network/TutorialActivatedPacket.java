package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C one-shot notification for the 1.0.7 tutorial activation flow. */
public record TutorialActivatedPacket(String tutorialId) implements CustomPacketPayload {
    public static final int MAX_ID_LENGTH = 64;
    public static final Type<TutorialActivatedPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "tutorial_activated"));
    public static final StreamCodec<ByteBuf, TutorialActivatedPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_ID_LENGTH), TutorialActivatedPacket::tutorialId,
            TutorialActivatedPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TutorialActivatedPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().isClientSide()) {
                com.mohistmc.academy.client.ClientPacketBridge.tutorialActivated(packet.tutorialId());
            }
        });
    }
}
