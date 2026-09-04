package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.capability.IFEnergyStorage;
import com.mohistmc.academy.energy.api.block.IWirelessGenerator;
import com.mohistmc.academy.world.AcademyBlockEntities;
import com.mohistmc.academy.world.block.WindGenBase;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class WindGenBaseBlockEntity extends AcademyContainerBlockEntity
        implements IFEnergyStorage, IWirelessGenerator {
    private boolean structureComplete;
    private boolean middleComplete;
    private boolean working;
    private double generationRate;
    /** Energy changes every working tick; coalesce client BE packets instead
     * of broadcasting the complete inventory/update tag at 20 Hz per turbine. */
    private long lastClientSyncTick = Long.MIN_VALUE;
    private boolean pendingEnergySync;

    private static final double MIN_GENERATION_RATE = 7.5;
    private static final double MAX_GENERATION_RATE = 15.0;
    private static final int MAX_STORAGE = 20000;
    private float storedEnergy = 0.0f;

    public WindGenBaseBlockEntity(BlockPos p_155229_, BlockState p_155230_) {
        super(AcademyBlockEntities.WINDGEN_BASE.get(), p_155229_, p_155230_);
        setItems(net.minecraft.core.NonNullList.withSize(getContainerSize(), ItemStack.EMPTY));
    }

    public void tick(boolean structureComplete, boolean middleComplete, boolean working, int mainY) {
        // The base ticker is registered on both logical sides.  Generation,
        // inventory charging and block-state mutation are server-authoritative;
        // letting the client run this method caused duplicate local energy,
        // desynchronised fan animation and (with some loaders) a client-side
        // block update loop.  Keep the cached structure flags for rendering,
        // but never mutate energy or send updates from the client.
        if (level == null || level.isClientSide()) {
            this.structureComplete = structureComplete;
            this.middleComplete = middleComplete;
            this.working = working;
            this.generationRate = working ? generationRate(mainY) : 0;
            return;
        }
        boolean stateChanged = this.structureComplete != structureComplete
                || this.middleComplete != middleComplete || this.working != working;
        this.structureComplete = structureComplete;
        this.middleComplete = middleComplete;
        this.working = working;
        this.generationRate = working ? generationRate(mainY) : 0;

        // Keep the authoritative runtime state in the block state as well as
        // the BE cache.  Without this, the base always rendered the disabled
        // model even while it was generating power.
        if (level != null && !level.isClientSide()
                && getBlockState().hasProperty(WindGenBase.ENABLE)
                && getBlockState().getValue(WindGenBase.ENABLE) != structureComplete) {
            level.setBlock(worldPosition, getBlockState().setValue(WindGenBase.ENABLE, structureComplete), Block.UPDATE_CLIENTS);
        }

        float oldEnergy = storedEnergy;

        if (working) {
            if (Double.isFinite(generationRate)) {
                storedEnergy = Math.min(MAX_STORAGE, storedEnergy + (float) generationRate);
            }
        }

        // 无论结构是否有效，都尝试用存储池中的整数能量给能源单元充能
        int chargeAmount = Math.min((int) storedEnergy, (int) getBandwidth());
        if (chargeAmount > 0) {
            int charged = chargeEnergyUnit(chargeAmount);
            storedEnergy -= charged;
        }

        boolean energyChanged = oldEnergy != storedEnergy;
        if (energyChanged || stateChanged) {
            setChanged();
        }
        if (energyChanged) pendingEnergySync = true;

        long now = level.getGameTime();
        boolean periodicEnergySync = pendingEnergySync
                && (lastClientSyncTick == Long.MIN_VALUE || now < lastClientSyncTick
                || now - lastClientSyncTick >= 10);
        if (stateChanged || periodicEnergySync) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            lastClientSyncTick = now;
            pendingEnergySync = false;
        }
    }

    private static double generationRate(int mainY) {
        double t = Math.clamp((mainY - 70.0) / 90.0, 0.0, 1.0);
        return MIN_GENERATION_RATE + (MAX_GENERATION_RATE - MIN_GENERATION_RATE) * t;
    }

    /**
     * 给槽位中的能源单元充能
     * @param amount 充能数量
     * @return 实际充能数量
     */
    private int chargeEnergyUnit(int amount) {
        ItemStack stack = getItems().getFirst();
        if (stack.isEmpty() || !EnergyItemHelper.isEnergyItem(stack)) return 0;
        return EnergyItemHelper.receiveEnergy(stack, amount, false);
    }

    @Override
    public int getContainerSize() {
        return 1;
    }

    public boolean isValidMiddle() {
        return middleComplete;
    }

    public boolean isValidMain() {
        return structureComplete;
    }

    public boolean isWorking() { return working; }
    public double getGenerationRate() { return generationRate; }

    // ==================== IWirelessGenerator ====================

    @Override
    public double getProvidedEnergy(double req) {
        if (!Double.isFinite(req) || req <= 0) return 0;
        double give = Math.min(Math.max(0, req), storedEnergy);
        storedEnergy -= (float) give;
        if (give > 0) setChanged();
        return give;
    }

    @Override
    public double getBandwidth() {
        return 300; // 1.0.7 IFConstants.LATENCY_MK3
    }

    // ==================== IFEnergyStorage ====================

    @Override
    public int getEnergyStored() {
        return (int) storedEnergy;
    }

    @Override
    public int getMaxEnergyStored() {
        return MAX_STORAGE;
    }

    @Override
    public void setEnergy(int energy) {
        this.storedEnergy = Math.min(MAX_STORAGE, Math.max(0, energy));
        setChanged();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.putFloat("storedEnergy", storedEnergy);
        tag.putBoolean("structureComplete", structureComplete);
        tag.putBoolean("middleComplete", middleComplete);
        tag.putBoolean("working", working);
        tag.putDouble("generationRate", generationRate);
        return tag;
    }

    @Override
    public void loadAdditional(CompoundTag p_331149_, HolderLookup.Provider p_333170_) {
        super.loadAdditional(p_331149_, p_333170_);
        if (p_331149_.contains("storedEnergy")) {
            this.storedEnergy = MachineStateSanitizer.clampFinite(p_331149_.getFloat("storedEnergy"), MAX_STORAGE);
        }
        structureComplete = p_331149_.getBoolean("structureComplete");
        middleComplete = p_331149_.getBoolean("middleComplete");
        working = p_331149_.getBoolean("working");
        double loadedRate = p_331149_.getDouble("generationRate");
        generationRate = Double.isFinite(loadedRate)
                ? Math.clamp(loadedRate, 0.0, MAX_GENERATION_RATE) : 0.0;
    }

    @Override
    public void saveAdditional(CompoundTag p_187471_, HolderLookup.Provider p_327783_) {
        super.saveAdditional(p_187471_, p_327783_);
        p_187471_.putFloat("storedEnergy", storedEnergy);
        p_187471_.putBoolean("structureComplete", structureComplete);
        p_187471_.putBoolean("middleComplete", middleComplete);
        p_187471_.putBoolean("working", working);
        p_187471_.putDouble("generationRate", generationRate);
    }
}
