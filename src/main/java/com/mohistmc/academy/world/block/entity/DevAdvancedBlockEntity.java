package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.capability.IFEnergyStorage;
import com.mohistmc.academy.energy.api.block.IWirelessReceiver;
import com.mohistmc.academy.world.AcademyBlockEntities;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.IDevStructure;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;

/**
 * 高级能力开发机方块实体 —— 支持物品槽(线圈+因子)和 IF 能量接收。
 * @author Mgazul
 */
public class DevAdvancedBlockEntity extends AcademyContainerBlockEntity
        implements IFEnergyStorage, IDevStructure, IWirelessReceiver {

    public static final int MAX_ENERGY = 200_000;
    private static final double MAX_BANDWIDTH = 300;

    private int energy = 0;
    private UUID structureId;

    public DevAdvancedBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyBlockEntities.DEV_ADVANCED.get(), pos, state);
        setItems(NonNullList.withSize(getContainerSize(), ItemStack.EMPTY));
    }

    @Override
    public int getContainerSize() {
        return 2; // 0=线圈, 1=因子
    }

    // ==================== 重置状态检查 ====================

    /** 是否有高压磁增幅线圈 */
    public boolean hasCoil() {
        ItemStack coil = getItems().get(0);
        return !coil.isEmpty() && coil.is(AcademyItems.MAGNETIC_COIL.get());
    }

    /** 是否有能力诱导因子 */
    public boolean hasFactor() {
        ItemStack factor = getItems().get(1);
        return !factor.isEmpty() && factor.getItem() instanceof com.mohistmc.academy.world.item.BaseFactor;
    }

    /** 是否满足重置条件 */
    public boolean isReadyForReset() {
        return hasCoil() && hasFactor();
    }

    /**
     * One-time, lossless migration for the experimental 0.0.10 machine slots.
     * AcademyCraft 1.0.7 reads reset materials from the player's hand and main
     * inventory, so new interactions never write these slots again.
     */
    public boolean returnLegacyStagingItems(ServerPlayer player) {
        boolean movedAny = false;
        for (int slot = 0; slot < getItems().size(); slot++) {
            ItemStack stored = getItems().get(slot);
            if (stored.isEmpty()) continue;
            ItemStack moving = stored.copy();
            getItems().set(slot, ItemStack.EMPTY);
            player.getInventory().add(moving);
            if (!moving.isEmpty() && level != null) {
                Block.popResource(level, worldPosition, moving);
            }
            movedAny = true;
        }
        if (movedAny) {
            setChanged();
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§e已将旧版开发机暂存槽中的材料退回背包（背包满时掉落在机器旁）"));
        }
        return movedAny;
    }

    // ==================== IDevStructure ====================

    @Override
    public UUID getStructureId() {
        return structureId;
    }

    @Override
    public void setStructureId(UUID structureId) {
        this.structureId = structureId;
        setChanged();
    }

    // ==================== IFEnergyStorage ====================

    @Override
    public int getEnergyStored() {
        return energy;
    }

    @Override
    public int getMaxEnergyStored() {
        return MAX_ENERGY;
    }

    @Override
    public void setEnergy(int energy) {
        this.energy = Math.clamp(energy, 0, MAX_ENERGY);
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // ==================== IWirelessReceiver ====================

    @Override
    public double getRequiredEnergy() {
        return energy < MAX_ENERGY ? Math.min(MAX_BANDWIDTH, MAX_ENERGY - energy) : 0;
    }

    @Override
    public double injectEnergy(double amt) {
        if (!Double.isFinite(amt) || amt <= 0) return amt;
        int accepted = (int) Math.floor(Math.min(amt, MAX_ENERGY - energy));
        if (accepted > 0) setEnergy(energy + accepted);
        return amt - accepted;
    }

    @Override
    public double pullEnergy(double amt) {
        return 0;
    }

    @Override
    public double getBandwidth() {
        return MAX_BANDWIDTH;
    }

    // ==================== NBT ====================

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("energy")) {
            this.energy = Math.clamp(tag.getInt("energy"), 0, MAX_ENERGY);
        }
        if (tag.contains("structureId")) {
            this.structureId = com.mohistmc.academy.world.block.IDevStructure
                    .parseStructureId(tag.getString("structureId"));
        }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider); tag.putInt("energy", energy); return tag;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("energy", energy);
        if (structureId != null) {
            tag.putString("structureId", structureId.toString());
        }
    }
}
