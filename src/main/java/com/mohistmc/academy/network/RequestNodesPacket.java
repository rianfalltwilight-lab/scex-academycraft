package com.mohistmc.academy.network;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.energy.api.block.IWirelessGenerator;
import com.mohistmc.academy.energy.api.block.IWirelessNode;
import com.mohistmc.academy.energy.api.block.IWirelessReceiver;
import com.mohistmc.academy.energy.impl.NodeConn;
import com.mohistmc.academy.energy.impl.WiWorldData;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import com.mohistmc.academy.world.menu.AcademyMenu;

/**
 * 客户端→服务端：请求附近的无线节点列表。
 */
public record RequestNodesPacket(BlockPos machinePos) implements CustomPacketPayload {

    /**
     * The limiter belongs to one concrete menu session, not merely to the
     * player.  Vanilla assigns a new container id whenever a machine is
     * reopened.  Keying only by UUID made a perfectly valid request from that
     * new menu disappear when the player switched machines within ten ticks;
     * the resulting wireless page looked as if no nodes existed.
     */
    private record RequestKey(UUID playerId, int containerId, BlockPos machinePos) {}

    private static final Map<RequestKey, Long> LAST_REQUEST_TICK = new HashMap<>();
    private static final long REQUEST_COOLDOWN_TICKS = 10;

    public static void forgetPlayer(UUID playerId) {
        LAST_REQUEST_TICK.keySet().removeIf(key -> key.playerId().equals(playerId));
    }
    public static void clearAll() { LAST_REQUEST_TICK.clear(); }

