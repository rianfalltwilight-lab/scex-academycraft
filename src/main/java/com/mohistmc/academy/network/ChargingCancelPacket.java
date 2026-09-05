package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.SkillChargingManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Exact-session abort used when a GUI interrupts held input. It can never release or reward. */
public record ChargingCancelPacket(int slotIndex, String skillId, long epoch) implements CustomPacketPayload {
    public static final Type<ChargingCancelPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "charging_cancel"));
    public static final StreamCodec<ByteBuf, ChargingCancelPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, ChargingCancelPacket::slotIndex,
            ByteBufCodecs.stringUtf8(64), ChargingCancelPacket::skillId,
            ByteBufCodecs.VAR_LONG, ChargingCancelPacket::epoch, ChargingCancelPacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ChargingCancelPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !SkillInputSessionManager.isCurrentPlayer(player)) return;
            SkillChargingManager.ChargingState state = SkillChargingManager.getState(player.getUUID());
            if (!SkillChargingManager.matches(state, packet.slotIndex(), packet.skillId(), packet.epoch())) return;
            SkillChargingManager.finalizeCharging(player, state, SkillChargingManager.FinalResult.ABORTED);
            SafePayloadSender.send(player, new SyncChargingStatePacket(-1, 0, state.slotIndex,
                    state.skillId, state.epoch, state.generation, true));
            player.getData(AcademyAttachments.PLAYER_ABILITY).syncTo(player);
        });
    }
}
