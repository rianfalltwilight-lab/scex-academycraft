package com.mohistmc.academy.energy.impl;

import com.mohistmc.academy.energy.api.block.IWirelessGenerator;
import com.mohistmc.academy.energy.api.block.IWirelessNode;
import com.mohistmc.academy.energy.api.block.IWirelessReceiver;
import com.mohistmc.academy.energy.impl.VBlocks.VBlock;
import com.mohistmc.academy.energy.impl.VBlocks.VNGenerator;
import com.mohistmc.academy.energy.impl.VBlocks.VNNode;
import com.mohistmc.academy.energy.impl.VBlocks.VNReceiver;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * 节点连接 —— 一个节点 + M 个发电机 + K 个接收器。
 * 每 tick 先从发电机收集能量到节点，再从节点分配能量到接收器。
 */
public class NodeConn {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final WiWorldData data;
    private final VNNode node;

    private boolean disposed = false;
    private final List<VNReceiver> receivers = new LinkedList<>();
    private final List<VNGenerator> generators = new LinkedList<>();
    private final List<VNReceiver> toRemoveReceivers = new ArrayList<>();
    private final List<VNGenerator> toRemoveGenerators = new ArrayList<>();
    private boolean ticking;

    public NodeConn(WiWorldData data, VNNode node) {
        this.data = data;
        this.node = node;
    }

    public NodeConn(WiWorldData data, CompoundTag tag) {
        this.data = data;
        this.node = new VNNode(tag.getCompound("node"));

        ListTag recList = tag.getList("receivers", 10);
        for (int i = 0; i < recList.size(); ++i) {
            VNReceiver receiver = new VNReceiver(recList.getCompound(i));
            receivers.add(receiver);
            data.nodeLookup.put(receiver, this);
        }

        ListTag genList = tag.getList("generators", 10);
        for (int i = 0; i < genList.size(); ++i) {
            VNGenerator generator = new VNGenerator(genList.getCompound(i));
            generators.add(generator);
            data.nodeLookup.put(generator, this);
        }
    }

    // ==================== NBT ====================

    CompoundTag toNBT() {
        Level level = getLevel();
        CompoundTag ret = new CompoundTag();

        ListTag list = new ListTag();
        for (VNReceiver r : receivers) {
            if (!r.isLoaded(level) || r.get(level) != null) {
                list.add(r.toNBT());
            }
        }
        ret.put("receivers", list);

        list = new ListTag();
        for (VNGenerator g : generators) {
            if (!g.isLoaded(level) || g.get(level) != null) {
                list.add(g.toNBT());
            }
        }
        ret.put("generators", list);

        ret.put("node", node.toNBT());
        return ret;
    }

    // ==================== 生命周期 ====================

    public void dispose() { disposed = true; }
    public boolean isDisposed() { return disposed; }

    void onAdded(WiWorldData data) {
        data.nodeLookup.put(node, this);
    }

    void onCleanup(WiWorldData data) {
        // A user may already have been rebound to a newer NodeConn while this
        // old connection is waiting for its cleanup tick.  Conditional remove
        // prevents the old connection from deleting the new authoritative
        // lookup entry for the same virtual block.
        data.nodeLookup.remove(node, this);
        for (VNGenerator gen : generators) data.nodeLookup.remove(gen, this);
        for (VNReceiver rec : receivers) data.nodeLookup.remove(rec, this);
    }

    boolean validate() {
        Level level = getLevel();
        if (!disposed && node.isLoaded(level)) {
            if (node.get(level) == null || (generators.isEmpty() && receivers.isEmpty())) {
                disposed = true;
            }
        }
        return !disposed;
    }

    // ==================== 添加/移除 ====================

    boolean addReceiver(VNReceiver receiver) {
        if (receiver == null) return false;
        if (receivers.contains(receiver)) {
            if (!toRemoveReceivers.contains(receiver)) {
                data.nodeLookup.put(receiver, this);
                return true;
            }
            // Never let a newly placed machine at the same coordinates cancel
            // an explicit/stale removal while this connection is being
            // iterated.  Outside tick it is safe to finish the old removal and
            // treat the request as a fresh link.
            if (ticking) return false;
            toRemoveReceivers.remove(receiver);
            receivers.remove(receiver);
        }
        if (getLoad() >= getCapacity() || !checkRange(receiver))
            return false;

        Level level = getLevel();
        if (level != null) {
            NodeConn old = data.getNodeConnection(receiver.get(level));
            if (old != null) old.removeReceiver(receiver);
        }

        receivers.add(receiver);
        data.nodeLookup.put(receiver, this);
        return true;
    }

    /** Queue one receiver for removal; the connection itself remains intact. */
    void removeReceiver(VNReceiver receiver) {
        if (receiver != null && !toRemoveReceivers.contains(receiver)) {
            toRemoveReceivers.add(receiver);
        }
    }

    /**
     * Unlink exactly this receiver, matching the legacy WirelessSystem
     * semantics.  It deliberately does not dispose the node connection, so
     * other machines sharing the node keep working.
     */
    public boolean unlinkReceiver(VNReceiver receiver) {
        if (receiver == null || (!receivers.contains(receiver) && !toRemoveReceivers.contains(receiver))) {
            return false;
        }
        // The lookup is the authoritative answer used by menus and placement
        // code, so revoke it synchronously.  List mutation is deferred only
        // when a provider callback re-enters during tick iteration.
        data.nodeLookup.remove(receiver, this);
        if (ticking) {
            removeReceiver(receiver);
            return true;
        }
        toRemoveReceivers.remove(receiver);
        return receivers.remove(receiver);
    }

