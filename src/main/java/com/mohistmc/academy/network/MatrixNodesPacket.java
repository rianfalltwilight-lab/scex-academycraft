package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.energy.api.block.IWirelessNode;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import com.mohistmc.academy.world.menu.MatrixMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative bulk link/unlink of owned, loaded nodes inside matrix range. */
public record MatrixNodesPacket(MenuActionToken actionToken, BlockPos matrixPos, boolean connect) implements CustomPacketPayload {
    private static final long COOLDOWN_TICKS = 10;
    private static final java.util.Map<RequestKey, Long> LAST_REQUEST = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Long> LAST_PLAYER_REQUEST = new java.util.concurrent.ConcurrentHashMap<>();
    private record RequestKey(java.util.UUID player, BlockPos matrix) {}
    public static final Type<MatrixNodesPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "matrix_nodes"));
    public static final StreamCodec<ByteBuf, MatrixNodesPacket> STREAM_CODEC = StreamCodec.composite(
                    MenuActionToken.STREAM_CODEC, MatrixNodesPacket::actionToken,
            BlockPos.STREAM_CODEC, MatrixNodesPacket::matrixPos,
            ByteBufCodecs.BOOL, MatrixNodesPacket::connect,
            MatrixNodesPacket::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(MatrixNodesPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.containerMenu instanceof MatrixMenu menu)
                    || !packet.matrixPos.equals(menu.pos) || !menu.stillValid(player) || !menu.acceptAction(packet.actionToken(), player)
                    || !player.serverLevel().isLoaded(packet.matrixPos)
                    || !player.serverLevel().mayInteract(player, packet.matrixPos)
                    || !(player.serverLevel().getBlockEntity(packet.matrixPos) instanceof MatrixBlockEntity matrix)
                    || !matrix.canManage(player)) return;
            var networkState = WirelessSystem.reconcileMatrixNetwork(player.serverLevel(), matrix);
            if (!networkState.active()) {
                player.sendSystemMessage(Component.literal(networkState
                        == WirelessSystem.MatrixNetworkState.NEEDS_REINITIALIZATION
                        ? "§e矩阵需要重新初始化" : "§c矩阵网络不可用，恢复失败"));
                return;
            }
            if (!claimRequest(player.getUUID(), packet.matrixPos, player.serverLevel().getGameTime())) return;
            int range = (int)Math.ceil(matrix.getRange()), changed = 0;
            int minChunkX = (packet.matrixPos.getX() - range) >> 4, maxChunkX = (packet.matrixPos.getX() + range) >> 4;
            int minChunkZ = (packet.matrixPos.getZ() - range) >> 4, maxChunkZ = (packet.matrixPos.getZ() + range) >> 4;
            java.util.List<net.minecraft.world.level.block.entity.BlockEntity> candidates = new java.util.ArrayList<>();
            for (int cx = minChunkX; cx <= maxChunkX; cx++) for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (player.serverLevel().hasChunk(cx, cz)) {
                    candidates.addAll(player.serverLevel().getChunk(cx, cz).getBlockEntities().values());
                }
            }
            for (var be : candidates) {
                BlockPos pos = be.getBlockPos();
                if (pos.distSqr(packet.matrixPos) > matrix.getRange() * matrix.getRange()) continue;
                if (!(be instanceof IWirelessNode node)
                        || (be instanceof BaseNodeBlockEntity owned && !owned.canManage(player))
                        || !player.serverLevel().mayInteract(player, pos)) continue;
                boolean ok = packet.connect
                        ? WirelessSystem.linkNode(player.serverLevel(), matrix, node, matrix.getPassword())
                        : WirelessSystem.unlinkNode(player.serverLevel(), matrix, node);
                if (ok) changed++;
            }
            player.sendSystemMessage(Component.translatable(packet.connect
                    ? "message.academy.matrix.nodes_linked" : "message.academy.matrix.nodes_unlinked", changed));
        });
    }

    /** Atomic server-side flood gate; exposed for deterministic GameTests. */
    public static boolean claimRequest(java.util.UUID player, BlockPos matrix, long gameTime) {
        final boolean[] playerAccepted = {false};
        LAST_PLAYER_REQUEST.compute(player, (ignored, previous) -> {
            if (previous == null || gameTime < previous || gameTime - previous >= COOLDOWN_TICKS) {
                playerAccepted[0] = true;
                return gameTime;
            }
            return previous;
        });
        if (!playerAccepted[0]) return false;
        RequestKey key = new RequestKey(player, matrix.immutable());
        final boolean[] accepted = {false};
        LAST_REQUEST.compute(key, (ignored, previous) -> {
            if (previous == null || gameTime < previous || gameTime - previous >= COOLDOWN_TICKS) {
                accepted[0] = true;
                return gameTime;
            }
            return previous;
        });
        return accepted[0];
    }

    public static void clearPlayer(java.util.UUID player) {
        LAST_PLAYER_REQUEST.remove(player);
        LAST_REQUEST.keySet().removeIf(key -> key.player.equals(player));
    }
    public static void clearMatrix(BlockPos matrix) {
        LAST_REQUEST.keySet().removeIf(key -> key.matrix.equals(matrix));
    }
    static void clearAll() { LAST_REQUEST.clear(); LAST_PLAYER_REQUEST.clear(); }
}
