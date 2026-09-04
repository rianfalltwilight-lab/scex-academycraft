package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.energy.impl.WiWorldData;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import com.mohistmc.academy.world.menu.BaseNodeMenu;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative equivalent of the 1.0.7 LinkNodeEvent UI action. */
public record ConnectNodeToMatrixPacket(BlockPos nodePos, BlockPos matrixPos,
                                        Optional<String> password) implements CustomPacketPayload {
    private static final int MAX_PASSWORD_LENGTH = NetworkInputLimits.PASSWORD;
    public static final Type<ConnectNodeToMatrixPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "connect_node_to_matrix"));
    public static final StreamCodec<ByteBuf, ConnectNodeToMatrixPacket> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConnectNodeToMatrixPacket::nodePos,
            BlockPos.STREAM_CODEC, ConnectNodeToMatrixPacket::matrixPos,
            ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(MAX_PASSWORD_LENGTH)),
            ConnectNodeToMatrixPacket::password,
            ConnectNodeToMatrixPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConnectNodeToMatrixPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            if (!(player.containerMenu instanceof BaseNodeMenu menu)
                    || !packet.nodePos().equals(menu.pos) || !menu.stillValid(player)
                    || packet.password().filter(value -> value.length() > MAX_PASSWORD_LENGTH).isPresent()
                    || !PayloadRateLimiter.allow(player.getUUID(), "node_matrix_connect",
                    level.getGameTime(), 20, 4)
                    || !level.isLoaded(packet.nodePos()) || !level.isLoaded(packet.matrixPos())
                    || !level.mayInteract(player, packet.nodePos())
                    || !level.mayInteract(player, packet.matrixPos())
                    || !(level.getBlockEntity(packet.nodePos()) instanceof BaseNodeBlockEntity node)
                    || !(level.getBlockEntity(packet.matrixPos()) instanceof MatrixBlockEntity matrix)) {
                return;
            }

            if (!node.canManage(player)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.academy.node.owner_only"));
                return;
            }

            var state = WirelessSystem.reconcileMatrixNetwork(level, matrix);
            if (!state.active() || !matrix.isOperational()) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c矩阵尚未初始化或组件不完整"));
                return;
            }

            WiWorldData data = WiWorldData.getNonCreate(level);
            var targetNetwork = data == null ? null : data.getNetwork(matrix);
            if (data == null || !data.isNetworkDiscoverable(targetNetwork,
                    packet.nodePos().getX(), packet.nodePos().getY(), packet.nodePos().getZ(),
                    node.getRange())) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c矩阵网络不在节点可发现范围内"));
                return;
            }

            boolean linked = WirelessSystem.linkNode(level, matrix, node, packet.password().orElse(""));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(linked
                    ? "§a节点已加入矩阵网络" : "§c连接失败，请检查密码、容量和范围"));
        });
    }
}
