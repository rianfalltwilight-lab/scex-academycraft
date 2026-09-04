package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.capability.ExternalEnergyConversion;
import com.mohistmc.academy.energy.api.block.IWirelessReceiver;
import com.mohistmc.academy.world.AcademyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** IF output bridge: receives wireless IF and pushes/exposes wired FE. */
public final class EnergyBridgeOutputBlockEntity extends EnergyBridgeBlockEntity
        implements IWirelessReceiver {
    private final IEnergyStorage external = new IEnergyStorage() {
        @Override public int receiveEnergy(int amount, boolean simulate) { return 0; }
        @Override public int extractEnergy(int amount, boolean simulate) {
            return extractExternalFe(amount, simulate);
        }
        @Override public int getEnergyStored() { return getStoredFe(); }
        @Override public int getMaxEnergyStored() { return MAX_FE; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    };

    public EnergyBridgeOutputBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyBlockEntities.RF_OUTPUT.get(), pos, state);
    }

    public IEnergyStorage externalEnergy() { return external; }

    public void serverTick() {
        if (level == null || level.isClientSide() || getStoredFe() <= 0) return;
        for (Direction direction : Direction.values()) {
            if (getStoredFe() <= 0) break;
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                    worldPosition.relative(direction), direction.getOpposite());
            if (target == null || !target.canReceive()) continue;
            int offered = getStoredFe();
            int accepted = Math.clamp(target.receiveEnergy(offered, false), 0, offered);
            if (accepted > 0) extractExternalFe(accepted, false);
        }
    }

    @Override public double getRequiredEnergy() {
        return (MAX_FE - getStoredFe()) / (double) ExternalEnergyConversion.FE_PER_IF;
    }

    @Override
    public double injectEnergy(double amountIf) {
        if (!Double.isFinite(amountIf) || amountIf <= 0) return amountIf;
        int offeredFe = (int) Math.min(Integer.MAX_VALUE,
                Math.floor(amountIf * ExternalEnergyConversion.FE_PER_IF + 1.0e-9));
        int acceptedFe = receiveExternalFe(offeredFe, false);
        return Math.max(0, amountIf
                - acceptedFe / (double) ExternalEnergyConversion.FE_PER_IF);
    }

    @Override
    public double pullEnergy(double amountIf) {
        if (!Double.isFinite(amountIf) || amountIf <= 0) return 0;
        int requestedFe = (int) Math.min(Integer.MAX_VALUE,
                Math.floor(amountIf * ExternalEnergyConversion.FE_PER_IF + 1.0e-9));
        int extractedFe = extractExternalFe(requestedFe, false);
        return extractedFe / (double) ExternalEnergyConversion.FE_PER_IF;
    }

    @Override public double getBandwidth() { return BANDWIDTH_IF; }
}
