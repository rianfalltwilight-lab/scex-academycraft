package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.energy.impl.WiWorldData;
import com.mohistmc.academy.energy.impl.WirelessNet;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import com.mohistmc.academy.world.menu.BaseNodeMenu;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client to server discovery for the original node-to-Matrix wireless page.
 *
 * <p>This is deliberately separate from {@link RequestNodesPacket}: a machine
 * discovers standalone nodes, while a node discovers initialized Matrix
 * networks. AcademyCraft 1.0.7 exposed both flows as different variants of
 * the same wireless page.</p>
 */
public record RequestMatrixNetworksPacket(BlockPos nodePos) implements CustomPacketPayload {
    private static final long REQUEST_COOLDOWN_TICKS = 10;
    /** See {@link RequestNodesPacket}: discovery throttling is menu-scoped. */
    private record RequestKey(UUID playerId, int containerId, BlockPos nodePos) {}

    private static final Map<RequestKey, Long> LAST_REQUEST_TICK = new HashMap<>();

    public static final Type<RequestMatrixNetworksPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "request_matrix_networks"));
    public static final StreamCodec<ByteBuf, RequestMatrixNetworksPacket> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, RequestMatrixNetworksPacket::nodePos,
                    RequestMatrixNetworksPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestMatrixNetworksPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            ServerLevel level = player.serverLevel();
            if (!(player.containerMenu instanceof BaseNodeMenu menu)
                    || !packet.nodePos().equals(menu.pos) || !menu.stillValid(player)
                    || !level.isLoaded(packet.nodePos())
                    || player.distanceToSqr(packet.nodePos().getX() + 0.5,
                    packet.nodePos().getY() + 0.5, packet.nodePos().getZ() + 0.5) > 64.0
                    || !level.mayInteract(player, packet.nodePos())
                    || !(level.getBlockEntity(packet.nodePos()) instanceof BaseNodeBlockEntity node)) {
                return;
            }

            long now = level.getGameTime();
            RequestKey requestKey = new RequestKey(player.getUUID(), menu.containerId,
                    packet.nodePos().immutable());
            LAST_REQUEST_TICK.keySet().removeIf(key -> key.playerId().equals(player.getUUID())
                    && !key.equals(requestKey));
            Long previous = LAST_REQUEST_TICK.get(requestKey);
            if (previous != null && now - previous < REQUEST_COOLDOWN_TICKS) return;
            LAST_REQUEST_TICK.put(requestKey, now);

            // Authorisation failures still produce a correlated empty response so
            // the client can leave its loading state.  Apply the same menu-scoped
            // cooldown first, otherwise a forged client could turn the denial
            // path into an unbounded response/message amplifier.
            if (!node.canManage(player)) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "message.academy.node.owner_only"));
                sendEmpty(player, menu, packet.nodePos());
                return;
            }

            WiWorldData data = WiWorldData.getNonCreate(level);
            WirelessNet linked = data == null ? null : data.getNetwork(node);
            Collection<WirelessNet> found = data == null ? List.of()
                    : data.rangeSearch(packet.nodePos().getX(), packet.nodePos().getY(),
                    packet.nodePos().getZ(), node.getRange(), 20);

            List<WirelessNet> candidates = orderedNetworks(found, packet.nodePos(), linked, 20);
            // A full network is absent from rangeSearch, but the node's current
            // network must remain visible so the player can disconnect it.
            if (linked != null && !candidates.contains(linked)) candidates.add(0, linked);

            CompoundTag response = new CompoundTag();
            ListTag list = new ListTag();
            int connectedIndex = -1;
            int index = 0;
            for (WirelessNet network : candidates) {
                if (network == null || network.isDisposed()) continue;
                var matrixEndpoint = network.getMatrix();
                if (!(matrixEndpoint instanceof MatrixBlockEntity matrix)) continue;
                BlockPos matrixPos = matrix.getBlockPos();
                if (!level.isLoaded(matrixPos) || !level.mayInteract(player, matrixPos)) continue;
                boolean connected = network == linked;
                if (!connected && (!matrix.isOperational()
                        || network.getLoad() >= network.getCapacity())) continue;

                CompoundTag entry = new CompoundTag();
                entry.putString("ssid", network.getSSID());
                entry.putBoolean("needAuth", !network.getPassword().isEmpty());
                entry.putLong("pos", matrixPos.asLong());
                entry.putInt("load", Math.max(0, network.getLoad()));
                entry.putInt("capacity", Math.max(0, network.getCapacity()));
                list.add(entry);
                if (connected) connectedIndex = index;
                index++;
            }

            response.put("networks", list);
            response.putInt("connectedIndex", connectedIndex);
            response.putLong("nodePos", packet.nodePos().asLong());
            response.putInt("containerId", menu.containerId);
            SafePayloadSender.send(player, new MatrixNetworkListSyncPacket(response));
        });
    }

    private static void sendEmpty(ServerPlayer player, BaseNodeMenu menu, BlockPos nodePos) {
        CompoundTag response = new CompoundTag();
        response.put("networks", new ListTag());
        response.putInt("connectedIndex", -1);
        response.putLong("nodePos", nodePos.asLong());
        response.putInt("containerId", menu.containerId);
        response.putBoolean("accessDenied", true);
        SafePayloadSender.send(player, new MatrixNetworkListSyncPacket(response));
    }

    static List<WirelessNet> orderedNetworks(Collection<WirelessNet> networks, BlockPos center,
                                               WirelessNet linked, int max) {
        if (networks == null || networks.isEmpty() || center == null || max <= 0) {
            return new ArrayList<>();
        }
        List<NodeCandidateOrdering.Candidate<WirelessNet>> sortable = new ArrayList<>();
        for (WirelessNet network : networks) {
            if (network == null || !(network.getMatrix() instanceof BlockEntity matrix)) continue;
            BlockPos pos = matrix.getBlockPos();
            sortable.add(new NodeCandidateOrdering.Candidate<>(network,
                    network == linked ? Long.MIN_VALUE : pos.asLong(), center.distSqr(pos),
                    pos.getY(), pos.getX(), pos.getZ()));
        }
        return new ArrayList<>(NodeCandidateOrdering.order(sortable, Long.MIN_VALUE, max));
    }

    public static void forgetPlayer(UUID playerId) {
        LAST_REQUEST_TICK.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    public static void clearAll() {
        LAST_REQUEST_TICK.clear();
    }
}
