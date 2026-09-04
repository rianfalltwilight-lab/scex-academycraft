package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillChargingManager;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.skill.SkillPreset;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SkillKeyDownPacket(int slotIndex, long generation) implements CustomPacketPayload {

    public static final Type<SkillKeyDownPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "skill_key_down"));

    public static final StreamCodec<ByteBuf, SkillKeyDownPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SkillKeyDownPacket::slotIndex,
            ByteBufCodecs.VAR_LONG, SkillKeyDownPacket::generation, SkillKeyDownPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkillKeyDownPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            long now = player.serverLevel().getGameTime();
            if (!PayloadRateLimiter.allow(player.getUUID(), "skill_key_down", now, 20, 20)) return;
            if (AbilityInterferenceService.isInterfered(player)) {
                AbilityInterferenceService.notifyBlocked(player);
                reject(player, packet.slotIndex(), "", packet.generation(), now);
                return;
            }

            if (packet.slotIndex() < 0 || packet.slotIndex() >= SkillPreset.SLOT_COUNT) {
                reject(player, packet.slotIndex(), "", packet.generation(), now); return;
            }

            Skill skill = data.getSlotSkill(data.getCurrentPresetIndex(), packet.slotIndex());
            String skillId = skill == null ? "" : skill.getId();
            if (!data.isAbilityActive() || skill == null || !data.hasLearnedSkill(skillId)
                    || !data.canUseSkill(skill)) { reject(player, packet.slotIndex(), skillId, packet.generation(), now); return; }

            SkillEffect effect = skill.getEffect();
            if (!(effect instanceof ChargingSkillEffect chargingEffect)) { reject(player, packet.slotIndex(), skillId, packet.generation(), now); return; }

            if (SkillChargingManager.isCharging(player.getUUID())) { reject(player, packet.slotIndex(), skillId, packet.generation(), now); return; }

            // Dynamic/legacy charging costs are authoritative and may be much larger
            // than the registry's UI estimate. Never create a state before they pass.
            if (!chargingEffect.canStartCharging(player, data)) { reject(player, packet.slotIndex(), skillId, packet.generation(), now); return; }

            SkillChargingManager.ChargingState state = SkillChargingManager.startCharging(player.getUUID(), packet.slotIndex(), skill.getId(), packet.generation(), player.serverLevel().getGameTime());
            chargingEffect.onChargingStart(player, data);
            SafePayloadSender.send(player, new SyncChargingStatePacket(0, chargingEffect.getMaxChargeTicks(data),
                    state.slotIndex, state.skillId, state.epoch, state.generation, true));
            data.syncTo(player);
        });
    }

    private static void reject(ServerPlayer player, int slot, String skillId, long generation, long now) {
        if (PayloadRateLimiter.allow(player.getUUID(), "skill_key_down_feedback", now, 20, 1))
            SafePayloadSender.send(player, new SyncChargingStatePacket(-1, 0, slot, skillId, 0, generation, false));
    }
}
