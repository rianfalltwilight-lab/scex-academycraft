package com.mohistmc.academy.world.item;

/** Five-cell legacy battery: 50,000 IF capacity and 100 IF/t bandwidth. */
public final class EnergyUnitGroup extends ExtraEnergyItem {
    public static final int MAX_ENERGY = 50_000;
    public EnergyUnitGroup() { super(MAX_ENERGY, 100); }
}
