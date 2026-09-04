package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.Skill;
import com.mohistmc.academy.skill.SkillChargingManager;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SkillKeyUpPacket(int slotIndex, String skillId, long epoch, float releaseValue) implements CustomPacketPayload {

    public SkillKeyUpPacket(int slotIndex, String skillId, long epoch) {
        this(slotIndex, skillId, epoch, Float.NaN);
    }

    public static final Type<SkillKeyUpPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "skill_key_up"));

    public static final StreamCodec<ByteBuf, SkillKeyUpPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, SkillKeyUpPacket::slotIndex,
            ByteBufCodecs.stringUtf8(64), SkillKeyUpPacket::skillId,
            ByteBufCodecs.VAR_LONG, SkillKeyUpPacket::epoch,
            ByteBufCodecs.FLOAT, SkillKeyUpPacket::releaseValue, SkillKeyUpPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkillKeyUpPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);

            SkillChargingManager.ChargingState state = SkillChargingManager.getState(player.getUUID());
            if (AbilityInterferenceService.isInterfered(player)) {
                if (state != null) {
                    SkillChargingManager.finalizeCharging(player, state, SkillChargingManager.FinalResult.ABORTED);
                    SafePayloadSender.send(player, new SyncChargingStatePacket(-1, 0, state.slotIndex,
                            state.skillId, state.epoch, state.generation, true));
                    data.syncTo(player);
                }
                AbilityInterferenceService.notifyBlocked(player);
                return;
            }
            if (!SkillChargingManager.matches(state,packet.slotIndex(),packet.skillId(),packet.epoch())) return;

            state.releasing = true; // 标记正在释放，防止 onPlayerTick 重复进入

            Skill skill = data.getSlotSkill(data.getCurrentPresetIndex(), state.slotIndex);
            if (skill == null || !skill.getId().equals(state.skillId)
                    || !data.isAbilityActive() || !data.hasLearnedSkill(state.skillId)) {
                SkillChargingManager.cancel(player);
                SafePayloadSender.send(player, new SyncChargingStatePacket(-1, 0, state.slotIndex, state.skillId, state.epoch, state.generation, true));
                return;
            }

            SkillEffect effect = skill.getEffect();
            if (!(effect instanceof ChargingSkillEffect chargingEffect)) {
                SkillChargingManager.cancel(player);
                SafePayloadSender.send(player, new SyncChargingStatePacket(-1, 0, state.slotIndex, state.skillId, state.epoch, state.generation, true));
                return;
            }

            // 通知客户端停止蓄力 HUD
            SafePayloadSender.send(player, new SyncChargingStatePacket(-1, 0, state.slotIndex, state.skillId, state.epoch, state.generation, true));

            if (!chargingEffect.releasesOnKeyUp()) {
                // ThunderClap's 1.0.7 key-up listener terminated the context. Only its
                // server tick (full charge or the 40-tick resource edge) invoked MSG_END.
                SkillChargingManager.finalizeCharging(player, state,
                        SkillChargingManager.FinalResult.ABORTED);
                data.syncTo(player);
                return;
            }

            if (state.ticks >= chargingEffect.getMinChargeTicks(data)) {
                float preCastProficiency = data.getProficiency(skill.getId());
                boolean released = chargingEffect.tryRelease(player, data, state.ticks, packet.releaseValue());
                if(released) com.mohistmc.academy.advancement.LegacyAdvancementBridge.used(player,skill);
                if (!released) {
                    SkillChargingManager.finalizeCharging(player, state, SkillChargingManager.FinalResult.ABORTED);
                    data.syncTo(player);
                    return;
                }
                SkillChargingManager.finalizeCharging(player, state, SkillChargingManager.FinalResult.RELEASED);
                if (effect.grantsActivationProficiency()) {
                    com.mohistmc.academy.skill.AbilityMutationService.addSkillExp(player, data, skill.getId(), 0.002f);
                }
                // 设置冷却（不重复扣 CP/Overload，由 onChargingRelease 自行处理）
                if (!data.isDevMode() && chargingEffect.shouldApplyCooldownAfterRelease(player, data, state.ticks)) {
                    int cd = chargingEffect.getCooldownTicks(preCastProficiency, state.ticks);
                    data.setCooldown(skill.getId(), cd);
                }
            } else {
                SkillChargingManager.finalizeCharging(player, state, SkillChargingManager.FinalResult.ABORTED);
            }
            data.syncTo(player);
        });
    }
}
