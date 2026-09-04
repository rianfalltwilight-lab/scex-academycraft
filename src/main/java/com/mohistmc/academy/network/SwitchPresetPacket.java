package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillChargingManager;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.common.NeoForge;
import com.mohistmc.academy.api.event.AbilityEvents;

/** Minimal C2S request. The client never supplies ability state or preset contents. */
public record SwitchPresetPacket(int targetIndex) implements CustomPacketPayload {
    public static final Type<SwitchPresetPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "switch_preset"));
    public static final StreamCodec<ByteBuf, SwitchPresetPacket> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(SwitchPresetPacket::new, SwitchPresetPacket::targetIndex);
    private static final Map<UUID, SwitchPresetPolicy.State> REQUESTS = new ConcurrentHashMap<>();

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SwitchPresetPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            long now = player.serverLevel().getGameTime();
            SwitchPresetPolicy.State old = REQUESTS.getOrDefault(player.getUUID(), SwitchPresetPolicy.State.empty());
            SwitchPresetPolicy.Decision decision = SwitchPresetPolicy.decide(packet.targetIndex(),
                    PlayerAbilityData.PRESET_COUNT, data.getCurrentPresetIndex(), data.hasAbility(),
                    SkillChargingManager.isCharging(player.getUUID()), old, now);
            if (decision.action() == SwitchPresetPolicy.Action.DROP) return;
            REQUESTS.put(player.getUUID(), decision.state());
            if (decision.action() == SwitchPresetPolicy.Action.ACCEPT) {
                int oldPreset = data.getCurrentPresetIndex();
                data.setCurrentPreset(packet.targetIndex());
                NeoForge.EVENT_BUS.post(new AbilityEvents.PresetSwitched(player, oldPreset, data.getCurrentPresetIndex()));
                data.syncTo(player);
            } else if (decision.action() == SwitchPresetPolicy.Action.REJECT_SYNC) {
                // Bounded rollback for stale presentation; flood rejections stay silent.
                data.syncTo(player);
            }
        });
    }

    public static void forget(UUID playerId) { REQUESTS.remove(playerId); }
    public static void clearAll() { REQUESTS.clear(); }
}
