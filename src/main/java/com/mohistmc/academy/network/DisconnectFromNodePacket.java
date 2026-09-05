package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.energy.api.block.IWirelessGenerator;
import com.mohistmc.academy.energy.api.block.IWirelessReceiver;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.mohistmc.academy.world.menu.AcademyMenu;

/**
 * 客户端→服务端：断开机器与节点的连接。
 */
public record DisconnectFromNodePacket(MenuActionToken actionToken, BlockPos machinePos) implements CustomPacketPayload {

    public static final Type<DisconnectFromNodePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "disconnect_from_node"));

    public static final StreamCodec<ByteBuf, DisconnectFromNodePacket> STREAM_CODEC =
            StreamCodec.composite(
                    MenuActionToken.STREAM_CODEC, DisconnectFromNodePacket::actionToken,
                    BlockPos.STREAM_CODEC, DisconnectFromNodePacket::machinePos,
                    DisconnectFromNodePacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DisconnectFromNodePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServerLevel level = player.serverLevel();
                if (!(player.containerMenu instanceof AcademyMenu menu)
                        || !packet.machinePos().equals(menu.pos) || !menu.stillValid(player) || !menu.acceptAction(packet.actionToken(), player)) return;
                if (!PayloadRateLimiter.allow(player.getUUID(), "machine_node_disconnect",
                        level.getGameTime(), 20, 8)) return;
                if (!level.isLoaded(packet.machinePos())
                        || player.distanceToSqr(packet.machinePos().getX() + 0.5, packet.machinePos().getY() + 0.5,
                        packet.machinePos().getZ() + 0.5) > 64.0
                        || !level.mayInteract(player, packet.machinePos())) return;
                BlockEntity be = level.getBlockEntity(packet.machinePos());
                if (be == null) return;

                if (be instanceof com.mohistmc.academy.energy.api.block.IWirelessUser user) {
                    if (WirelessSystem.unlinkUser(level, user)) {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a已断开连接"));
                    } else {
                        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c该机器未连接到任何节点"));
                    }
                }
            }
        });
    }
}
