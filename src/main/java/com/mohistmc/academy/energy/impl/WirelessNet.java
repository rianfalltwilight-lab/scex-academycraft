package com.mohistmc.academy.energy.impl;

import com.mohistmc.academy.energy.api.block.IWirelessMatrix;
import com.mohistmc.academy.energy.api.block.IWirelessNode;
import com.mohistmc.academy.energy.impl.VBlocks.VWMatrix;
import com.mohistmc.academy.energy.impl.VBlocks.VWNode;
import com.mohistmc.academy.utils.MathUtils;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * 无线网络 —— 一个矩阵 + N 个节点，在节点间均衡能量。
 */
public class WirelessNet {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int UPDATE_INTERVAL = 40;
    private static final double BUFFER_MAX = 2000;

    private final WiWorldData data;
    Level level;

    private final List<VWNode> nodes = new LinkedList<>();
    private final List<VWNode> toRemoveNodes = new ArrayList<>();
    private VWMatrix matrix;

    private String ssid;
    private String password;

    private double buffer;

    private boolean disposed = false;

    WirelessNet(WiWorldData data, VWMatrix matrix, String ssid, String pass) {
        this.data = data;
        this.matrix = matrix;
        this.ssid = ssid;
        this.password = pass;
    }

    WirelessNet(WiWorldData data, CompoundTag tag) {
        this.data = data;

        matrix = new VWMatrix(tag.getCompound("matrix"));

        ssid = bounded(tag.getString("ssid"));
        password = bounded(tag.getString("password"));
        buffer = EnergyBoundary.bounded(tag.getDouble("buffer"), BUFFER_MAX);

        ListTag list = tag.getList("list", 10); // 10 = TAG_Compound
        for (int i = 0; i < list.size(); ++i) {
            doAddNode(new VWNode(list.getCompound(i)));
        }
    }

    CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("matrix", matrix.toNBT());
        tag.putString("ssid", ssid);
        tag.putString("password", password);
        tag.putDouble("buffer", buffer);

        ListTag list = new ListTag();
        for (VWNode vn : nodes) {
            if (!vn.isLoaded(level) || vn.get(level) != null) {
                list.add(vn.toNBT());
            }
        }
        tag.put("list", list);

