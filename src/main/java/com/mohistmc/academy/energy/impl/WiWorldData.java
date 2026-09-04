package com.mohistmc.academy.energy.impl;

import com.mohistmc.academy.energy.api.block.IWirelessGenerator;
import com.mohistmc.academy.energy.api.block.IWirelessMatrix;
import com.mohistmc.academy.energy.api.block.IWirelessNode;
import com.mohistmc.academy.energy.api.block.IWirelessReceiver;
import com.mohistmc.academy.energy.api.block.IWirelessUser;
import com.mohistmc.academy.energy.impl.VBlocks.VNGenerator;
import com.mohistmc.academy.energy.impl.VBlocks.VNNode;
import com.mohistmc.academy.energy.impl.VBlocks.VNReceiver;
import com.mohistmc.academy.energy.impl.VBlocks.VWMatrix;
import com.mohistmc.academy.energy.impl.VBlocks.VWNode;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

/**
 * 维度级无线能源世界数据。
 * 存储所有 WirelessNet 和 NodeConn，每 tick 更新。
 */
public class WiWorldData extends SavedData {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_ID = "academy_wen";

    // ==================== 工厂 ====================

    public static WiWorldData get(ServerLevel level) {
        WiWorldData data = level.getDataStorage().computeIfAbsent(
                FACTORY,
                DATA_ID
        );
        // Callers may create/link a network before the next server tick. Every
        // virtual block lookup requires the owning level immediately.
        data.level = level;
        return data;
    }

    public static WiWorldData getNonCreate(ServerLevel level) {
        var existing = level.getDataStorage().get(FACTORY, DATA_ID);
        if (existing != null) {
            existing.level = level;
        }
        return existing;
    }

    private static final SavedData.Factory<WiWorldData> FACTORY = new SavedData.Factory<>(
            WiWorldData::new,
            (tag, provider) -> {
                WiWorldData data = new WiWorldData();
                data.load(tag, provider);
                return data;
            }
    );

    // ==================== 实例 ====================

    Level level;

    public WiWorldData() {}

