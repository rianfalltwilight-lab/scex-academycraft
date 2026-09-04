package com.mohistmc.academy.capability;

import net.neoforged.neoforge.energy.IEnergyStorage;

/** NeoForge/Jade-facing view over the mod's authoritative IF storage. */
public final class ForgeEnergyView implements IEnergyStorage {
    private final IFEnergyStorage delegate;
    public ForgeEnergyView(IFEnergyStorage delegate) { this.delegate = delegate; }
    @Override public int receiveEnergy(int amount, boolean simulate) {
        int acceptedIf = delegate.receiveEnergy(ExternalEnergyConversion.wholeIfFromFe(amount), simulate);
        return ExternalEnergyConversion.ifToFe(acceptedIf);
    }
    @Override public int extractEnergy(int amount, boolean simulate) {
        int extractedIf = delegate.extractEnergy(ExternalEnergyConversion.wholeIfFromFe(amount), simulate);
        return ExternalEnergyConversion.ifToFe(extractedIf);
    }
    @Override public int getEnergyStored() {
        return ExternalEnergyConversion.ifToFe(delegate.getEnergyStored());
    }
    @Override public int getMaxEnergyStored() {
        return ExternalEnergyConversion.ifToFe(delegate.getMaxEnergyStored());
    }
    @Override public boolean canExtract() { return true; }
    @Override public boolean canReceive() { return true; }
}
