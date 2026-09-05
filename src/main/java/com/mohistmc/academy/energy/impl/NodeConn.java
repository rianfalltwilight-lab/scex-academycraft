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
            if (!receivers.contains(receiver)) receivers.add(receiver);
            NodeConn previous = data.nodeLookup.put(receiver, this);
            if (previous != null && previous != this) previous.unlinkReceiver(receiver);
        }

        ListTag genList = tag.getList("generators", 10);
        for (int i = 0; i < genList.size(); ++i) {
            VNGenerator generator = new VNGenerator(genList.getCompound(i));
            if (!generators.contains(generator)) generators.add(generator);
            NodeConn previous = data.nodeLookup.put(generator, this);
            if (previous != null && previous != this) previous.unlinkGenerator(generator);
        }
    }

    // ==================== NBT ====================

    CompoundTag toNBT() {
        Level level = getLevel();
        CompoundTag ret = new CompoundTag();

        ListTag list = new ListTag();
        for (VNReceiver r : receivers) {
            if (data.nodeLookup.get(r) == this
                    && (!r.isLoaded(level) || r.get(level) != null)) {
                list.add(r.toNBT());
            }
        }
        ret.put("receivers", list);

        list = new ListTag();
        for (VNGenerator g : generators) {
            if (data.nodeLookup.get(g) == this
                    && (!g.isLoaded(level) || g.get(level) != null)) {
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
        if (receiver == null || disposed) return false;
        if (receivers.contains(receiver) && data.nodeLookup.get(receiver) == this) return true;
        if (getLoad() >= getCapacity() || !checkRange(receiver)) return false;
        NodeConn old = data.nodeLookup.get(receiver);
        if (old != null) old.unlinkReceiver(receiver);
        // Iteration uses a snapshot, so A -> B -> A during an energy callback
        // can replace the authoritative edge immediately without duplicates.
        if (!receivers.contains(receiver)) receivers.add(receiver);
        data.nodeLookup.put(receiver, this);
        return true;
    }

    void removeReceiver(VNReceiver receiver) { unlinkReceiver(receiver); }

    /** Unlink exactly one machine; all other machines keep their connection. */
    public boolean unlinkReceiver(VNReceiver receiver) {
        if (receiver == null) return false;
        data.nodeLookup.remove(receiver, this);
        return receivers.removeIf(receiver::equals);
    }

    boolean addGenerator(VNGenerator generator) {
        if (generator == null || disposed) return false;
        if (generators.contains(generator) && data.nodeLookup.get(generator) == this) return true;
        if (getLoad() >= getCapacity() || !checkRange(generator)) return false;
        NodeConn old = data.nodeLookup.get(generator);
        if (old != null) old.unlinkGenerator(generator);
        if (!generators.contains(generator)) generators.add(generator);
        data.nodeLookup.put(generator, this);
        return true;
    }

    void removeGenerator(VNGenerator generator) { unlinkGenerator(generator); }

    public boolean unlinkGenerator(VNGenerator generator) {
        if (generator == null) return false;
        data.nodeLookup.remove(generator, this);
        return generators.removeIf(generator::equals);
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
        if (!validate()) return;
        Level level = getLevel();
        if (!node.isLoaded(level)) return;
        IWirelessNode iNode = node.get(level);
        if (iNode == null) return;
        double transferLeft = EnergyBoundary.transfer(iNode.getBandwidth());

        // Callbacks may unlink/rebind users or create another connection. Each
        // endpoint present at the start of this phase is considered at most once.
        List<VNGenerator> generatorSnapshot = new ArrayList<>(generators);
        Collections.shuffle(generatorSnapshot);
        for (VNGenerator generator : generatorSnapshot) {
            if (transferLeft <= 0 || disposed) break;
            if (data.nodeLookup.get(generator) != this || !generator.isLoaded(level)) continue;
            IWirelessGenerator source = generator.get(level);
            if (source == null) {
                removeGenerator(generator);
                continue;
            }
            double max = EnergyBoundary.energy(iNode.getMaxEnergy());
            double current = EnergyBoundary.bounded(iNode.getEnergy(), max);
            double required = Math.min(transferLeft,
                    Math.min(EnergyBoundary.transfer(source.getBandwidth()), max - current));
            double raw = source.getProvidedEnergy(required);
            double amount = EnergyBoundary.bounded(raw, required);
            if (raw != amount) LOGGER.warn("Energy input overflow for generator {}", source);
            iNode.setEnergy(current + amount);
            transferLeft -= amount;
        }

        transferLeft = EnergyBoundary.transfer(iNode.getBandwidth());
        List<VNReceiver> receiverSnapshot = new ArrayList<>(receivers);
        Collections.shuffle(receiverSnapshot);
        for (VNReceiver receiver : receiverSnapshot) {
            if (transferLeft <= 0 || disposed) break;
            if (data.nodeLookup.get(receiver) != this || !receiver.isLoaded(level)) continue;
            IWirelessReceiver target = receiver.get(level);
            if (target == null) {
                removeReceiver(receiver);
                continue;
            }
            double max = EnergyBoundary.energy(iNode.getMaxEnergy());
            double current = EnergyBoundary.bounded(iNode.getEnergy(), max);
            double give = Math.min(current,
                    Math.min(transferLeft, EnergyBoundary.transfer(target.getBandwidth())));
            give = Math.min(EnergyBoundary.transfer(target.getRequiredEnergy()), give);
            double remainder = EnergyBoundary.bounded(target.injectEnergy(give), give);
            double accepted = give - remainder;
            iNode.setEnergy(current - accepted);
            transferLeft -= accepted;
        }
    }
}