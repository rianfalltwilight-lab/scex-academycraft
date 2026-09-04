package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillPreset;
import com.mohistmc.academy.skill.SkillRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.common.NeoForge;
import com.mohistmc.academy.api.event.AbilityEvents;

/**
 * 客户端→服务端：设置技能槽位。
 */
public record SetSkillSlotPacket(int presetIndex, int slotIndex, String skillId) implements CustomPacketPayload {

    private static final int MAX_SKILL_ID_LENGTH = 64;

    public static final Type<SetSkillSlotPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "set_skill_slot"));

    public static final StreamCodec<ByteBuf, SetSkillSlotPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, SetSkillSlotPacket::presetIndex,
                    ByteBufCodecs.INT, SetSkillSlotPacket::slotIndex,
                    ByteBufCodecs.stringUtf8(MAX_SKILL_ID_LENGTH), SetSkillSlotPacket::skillId,
                    SetSkillSlotPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetSkillSlotPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);

            if (packet.presetIndex() < 0 || packet.presetIndex() >= PlayerAbilityData.PRESET_COUNT) return;
            if (packet.slotIndex() < 0 || packet.slotIndex() >= SkillPreset.SLOT_COUNT) return;
            if (!PayloadRateLimiter.allow(player.getUUID(), "set_skill_slot",
                    player.serverLevel().getGameTime(), 2, 4)) return;

            String skillId = packet.skillId();
            SkillPreset preset = data.getPreset(packet.presetIndex());
            String existing = preset == null ? null : preset.getSlot(packet.slotIndex());
            if (skillId.equals(existing == null ? "" : existing)) return;
            if (skillId.isEmpty()) {
                data.clearSlot(packet.presetIndex(), packet.slotIndex());
            } else {
                com.mohistmc.academy.skill.Skill skill = SkillRegistry.getSkill(data.getCurrentAbility(), skillId);
                if (skill == null || skill.getType() != com.mohistmc.academy.skill.SkillType.ACTIVE
                        || !data.hasLearnedSkill(skillId)) return;
                data.setSlot(packet.presetIndex(), packet.slotIndex(), skillId);
            }
            NeoForge.EVENT_BUS.post(new AbilityEvents.PresetUpdated(player, packet.presetIndex(), packet.slotIndex(), existing,
                    skillId.isEmpty() ? null : skillId));
            data.syncTo(player);
        });
    }
}
