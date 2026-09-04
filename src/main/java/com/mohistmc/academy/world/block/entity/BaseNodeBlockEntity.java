package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.energy.api.block.IWirelessNode;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.network.NetworkInputLimits;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * 无线节点基类 —— 实现 IWirelessNode 接口以参与 IF 能源系统。
 * @author Mgazul
 */
public abstract class BaseNodeBlockEntity extends AcademyContainerBlockEntity implements IWirelessNode {

    private static final double DEFAULT_MAX_ENERGY = 5000;
    private static final double DEFAULT_BANDWIDTH = 50;

    private double energy = 0;
    private double maxEnergy = DEFAULT_MAX_ENERGY;
    private double bandwidth = DEFAULT_BANDWIDTH;
    public static final String DEFAULT_NODE_NAME = "Unnamed";
    private String nodeName = DEFAULT_NODE_NAME;
    private String password = "";
    private UUID ownerUUID = null;
    /** Client-side mirror of the runtime network membership flag. */
    private boolean clientConnected = false;

    public BaseNodeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setItems(NonNullList.withSize(getContainerSize(), ItemStack.EMPTY));
    }

    @Override
    public int getContainerSize() {
        return 2;
    }

    /** 子类实现 — 返回节点等级对应的信号范围（IWirelessNode 接口） */
    @Override
    public abstract double getRange();

    /** 是否已连接到矩阵网络 */
    public boolean isConnected() {
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            return WirelessSystem.getNetwork(server, this) != null;
        }
        return clientConnected;
    }

    /** Reconcile authoritative network membership with the block model state. */
    public void refreshConnectionState() {
        if (level == null || level.isClientSide()) return;
        boolean connected = level instanceof net.minecraft.server.level.ServerLevel server
                && WirelessSystem.getNetwork(server, this) != null;
        BlockState state = getBlockState();
        var property = state.getBlock().getStateDefinition().getProperty("connected");
        if (property instanceof BooleanProperty connectedProperty
                && state.getValue(connectedProperty) != connected) {
            level.setBlock(worldPosition, state.setValue(connectedProperty, connected), 3);
        }
    }

    /**
     * Legacy TileNode performs both item transfers every server tick: slot 0
     * discharges an IF item into the node, then slot 1 charges an IF item from
     * the node.  Keep the two transactions bounded independently by this
     * node tier's bandwidth so energy can pass through without being created.
     */
    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        refreshConnectionState();

        int limit = !Double.isFinite(bandwidth) || bandwidth <= 0
                ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.floor(bandwidth));
        boolean chargingIn = false;
        boolean chargingOut = false;
        double nextEnergy = energy;

        if (limit > 0 && getItems().size() >= 2) {
            ItemStack input = getItems().get(0);
            int room = (int) Math.min(Integer.MAX_VALUE,
                    Math.max(0, Math.floor(maxEnergy - nextEnergy)));
            int pulled = com.mohistmc.academy.capability.EnergyItemHelper.extractEnergy(
                    input, Math.min(limit, room), false);
            if (pulled > 0) {
                nextEnergy += pulled;
                chargingIn = true;
            }

            ItemStack output = getItems().get(1);
            int available = (int) Math.min(Integer.MAX_VALUE,
                    Math.max(0, Math.floor(nextEnergy)));
            int pushed = com.mohistmc.academy.capability.EnergyItemHelper.receiveEnergy(
                    output, Math.min(limit, available), false);
            if (pushed > 0) {
                nextEnergy -= pushed;
                chargingOut = true;
            }
        }

        if (Double.compare(nextEnergy, energy) != 0) {
            setEnergy(nextEnergy);
        }
        if (chargingIn || chargingOut) {
            // ItemStack energy can change while the node's net energy stays
            // constant (direct pass-through), so persist the inventory too.
            setChanged();
        }
        // The numbered node side textures are energy quarters, not a transfer
        // animation.  This distinction was fixed in the final 1.12.2 branch.
        updateEnergyModel();
    }

    private void updateEnergyModel() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        var raw = state.getBlock().getStateDefinition().getProperty("working");
        if (!(raw instanceof IntegerProperty energyProperty)) return;
        int wanted = maxEnergy > 0 && Double.isFinite(maxEnergy)
                ? Math.clamp((int) Math.round(4.0 * energy / maxEnergy), 0, 4) : 0;
        if (state.getValue(energyProperty) != wanted) {
            level.setBlock(worldPosition, state.setValue(energyProperty, wanted), Block.UPDATE_CLIENTS);
        }
    }

    // ==================== IWirelessNode ====================

    @Override
    public double getMaxEnergy() { return maxEnergy; }

    @Override
    public double getEnergy() { return energy; }

    @Override
    public void setEnergy(double value) {
        this.energy = com.mohistmc.academy.energy.impl.EnergyBoundary.bounded(value, maxEnergy);
        setChanged();
        updateEnergyModel();
        // 节流同步到客户端，最多每秒刷新一次
        if (level != null && !level.isClientSide() && level.getGameTime() % 20 == 0) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public double getBandwidth() { return bandwidth; }

    @Override
    public int getCapacity() {
        // 子类可覆盖以提供不同的负载容量
        return 10;
    }

    @Override
    public String getNodeName() { return nodeName; }

    @Override
    public String getPassword() { return password; }

    // ==================== Setters ====================

    public boolean setNodeName(String name) {
        if (!NetworkInputLimits.validRequired(name, NetworkInputLimits.NODE_NAME)) return false;
        this.nodeName = name;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }
    public void setPassword(String password) {
        if (password == null || password.length() > 64) return;
        this.password = password;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
    public void setMaxEnergy(double maxEnergy) { this.maxEnergy = boundedFinite(maxEnergy, DEFAULT_MAX_ENERGY, 1_000_000_000); energy = com.mohistmc.academy.energy.impl.EnergyBoundary.bounded(energy, this.maxEnergy); setChanged(); updateEnergyModel(); }
    public void setBandwidth(double bandwidth) { this.bandwidth = boundedFinite(bandwidth, DEFAULT_BANDWIDTH, 1_000_000); setChanged(); }

    // ==================== Owner ====================

    public UUID getOwnerUUID() { return ownerUUID; }
    public void setOwnerUUID(UUID uuid) { this.ownerUUID = uuid; setChanged(); }

    /** Migrate a command-placed or pre-owner node only through an administrator interaction. */
    public boolean claimLegacyOwnerIfAbsent(net.minecraft.world.entity.player.Player player) {
        if (MachineOwnership.canClaimLegacy(ownerUUID, player)) {
            ownerUUID = player.getUUID();
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
            return true;
        }
        return false;
    }

    /** 检查玩家是否为所有者 */
    public boolean isOwner(net.minecraft.world.entity.player.Player player) {
        return ownerUUID != null && player != null && player.getUUID().equals(ownerUUID);
    }

    public boolean canManage(net.minecraft.world.entity.player.Player player) {
        return MachineOwnership.canManage(ownerUUID, player);
    }

    // ==================== NBT ====================

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("node_maxEnergy")) maxEnergy = boundedFinite(tag.getDouble("node_maxEnergy"), DEFAULT_MAX_ENERGY, 1_000_000_000);
        // Final 1.12.2 used energy/nodeName/password.  Earlier rebuilt jars
        // switched to snake_case without an importer, so an upgraded node
        // silently reopened as Unnamed. Prefer the current schema but accept
        // the official keys once and persist them in the current form later.
        if (tag.contains("node_energy")) energy = com.mohistmc.academy.energy.impl.EnergyBoundary.bounded(tag.getDouble("node_energy"), maxEnergy);
        else if (tag.contains("energy")) energy = com.mohistmc.academy.energy.impl.EnergyBoundary.bounded(tag.getDouble("energy"), maxEnergy);
        if (tag.contains("node_bandwidth")) bandwidth = boundedFinite(tag.getDouble("node_bandwidth"), DEFAULT_BANDWIDTH, 1_000_000);
        String loadedName = tag.contains("node_name") ? tag.getString("node_name")
                : tag.contains("nodeName") ? tag.getString("nodeName") : DEFAULT_NODE_NAME;
        // 0.0.15 and the original tile could persist names longer than the
        // current network field.  Loading is a migration boundary: preserve
        // the usable prefix instead of turning the whole saved identity into
        // Unnamed. New C2S edits remain strictly bounded and are never silently
        // truncated.
        loadedName = bounded(loadedName, NetworkInputLimits.NODE_NAME);
        nodeName = NetworkInputLimits.validRequired(loadedName, NetworkInputLimits.NODE_NAME)
                ? loadedName : DEFAULT_NODE_NAME;
        if (tag.contains("node_pass")) password = bounded(tag.getString("node_pass"), NetworkInputLimits.PASSWORD);
        else if (tag.contains("password")) password = bounded(tag.getString("password"), NetworkInputLimits.PASSWORD);
        if (tag.contains("ownerUUID")) ownerUUID = parseUuid(tag.getString("ownerUUID"));
        if (tag.contains("node_connected")) clientConnected = tag.getBoolean("node_connected");
    }

    private static String bounded(String value, int max) { return value == null ? "" : value.substring(0, Math.min(value.length(), max)); }
    private static double boundedFinite(double value, double fallback, double max) { return Double.isFinite(value) && value >= 0 ? Math.min(value, max) : fallback; }
    private static UUID parseUuid(String value) { try { return UUID.fromString(value); } catch (RuntimeException ignored) { return null; } }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putDouble("node_energy", energy);
        tag.putDouble("node_maxEnergy", maxEnergy);
        tag.putDouble("node_bandwidth", bandwidth);
        tag.putString("node_name", nodeName);
        tag.putString("node_pass", password);
        if (ownerUUID != null) tag.putString("ownerUUID", ownerUUID.toString());
    }

    // ==================== 客户端同步 ====================

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        // The normal save tag is server-private because it contains
        // node_pass.  Never reuse saveAdditional for a client update packet:
        // every tracking client would otherwise receive the plaintext node
        // password.  Only state required by the screen/model is mirrored.
        tag.putDouble("node_energy", energy);
        tag.putDouble("node_maxEnergy", maxEnergy);
        tag.putDouble("node_bandwidth", bandwidth);
        tag.putString("node_name", nodeName);
        if (ownerUUID != null) tag.putString("ownerUUID", ownerUUID.toString());
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            tag.putBoolean("node_connected", WirelessSystem.getNetwork(server, this) != null);
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