        return tag;
    }

    // ==================== Accessors ====================

    public String getSSID() { return ssid; }
    public void setSSID(String ssid) { if (ssid != null && ssid.length() <= 64) this.ssid = ssid; }
    public String getPassword() { return password; }

    public boolean resetPassword(String np) {
        if (np == null || np.length() > 64) return false;
        password = np;
        return true;
    }

    private static String bounded(String value) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), 64));
    }

    public boolean isDisposed() { return disposed; }

    public int getLoad() { return nodes.size(); }

    public int getCapacity() {
        IWirelessMatrix imat = matrix.get(level);
        return imat == null ? 0 : EnergyBoundary.capacity(imat.getCapacity());
    }

    public IWirelessMatrix getMatrix() {
        return matrix.get(level);
    }

    // ==================== 生命周期 ====================

    void dispose() {
        disposed = true;
    }

    void onCreate(WiWorldData data) {
        data.netLookup.put(matrix, this);
    }

    void onCleanup(WiWorldData data) {
        data.netLookup.remove(matrix, this);
        for (VWNode n : nodes) {
            data.netLookup.remove(n, this);
        }
        // Refresh after every lookup has been detached. Otherwise the node's
        // model can remain in its connected state until a later tick (or until
        // its chunk is loaded again) after the matrix is removed.
        for (VWNode n : nodes) {
            IWirelessNode node = n.get(level);
            if (node instanceof com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity be) {
                be.refreshConnectionState();
            }
        }
    }

    // ==================== 节点管理 ====================

    boolean addNode(VWNode node, String password) {
        if (node == null) return false;
        if (password == null || this.password == null || password.length() > 64 || !password.equals(this.password))
            return false;

        IWirelessMatrix imat = matrix.get(level);
        if (imat == null) return false;

        double r = Math.min(EnergyBoundary.nonNegative(imat.getRange()), 256);
        if (node.distSq(matrix) > r * r)
            return false;

        // Linking is idempotent.  The old implementation removed a node from
        // its current network and then appended it again even when that
        // network was this one.  A double click (or a reconnect packet sent
        // before the next tick) therefore created duplicate virtual nodes,
        // consumed capacity twice, and could make a matrix appear broken.
        if (nodes.contains(node)) {
            toRemoveNodes.remove(node);
            data.netLookup.put(node, this);
            return true;
        }
        if (toRemoveNodes.remove(node)) {
            // The node was queued for unlink but has been linked again before
            // the cleanup tick.  Keep the existing entry and restore lookup.
            data.netLookup.put(node, this);
            return true;
        }

        if (getLoad() >= getCapacity())
            return false;

        // 检查节点是否已加入其他网络
        WirelessNet other = data.getNetwork(node.get(level));
        if (other != null && other != this) {
            // Move immediately so a reconnect cannot leave the node mapped to
            // the old network until its next tick.  This also prevents the old
            // network from continuing to transfer energy for one extra cycle.
            other.removeNodeImmediately(node);
            data.setDirty();
        }

        doAddNode(node);
        data.setDirty();
        return true;
    }

    private void doAddNode(VWNode node) {
        nodes.add(node);
        data.netLookup.put(node, this);
    }

    void removeNode(VWNode node) {
        if (node != null && !toRemoveNodes.contains(node)) {
            toRemoveNodes.add(node);
        }
    }

    void removeNodeImmediately(VWNode node) {
        data.netLookup.remove(node, this);
        nodes.remove(node);
        toRemoveNodes.remove(node);
    }

    // ==================== 验证 ====================

    boolean validate() {
        if (matrix.isLoaded(level)) {
            IWirelessMatrix mat = matrix.get(level);
            if (mat == null) {
                disposed = true;
            }
        }
        return !disposed;
    }

    boolean isInRange(int x, int y, int z) {
        IWirelessMatrix imat = matrix.get(level);
        if (imat == null) return false;
        double r = Math.min(EnergyBoundary.nonNegative(imat.getRange()), 256);
        return MathUtils.distanceSq(x, y, z, matrix.x, matrix.y, matrix.z) <= r * r;
    }

    /**
     * Matches the first half of the legacy {@code WiWorldData.rangeSearch}
     * contract: the search volume must contain either the Matrix or one of its
     * already-linked nodes. The previous registry-only port ignored the
     * caller's range entirely, so a node UI could advertise a network that its
     * own radio could not discover.
     */
    boolean hasLoadedEndpointWithin(int x, int y, int z, double range) {
        if (level == null || !Double.isFinite(range) || range < 0) return false;
        double boundedRange = Math.min(range, 256.0);
        double rangeSquared = boundedRange * boundedRange;

        IWirelessMatrix matrixEndpoint = matrix.get(level);
        if (matrixEndpoint instanceof net.minecraft.world.level.block.entity.BlockEntity matrixEntity
                && matrixEntity.getBlockPos().distSqr(new net.minecraft.core.BlockPos(x, y, z)) <= rangeSquared) {
            return true;
        }
        net.minecraft.core.BlockPos center = new net.minecraft.core.BlockPos(x, y, z);
        for (VWNode virtualNode : nodes) {
            IWirelessNode nodeEndpoint = virtualNode.get(level);
            if (nodeEndpoint instanceof net.minecraft.world.level.block.entity.BlockEntity nodeEntity
                    && nodeEntity.getBlockPos().distSqr(center) <= rangeSquared) {
                return true;
            }
        }
        return false;
    }

    // ==================== Tick ====================

    void tick() {
        validate();
        if (!matrix.isLoaded(level)) return;

        IWirelessMatrix imat = matrix.get(level);
        if (imat == null) {
            dispose();
            return;
        }

        // 随机打乱节点列表，避免总对同一个节点均衡
        Collections.shuffle(nodes);

        double sum = 0, maxSum = 0;
        for (VWNode vn : nodes) {
            if (vn.isLoaded(level)) {
                IWirelessNode node = vn.get(level);
                if (node == null) {
                    removeNode(vn);
                } else {
                    double max = EnergyBoundary.energy(node.getMaxEnergy());
                    sum = EnergyBoundary.saturatedAdd(sum, EnergyBoundary.bounded(node.getEnergy(), max), EnergyBoundary.MAX_NETWORK_ENERGY);
                    maxSum = EnergyBoundary.saturatedAdd(maxSum, max, EnergyBoundary.MAX_NETWORK_ENERGY);
                }
            }
        }

        // 清理待删除节点
        for (VWNode node : toRemoveNodes) {
            data.netLookup.remove(node, this);
        }
        nodes.removeAll(toRemoveNodes);
        toRemoveNodes.clear();

        double percent = EnergyBoundary.finiteRatio(sum, maxSum);
        double transferLeft = EnergyBoundary.transfer(imat.getBandwidth());

        for (VWNode vn : nodes) {
            if (!vn.isLoaded(level)) continue;
            IWirelessNode node = vn.get(level);
            if (node == null) continue;

            double max = EnergyBoundary.energy(node.getMaxEnergy());
            double cur = EnergyBoundary.bounded(node.getEnergy(), max);
            double targ = max * percent;
            double delta = targ - cur;
            delta = Math.signum(delta) * Math.min(Math.abs(delta),
                    Math.min(transferLeft, EnergyBoundary.transfer(node.getBandwidth())));
            if (!Double.isFinite(delta)) delta = 0;

            if (buffer + delta > BUFFER_MAX) {
                delta = BUFFER_MAX - buffer;
            } else if (buffer + delta < 0) {
                delta = -buffer;
            }

            transferLeft -= Math.abs(delta);
            buffer = EnergyBoundary.bounded(buffer + delta, BUFFER_MAX);
            node.setEnergy(cur + delta);

            if (transferLeft == 0) break;
        }
    }

    private void debug(Object msg) {
        LOGGER.debug("WN:{}", msg);
    }
}