    public static final Type<RequestNodesPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, "request_nodes"));

    public static final StreamCodec<ByteBuf, RequestNodesPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestNodesPacket::machinePos,
                    RequestNodesPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestNodesPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ServerLevel level = player.serverLevel();
                if (!(player.containerMenu instanceof AcademyMenu menu)
                        || !packet.machinePos().equals(menu.pos) || !menu.stillValid(player)) return;
                if (!level.isLoaded(packet.machinePos())
                        || player.distanceToSqr(packet.machinePos().getX() + 0.5, packet.machinePos().getY() + 0.5,
                        packet.machinePos().getZ() + 0.5) > 64.0
                        || !level.mayInteract(player, packet.machinePos())) return;

                BlockEntity machineBe = level.getBlockEntity(packet.machinePos());
                if (!(machineBe instanceof IWirelessGenerator) && !(machineBe instanceof IWirelessReceiver)) return;

                long now = level.getGameTime();
                RequestKey requestKey = new RequestKey(player.getUUID(), menu.containerId,
                        packet.machinePos().immutable());
                // Retain at most the current menu-session entry for a player.
                // Besides bounding the map, this deliberately lets a freshly
                // opened machine answer immediately even if the previous menu
                // requested a list on the preceding tick.
                LAST_REQUEST_TICK.keySet().removeIf(key -> key.playerId().equals(player.getUUID())
                        && !key.equals(requestKey));
                Long previous = LAST_REQUEST_TICK.get(requestKey);
                if (previous != null && now - previous < REQUEST_COOLDOWN_TICKS) return;
                // Advance the limiter only for a request that is actually
                // accepted.  Updating it before the check allowed a burst of
                // rejected refreshes to slide the cooldown forever, leaving
                // the wireless page waiting for a response that never came.
                LAST_REQUEST_TICK.put(requestKey, now);

                WiWorldData data = WiWorldData.getNonCreate(level);

                // Legacy WirelessHelper scans a 20-block neighbourhood, then
                // applies each node's own range.  Do both checks here so the UI
                // cannot offer a node that ConnectToNodePacket will reject.
                Set<BlockPos> foundNodes = new HashSet<>();
                BlockPos center = packet.machinePos();
                int range = 20;

                for (int dx = -range; dx <= range; dx++) {
                    for (int dz = -range; dz <= range; dz++) {
                        int distXZ = dx * dx + dz * dz;
                        if (distXZ > range * range) continue;
                        int maxDy = (int) Math.sqrt(range * range - distXZ);
                        for (int dy = -maxDy; dy <= maxDy; dy++) {
                            BlockPos bp = center.offset(dx, dy, dz);
                            if (!level.isLoaded(bp)) continue;
                            BlockEntity be = level.getBlockEntity(bp);
                            if (be instanceof IWirelessNode node && level.mayInteract(player, bp)) {
                                double nodeRange = Math.max(0.0, Math.min(256.0, node.getRange()));
                                if (center.distSqr(bp) <= nodeRange * nodeRange) {
                                    foundNodes.add(bp.immutable());
                                }
                            }
                        }
                    }
                }

                // 检查当前机器是否已连接到某个节点
                // BlockPos.ZERO is a valid machine/node position.  Use an
                // impossible sentinel so an unconnected machine cannot
                // accidentally mark a node at (0,0,0) as selected.
                long connectedPos = Long.MIN_VALUE;
                if (data != null) {
                    NodeConn existingConn = null;
                    if (machineBe instanceof IWirelessGenerator gen) {
                        existingConn = data.getNodeConnection(gen);
                    } else if (machineBe instanceof IWirelessReceiver rec) {
                        existingConn = data.getNodeConnection(rec);
                    }
                    if (existingConn != null) {
                        com.mohistmc.academy.energy.api.block.IWirelessNode node = existingConn.getNode();
                        if (node instanceof BlockEntity nodeBe) {
                            connectedPos = nodeBe.getBlockPos().asLong();
                        }
                    }
                }

                CompoundTag response = new CompoundTag();
                ListTag nodeList = new ListTag();
                int index = 0;
                int connectedIndex = -1;

                // HashSet iteration is deliberately unspecified.  Truncating it
                // directly could omit the machine's existing connection in a
                // dense installation, making the authoritative link look lost
                // and removing the only disconnect affordance.  Keep that node
                // first and make every other row stable and proximity ordered.
                for (BlockPos nodePos : orderedCandidates(foundNodes, center, connectedPos, foundNodes.size())) {
                    BlockEntity be = level.getBlockEntity(nodePos);
                    // Legacy NodeConn topology is independent from Matrix membership:
                    // a standalone node must be discoverable by nearby generators and
                    // receivers before (or without ever) joining a matrix network.
                    if (!(be instanceof IWirelessNode node)) continue;

                    // WirelessHelper.getNodesInRange in 1.0.7 only exposed nodes
                    // that still had a free connection slot.  Keep the machine's
                    // current node visible even when it is full so the client can
                    // still identify and disconnect that existing link.
                    NodeConn conn = data != null ? data.getExistingNodeConnection(node) : null;
                    boolean connected = nodePos.asLong() == connectedPos;
                    if (!connected && conn != null && conn.getLoad() >= conn.getCapacity()) {
                        continue;
                    }

                    CompoundTag nodeTag = new CompoundTag();
                    nodeTag.putString("name", node.getNodeName());
                    nodeTag.putBoolean("needAuth", !node.getPassword().isEmpty());
                    nodeTag.putLong("pos", nodePos.asLong());
                    nodeTag.putInt("index", index);

                    // 节点连接信息
                    if (conn != null) {
                        nodeTag.putInt("load", conn.getLoad());
                        nodeTag.putInt("capacity", conn.getCapacity());
                    } else {
                        nodeTag.putInt("load", 0);
                        nodeTag.putInt("capacity", node.getCapacity());
                    }

                    nodeList.add(nodeTag);

                    if (connected) {
                        connectedIndex = index;
                    }

                    index++;
                    // Apply the payload limit after full/unavailable nodes are
                    // filtered.  Truncating candidates first can yield an empty
                    // page even when the 33rd nearest node is linkable.
                    if (index >= 32) break;
                }

                response.put("nodes", nodeList);
                response.putInt("connectedIndex", connectedIndex);
                response.putLong("machinePos", packet.machinePos().asLong());
                response.putInt("containerId", menu.containerId);
                SafePayloadSender.send(player, new NodeListSyncPacket(response));
            }
        });
    }

    static List<BlockPos> orderedCandidates(Collection<BlockPos> candidates, BlockPos center,
                                             long connectedPos, int max) {
        if (candidates == null || candidates.isEmpty() || center == null || max <= 0) {
            return List.of();
        }
        List<NodeCandidateOrdering.Candidate<BlockPos>> sortable = new ArrayList<>(candidates.size());
        for (BlockPos pos : candidates) {
            sortable.add(new NodeCandidateOrdering.Candidate<>(pos, pos.asLong(), center.distSqr(pos),
                    pos.getY(), pos.getX(), pos.getZ()));
        }
        return NodeCandidateOrdering.order(sortable, connectedPos, max);
    }
}
