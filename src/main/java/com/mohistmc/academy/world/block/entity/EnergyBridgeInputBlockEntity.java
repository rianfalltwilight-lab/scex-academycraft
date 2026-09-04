package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.capability.ExternalEnergyConversion;
import com.mohistmc.academy.energy.api.block.IWirelessGenerator;
import com.mohistmc.academy.world.AcademyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** RF/FE input bridge: accepts wired FE and supplies IF to a wireless node. */
public final class EnergyBridgeInputBlockEntity extends EnergyBridgeBlockEntity
        implements IWirelessGenerator {
    private final IEnergyStorage external = new IEnergyStorage() {
        @Override public int receiveEnergy(int amount, boolean simulate) {
            return receiveExternalFe(amount, simulate);
        }
        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return getStoredFe(); }
        @Override public int getMaxEnergyStored() { return MAX_FE; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };

    public EnergyBridgeInputBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyBlockEntities.RF_INPUT.get(), pos, state);
    }

    public IEnergyStorage externalEnergy() { return external; }

    @Override
    public double getProvidedEnergy(double requestedIf) {
        if (!Double.isFinite(requestedIf) || requestedIf <= 0) return 0;
        int requestedFe = (int) Math.min(Integer.MAX_VALUE,
                Math.floor(requestedIf * ExternalEnergyConversion.FE_PER_IF + 1.0e-9));
        int suppliedFe = extractExternalFe(requestedFe, false);
        return suppliedFe / (double) ExternalEnergyConversion.FE_PER_IF;
    }

    @Override public double getBandwidth() { return BANDWIDTH_IF; }
}
