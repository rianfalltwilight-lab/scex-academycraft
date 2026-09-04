package com.mohistmc.academy.capability;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** NeoForge energy bridge for legacy IF items ({@code 4 FE = 1 IF}). */
public final class AcademyEnergyItemStorage implements IEnergyStorage {
    private final ItemStack stack;
    public AcademyEnergyItemStorage(ItemStack stack){this.stack=stack;}
    public int receiveEnergy(int maxReceive,boolean simulate){
        return ExternalEnergyConversion.ifToFe(EnergyItemHelper.receiveEnergy(stack,
                ExternalEnergyConversion.wholeIfFromFe(maxReceive),simulate));
    }
    public int extractEnergy(int maxExtract,boolean simulate){
        return ExternalEnergyConversion.ifToFe(EnergyItemHelper.extractEnergy(stack,
                ExternalEnergyConversion.wholeIfFromFe(maxExtract),simulate));
    }
    public int getEnergyStored(){return ExternalEnergyConversion.ifToFe(EnergyItemHelper.getEnergy(stack));}
    public int getMaxEnergyStored(){return stack.getItem() instanceof IEnergyItem e
            ? ExternalEnergyConversion.ifToFe(e.getMaxEnergyStored(stack)):0;}
    public boolean canExtract(){return true;}
    public boolean canReceive(){return true;}
}
