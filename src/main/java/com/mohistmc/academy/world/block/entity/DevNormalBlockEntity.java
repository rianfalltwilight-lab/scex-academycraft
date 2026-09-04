package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.capability.IFEnergyStorage;
import com.mohistmc.academy.energy.api.block.IWirelessReceiver;
import com.mohistmc.academy.world.AcademyBlockEntities;
import com.mohistmc.academy.world.block.IDevStructure;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;

public class DevNormalBlockEntity extends BlockEntity implements IFEnergyStorage, IDevStructure, IWirelessReceiver {
    public static final int MAX_ENERGY = 50_000;
    private static final int MAX_BANDWIDTH = 100;
    private int energy = 0;
    private UUID structureId;

    public DevNormalBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyBlockEntities.DEV_NORMAL.get(), pos, state);
    }

    @Override
    public UUID getStructureId() {
        return structureId;
    }

    @Override
    public void setStructureId(UUID structureId) {
        this.structureId = structureId;
        setChanged();
    }

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

    @Override public double getRequiredEnergy() {
        return energy < MAX_ENERGY ? Math.min(MAX_BANDWIDTH, MAX_ENERGY - energy) : 0;
    }

    @Override public double injectEnergy(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return amount;
        int accepted = (int) Math.floor(Math.min(amount, MAX_ENERGY - energy));
        if (accepted > 0) setEnergy(energy + accepted);
        return amount - accepted;
    }

    @Override public double pullEnergy(double amount) { return 0; }

    @Override public double getBandwidth() { return MAX_BANDWIDTH; }

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