    boolean addGenerator(VNGenerator gen) {
        if (gen == null) return false;
        if (generators.contains(gen)) {
            if (!toRemoveGenerators.contains(gen)) {
                data.nodeLookup.put(gen, this);
                return true;
            }
            if (ticking) return false;
            toRemoveGenerators.remove(gen);
            generators.remove(gen);
        }
        if (getLoad() >= getCapacity() || !checkRange(gen))
            return false;

        Level level = getLevel();
        NodeConn old = data.getNodeConnection(gen.get(level));
        if (old != null) old.removeGenerator(gen);

        generators.add(gen);
        data.nodeLookup.put(gen, this);
        return true;
    }

    /** Queue one generator for removal; the connection itself remains intact. */
    void removeGenerator(VNGenerator gen) {
        if (gen != null && !toRemoveGenerators.contains(gen)) {
            toRemoveGenerators.add(gen);
        }
    }

    /** Unlink exactly this generator without destroying the shared node link. */
    public boolean unlinkGenerator(VNGenerator gen) {
        if (gen == null || (!generators.contains(gen) && !toRemoveGenerators.contains(gen))) {
            return false;
        }
        data.nodeLookup.remove(gen, this);
        if (ticking) {
            removeGenerator(gen);
            return true;
        }
        toRemoveGenerators.remove(gen);
        return generators.remove(gen);
    }

    private boolean checkRange(VBlock<?> block) {
        IWirelessNode inode = node.get(getLevel());
        double range = inode == null ? 1000 : inode.getRange();
        return block.distSq(node) <= range * range;
    }

    // ==================== Accessors ====================

    public IWirelessNode getNode() {
        return node.get(getLevel());
    }

    private Level getLevel() {
        return data.level;
    }

    public int getLoad() {
        return receivers.size() + generators.size();
    }

    public int getCapacity() {
        Level level = getLevel();
        IWirelessNode inode = level == null ? null : node.get(getLevel());
        return inode == null ? EnergyBoundary.MAX_CONNECTIONS : EnergyBoundary.capacity(inode.getCapacity());
    }

    // ==================== Tick ====================

    void tick() {
        ticking = true;
        try {
            validate();
            Level level = getLevel();
            if (!node.isLoaded(level)) return;

            IWirelessNode iNode = node.get(level);
            if (iNode == null) return;

            double transferLeft = EnergyBoundary.transfer(iNode.getBandwidth());

        // 1. 从发电机收集能量
            Collections.shuffle(generators);
            Iterator<VNGenerator> genIter = generators.iterator();
            while (transferLeft > 0 && genIter.hasNext()) {
                VNGenerator gen = genIter.next();
                if (!gen.isLoaded(level)) continue;

                IWirelessGenerator igen = gen.get(level);
                if (igen == null) {
                    removeGenerator(gen);
                } else {
                    double max = EnergyBoundary.energy(iNode.getMaxEnergy());
                    double cur = EnergyBoundary.bounded(iNode.getEnergy(), max);
                    double required = Math.min(transferLeft,
                            Math.min(EnergyBoundary.transfer(igen.getBandwidth()), max - cur));
                    double raw = igen.getProvidedEnergy(required);
                    double amt = EnergyBoundary.bounded(raw, required);

                    if (raw != amt) {
                        LOGGER.warn("Energy input overflow for generator {}", igen);
                        amt = required;
                    }

                    cur += amt;
                    iNode.setEnergy(cur);
                    transferLeft -= amt;
                }
            }

            // 2. 向接收器分配能量
            transferLeft = EnergyBoundary.transfer(iNode.getBandwidth());
            Collections.shuffle(receivers);
            Iterator<VNReceiver> recIter = receivers.iterator();
            while (transferLeft > 0 && recIter.hasNext()) {
                VNReceiver rec = recIter.next();
                if (!rec.isLoaded(level)) continue;

                IWirelessReceiver irec = rec.get(level);
                if (irec == null) {
                    removeReceiver(rec);
                } else {
                    double max = EnergyBoundary.energy(iNode.getMaxEnergy());
                    double cur = EnergyBoundary.bounded(iNode.getEnergy(), max);
                    double give = Math.min(cur, Math.min(transferLeft, EnergyBoundary.transfer(irec.getBandwidth())));
                    give = Math.min(EnergyBoundary.transfer(irec.getRequiredEnergy()), give);

                    double remainder = EnergyBoundary.bounded(irec.injectEnergy(give), give);
                    double accepted = give - remainder;
                    cur -= accepted;
                    transferLeft -= accepted;
                    iNode.setEnergy(cur);
                }
            }
        } finally {
            ticking = false;
            flushRemovals();
        }
    }

    private void flushRemovals() {
        // 清理待删除的发电机/接收器
        for (VNGenerator gen : toRemoveGenerators) {
            data.nodeLookup.remove(gen, this);
        }
        generators.removeAll(toRemoveGenerators);
        for (VNReceiver rec : toRemoveReceivers) {
            data.nodeLookup.remove(rec, this);
        }
        receivers.removeAll(toRemoveReceivers);

        toRemoveGenerators.clear();
        toRemoveReceivers.clear();
    }
}
