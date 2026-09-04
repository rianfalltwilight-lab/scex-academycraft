package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.energy.impl.WiWorldData;
import com.mohistmc.academy.energy.impl.WirelessNet;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import com.mohistmc.academy.world.menu.BaseNodeMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Disconnects only the open node from its current Matrix network. */
public record DisconnectNodeFromMatrixPacket(BlockPos nodePos) implements CustomPacketPayload {
    public static final Type<DisconnectNodeFromMatrixPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "disconnect_node_from_matrix"));
    public static final StreamCodec<ByteBuf, DisconnectNodeFromMatrixPacket> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, DisconnectNodeFromMatrixPacket::nodePos,
                    DisconnectNodeFromMatrixPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DisconnectNodeFromMatrixPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            if (!(player.containerMenu instanceof BaseNodeMenu menu)
                    || !packet.nodePos().equals(menu.pos) || !menu.stillValid(player)
                    || !PayloadRateLimiter.allow(player.getUUID(), "node_matrix_disconnect",
                    level.getGameTime(), 20, 4)
                    || !level.isLoaded(packet.nodePos())
                    || !level.mayInteract(player, packet.nodePos())
                    || !(level.getBlockEntity(packet.nodePos()) instanceof BaseNodeBlockEntity node)) {
                return;
            }

            if (!node.canManage(player)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.academy.node.owner_only"));
                return;
            }

            WiWorldData data = WiWorldData.getNonCreate(level);
            WirelessNet network = data == null ? null : data.getNetwork(node);
            var matrix = network == null ? null : network.getMatrix();
            boolean unlinked = matrix != null && WirelessSystem.unlinkNode(level, matrix, node);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(unlinked
                    ? "§a节点已退出矩阵网络" : "§c节点当前未连接矩阵"));
        });
    }
}