    private void load(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag.contains("net")) {
            CompoundTag netTag = tag.getCompound("net");
            loadNetwork(netTag);
        }
        if (tag.contains("node")) {
            CompoundTag nodeTag = tag.getCompound("node");
            loadNode(nodeTag);
        }
    }

    // ==================== 网络数据 ====================

    final Map<Object, WirelessNet> netLookup = new HashMap<>();
    final Set<WirelessNet> netList = new HashSet<>();
    private final List<WirelessNet> toRemove = new ArrayList<>();

    boolean createNetwork(IWirelessMatrix matrix, String ssid, String password) {
        VWMatrix vm = new VWMatrix(matrix);
        if (netLookup.containsKey(vm)) {
            WirelessNet old = netLookup.get(vm);
            doRemoveNetwork(old);
        }

        WirelessNet net = new WirelessNet(this, vm, ssid, password);
        doAddNetwork(net);
        return true;
    }

    boolean removeNetwork(IWirelessMatrix matrix) {
        WirelessNet net = netLookup.get(new VWMatrix(matrix));
        if (net == null) return false;
        doRemoveNetwork(net);
        setDirty();
        return true;
    }

    public Collection<WirelessNet> rangeSearch(int x, int y, int z, double range, int max) {
        Set<WirelessNet> set = new HashSet<>();
        if (level == null || !Double.isFinite(range) || range < 0 || max <= 0) return set;
        range = Math.min(range, 256);
        max = Math.min(max, 1024);

        // Search the authoritative network registry instead of sampling a coarse
        // 4-block grid.  Sampling could miss a matrix/node at arbitrary
        // coordinates, making newly linked networks appear undiscoverable.
        for (WirelessNet net : netList) {
            if (isNetworkDiscoverable(net, x, y, z, range)) {
                set.add(net);
                if (set.size() >= max) return set;
            }
        }
        return set;
    }

    /**
     * Exact legacy range-search predicate for one target network.
     *
     * <p>A node may discover a network through the Matrix <em>or any already
     * linked node</em> inside its own radio range, while the candidate node
     * still has to be inside the Matrix range.  Packet validation uses this
     * method instead of a direct node-to-Matrix distance check; the latter
     * incorrectly disabled the relay topology supported by 1.0.7.</p>
     */
    public boolean isNetworkDiscoverable(WirelessNet net, int x, int y, int z, double range) {
        return net != null && netList.contains(net) && net.validate()
                && net.hasLoadedEndpointWithin(x, y, z, range)
                && net.isInRange(x, y, z)
                && net.getLoad() < net.getCapacity();
    }

    public WirelessNet getNetwork(IWirelessMatrix matrix) {
        if (!(matrix instanceof BlockEntity)) return null;
        return privateGetNetwork(new VWMatrix(matrix));
    }

    public WirelessNet getNetwork(IWirelessNode node) {
        if (!(node instanceof BlockEntity)) return null;
        return privateGetNetwork(new VWNode(node));
    }

    private WirelessNet privateGetNetwork(Object key) {
        WirelessNet ret = netLookup.get(key);
        if (ret != null && ret.validate()) {
            return ret;
        }
        return null;
    }

    private void doRemoveNetwork(WirelessNet net) {
        netList.remove(net);
        net.onCleanup(this);
    }

    private void doAddNetwork(WirelessNet net) {
        net.level = level;
        netList.add(net);
        net.onCreate(this);
    }

    private void loadNetwork(CompoundTag tag) {
        if (!tag.contains("networks")) return;
        ListTag list = tag.getList("networks", 10);
        for (int i = 0; i < list.size(); ++i) {
            WirelessNet net = new WirelessNet(this, list.getCompound(i));
            doAddNetwork(net);
        }
    }

    private void saveNetwork(CompoundTag tag) {
        ListTag list = new ListTag();
        for (WirelessNet net : netList) {
            if (!net.isDisposed()) {
                list.add(net.toNBT());
            }
        }
        tag.put("networks", list);
    }

    // ==================== 节点连接数据 ====================

    final Map<Object, NodeConn> nodeLookup = new HashMap<>();
    final Set<NodeConn> nodeList = new HashSet<>();
    private final List<NodeConn> nToRemove = new ArrayList<>();

    public NodeConn getNodeConnection(IWirelessNode node) {
        if (!(node instanceof BlockEntity)) return null;
        VNNode vnn = new VNNode(node);
        NodeConn ret = privateGetNodeConn(vnn);
        if (ret == null) {
            doAddNode(ret = new NodeConn(this, vnn));
        }
        return ret;
    }

    /** Read-only lookup used by discovery/UI code; never creates an empty
     * connection as a side effect. */
    public NodeConn getExistingNodeConnection(IWirelessNode node) {
        if (!(node instanceof BlockEntity)) return null;
        return privateGetNodeConn(new VNNode(node));
    }

    public NodeConn getNodeConnection(IWirelessUser user) {
        if (!(user instanceof BlockEntity)) return null;
        if (user instanceof IWirelessGenerator) {
            return privateGetNodeConn(new VNGenerator((IWirelessGenerator) user));
        } else if (user instanceof IWirelessReceiver) {
            return privateGetNodeConn(new VNReceiver((IWirelessReceiver) user));
        }
        return null;
    }

    private NodeConn privateGetNodeConn(Object key) {
        NodeConn ret = nodeLookup.get(key);
        if (ret != null && ret.validate()) {
            return ret;
        }
        return null;
    }

    private void doAddNode(NodeConn conn) {
        nodeList.add(conn);
        conn.onAdded(this);
    }

    private void doRemoveNode(NodeConn conn) {
        nodeList.remove(conn);
        conn.onCleanup(this);
    }

    private void loadNode(CompoundTag tag) {
        if (!tag.contains("list")) return;
        ListTag list = tag.getList("list", 10);
        for (int i = 0; i < list.size(); ++i) {
            doAddNode(new NodeConn(this, list.getCompound(i)));
        }
    }

    private void saveNode(CompoundTag tag) {
        ListTag list = new ListTag();
        for (NodeConn c : nodeList) {
            if (!c.isDisposed()) {
                list.add(c.toNBT());
            }
        }
        tag.put("list", list);
    }

    // ==================== Tick ====================

    public void tick() {
        tickNetwork();
        tickNode();
    }

    private void tickNetwork() {
        for (WirelessNet net : toRemove) {
            doRemoveNetwork(net);
        }
        toRemove.clear();

        for (WirelessNet net : netList) {
            if (net.isDisposed()) {
                toRemove.add(net);
            } else {
                net.level = level;
                net.tick();
            }
        }
    }

    private void tickNode() {
        for (NodeConn nc : nToRemove) {
            doRemoveNode(nc);
        }
        nToRemove.clear();

        for (NodeConn conn : nodeList) {
            if (conn.isDisposed()) {
                nToRemove.add(conn);
            } else {
                conn.tick();
            }
        }
    }

    // ==================== SavedData ====================

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag netTag = new CompoundTag();
        saveNetwork(netTag);
        tag.put("net", netTag);

        CompoundTag nodeTag = new CompoundTag();
        saveNode(nodeTag);
        tag.put("node", nodeTag);

        return tag;
    }

    @Override
    public boolean isDirty() {
        return true;
    }
}
