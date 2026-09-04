package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.energy.api.block.IWirelessMatrix;
import com.mohistmc.academy.world.AcademyBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;

/**
 * 无线矩阵方块实体 —— 无线能源网络的核心,实现 IWirelessMatrix 接口以参与 IF 能源系统。
 * @author Mgazul
 */
public class MatrixBlockEntity extends AcademyContainerBlockEntity implements IWirelessMatrix {

    /** Final 1.12.2 {@code ContainerMatrix}/{@code TileMatrix} slot order. */
    public static final int PLATE_SLOT_0 = 0;
    public static final int PLATE_SLOT_1 = 1;
    public static final int PLATE_SLOT_2 = 2;
    public static final int CORE_SLOT = 3;

    private static final int DEFAULT_CAPACITY = 8;
    private static final double DEFAULT_BANDWIDTH = 60;
    private static final double DEFAULT_RANGE = 24;

    private String ssid = "";
    private String password = "";
    private int capacity = DEFAULT_CAPACITY;
    private double bandwidth = DEFAULT_BANDWIDTH;
    private double range = DEFAULT_RANGE;
    private UUID ownerUUID = null;
    private boolean initialized = false;
    private boolean syncedHasPassword = false;

    public MatrixBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyBlockEntities.MATRIX.get(), pos, state);
        setItems(NonNullList.withSize(getContainerSize(), ItemStack.EMPTY));
    }

    @Override public int getContainerSize() { return 4; }
    public int initializationCoreLevel() {
        if (getItems().size() != 4) return -1;
        ItemStack core = getItems().get(CORE_SLOT);
        if (core.is(com.mohistmc.academy.world.AcademyItems.MAT_CORE_0.get())) return 0;
        if (core.is(com.mohistmc.academy.world.AcademyItems.MAT_CORE_1.get())) return 1;
        if (core.is(com.mohistmc.academy.world.AcademyItems.MAT_CORE_2.get())) return 2;
        return -1;
    }
    public boolean hasInitializationMaterials() {
        return initializationCoreLevel() >= 0
                && getItems().get(PLATE_SLOT_0).is(com.mohistmc.academy.world.AcademyItems.CONSTRAINT_PLATE.get())
                && getItems().get(PLATE_SLOT_1).is(com.mohistmc.academy.world.AcademyItems.CONSTRAINT_PLATE.get())
                && getItems().get(PLATE_SLOT_2).is(com.mohistmc.academy.world.AcademyItems.CONSTRAINT_PLATE.get());
    }

    // ==================== IWirelessMatrix ====================

    @Override
    public int getCapacity() {
        int level = legacyCoreLevel();
        return hasInitializationMaterials() ? 8 * level : 0;
    }

    @Override
    public double getBandwidth() {
        int level = legacyCoreLevel();
        return hasInitializationMaterials() ? 60.0 * level * level : 0;
    }

    @Override
    public double getRange() {
        int level = legacyCoreLevel();
        return hasInitializationMaterials() ? 24.0 * Math.sqrt(level) : 0;
    }

    /** Legacy core metadata 0..2 maps to operational levels 1..3. */
    private int legacyCoreLevel() {
        int core = initializationCoreLevel();
        return core < 0 ? 0 : core + 1;
    }

    // ==================== 自定义属性 ====================

    public String getSSID() { return ssid; }
    public void setSSID(String ssid) { if (ssid == null || ssid.length() > 64) return; this.ssid = ssid; setChanged(); }
    public String getPassword() { return password; }
    public void setPassword(String password) { if (password == null || password.length() > 64) return; this.password = password; this.syncedHasPassword = !password.isEmpty(); setChanged(); }
    public boolean hasPasswordConfigured() { return !password.isEmpty() || syncedHasPassword; }

    public void setCapacity(int capacity) { this.capacity = Math.clamp(capacity, 1, 1024); setChanged(); }
    public void setBandwidth(double bandwidth) { this.bandwidth = boundedFinite(bandwidth, DEFAULT_BANDWIDTH, 1_000_000); setChanged(); }
    public void setRange(double range) { this.range = boundedFinite(range, DEFAULT_RANGE, 256); setChanged(); }

    // ==================== Owner ====================

    public UUID getOwnerUUID() { return ownerUUID; }
    public void setOwnerUUID(UUID uuid) { this.ownerUUID = uuid; setChanged(); }

    /** Migrate a command-placed or pre-owner matrix only through an administrator interaction. */
    public boolean claimLegacyOwnerIfAbsent(net.minecraft.world.entity.player.Player player) {
        if (MachineOwnership.canClaimLegacy(ownerUUID, player)) {
            ownerUUID = player.getUUID();
            setChanged();
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
            return true;
        }
        return false;
    }

    public boolean isOwner(net.minecraft.world.entity.player.Player player) {
        return ownerUUID != null && player != null && player.getUUID().equals(ownerUUID);
    }

    public boolean canManage(net.minecraft.world.entity.player.Player player) {
        return MachineOwnership.canManage(ownerUUID, player);
    }

    // ==================== Initialization ====================

    public boolean isInitialized() { return initialized; }
    public void setInitialized(boolean init) { this.initialized = init; setChanged(); }
    public boolean isOperational() { return initialized && hasInitializationMaterials(); }

    /**
     * 根据矩阵核心等级调整性能参数
     */
    public void applyCoreLevel(int coreLevel) {
        int level = Math.clamp(coreLevel + 1, 1, 3);
        this.capacity = 8 * level;
        this.bandwidth = 60.0 * level * level;
        this.range = 24.0 * Math.sqrt(level);
        setChanged();
    }

    // ==================== NBT ====================

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        migrateRebuildSlotOrder();
        if (tag.contains("ssid")) ssid = bounded(tag.getString("ssid"), 64);
        if (tag.contains("password")) password = bounded(tag.getString("password"), 64);
        if (tag.contains("has_password")) syncedHasPassword = tag.getBoolean("has_password");
        if (tag.contains("capacity")) capacity = Math.clamp(tag.getInt("capacity"), 1, 1024);
        if (tag.contains("bandwidth")) bandwidth = boundedFinite(tag.getDouble("bandwidth"), DEFAULT_BANDWIDTH, 1_000_000);
        if (tag.contains("matrix_range")) range = boundedFinite(tag.getDouble("matrix_range"), DEFAULT_RANGE, 256);
        if (tag.contains("ownerUUID")) ownerUUID = parseUuid(tag.getString("ownerUUID"));
        if (tag.contains("initialized")) initialized = tag.getBoolean("initialized");
    }

    /**
     * Builds through 0.0.10 accidentally stored core,plate,plate,plate while
     * the official 1.12.2 machine stores plate,plate,plate,core.  Normalize
     * that exact old rebuild layout on load without consuming or copying any
     * stack, so existing test/player worlds remain usable after the parity fix.
     */
    private void migrateRebuildSlotOrder() {
        if (getItems().size() != 4 || !isMatrixCore(getItems().get(0))
                || isMatrixCore(getItems().get(CORE_SLOT))) return;
        ItemStack core = getItems().get(0);
        ItemStack plate0 = getItems().get(1);
        ItemStack plate1 = getItems().get(2);
        ItemStack plate2 = getItems().get(3);
        getItems().set(PLATE_SLOT_0, plate0);
        getItems().set(PLATE_SLOT_1, plate1);
        getItems().set(PLATE_SLOT_2, plate2);
        getItems().set(CORE_SLOT, core);
    }

    private static boolean isMatrixCore(ItemStack stack) {
        return stack.is(com.mohistmc.academy.world.AcademyItems.MAT_CORE_0.get())
                || stack.is(com.mohistmc.academy.world.AcademyItems.MAT_CORE_1.get())
                || stack.is(com.mohistmc.academy.world.AcademyItems.MAT_CORE_2.get());
    }

    private static String bounded(String value, int max) { return value == null ? "" : value.substring(0, Math.min(value.length(), max)); }
    private static double boundedFinite(double value, double fallback, double max) { return Double.isFinite(value) && value >= 0 ? Math.min(value, max) : fallback; }
    private static UUID parseUuid(String value) { try { return UUID.fromString(value); } catch (RuntimeException ignored) { return null; } }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putString("ssid", ssid);
        tag.putString("password", password);
        tag.putInt("capacity", capacity);
        tag.putDouble("bandwidth", bandwidth);
        tag.putDouble("matrix_range", range);
        if (ownerUUID != null) tag.putString("ownerUUID", ownerUUID.toString());
        tag.putBoolean("initialized", initialized);
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.putString("ssid", ssid);
        tag.putBoolean("has_password", !password.isEmpty());
        tag.putInt("capacity", capacity);
        tag.putDouble("bandwidth", bandwidth);
        tag.putDouble("matrix_range", range);
        if (ownerUUID != null) tag.putString("ownerUUID", ownerUUID.toString());
        tag.putBoolean("initialized", initialized);
        return tag;
    }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
