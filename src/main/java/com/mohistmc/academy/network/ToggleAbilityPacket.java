package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.common.NeoForge;
import com.mohistmc.academy.api.event.AbilityEvents;

public record ToggleAbilityPacket() implements CustomPacketPayload {

    public static final ToggleAbilityPacket INSTANCE = new ToggleAbilityPacket();

    public static final Type<ToggleAbilityPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "toggle_ability"));

    public static final StreamCodec<ByteBuf, ToggleAbilityPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleAbilityPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);

            if (!data.hasAbility() || !PayloadRateLimiter.allow(player.getUUID(), "toggle_ability",
                    player.serverLevel().getGameTime(), 4, 1)) return;

            // Suppression blocks activation, while still allowing an already-active
            // player to switch the ability off.
            if (!data.isAbilityActive() && AbilityInterferenceService.isInterfered(player)) {
                AbilityInterferenceService.notifyBlocked(player);
                return;
            }

            data.toggleAbilityActive();
            NeoForge.EVENT_BUS.post(data.isAbilityActive() ? new AbilityEvents.Activate(player) : new AbilityEvents.Deactivate(player));

            data.syncTo(player);
        });
    }
}
