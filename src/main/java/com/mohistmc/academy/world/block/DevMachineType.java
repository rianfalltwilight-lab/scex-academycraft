package com.mohistmc.academy.world.block;

public enum DevMachineType {
    PORTABLE(2, 30, "便携", 25, 750),
    NORMAL(3, 70, "基础", 20, 700),
    ADVANCED(Integer.MAX_VALUE, 100, "高级", 15, 600);

    public final int maxLevel;
    public final int syncRate;
    public final String displayName;
    public final int stimulationTicks;
    public final int energyPerStimulation;

    DevMachineType(int maxLevel, int syncRate, String displayName, int stimulationTicks, int energyPerStimulation) {
        this.maxLevel = maxLevel;
        this.syncRate = syncRate;
        this.displayName = displayName;
        this.stimulationTicks = stimulationTicks;
        this.energyPerStimulation = energyPerStimulation;
    }

    public int energyPerTick() { return energyPerStimulation / stimulationTicks; }

    /**
     * AcademyCraft 1.0.7 advanced a stimulation only after {@code tick > tps}.
     * That means the real debit window is TPS + 1 ticks, despite the old UI
     * estimating CPS from TPS. Keep the runtime quirk for behavioural parity.
     */
    public int developmentTicksPerStimulation() { return stimulationTicks + 1; }

    public int actualEnergyPerStimulation() {
        return energyPerTick() * developmentTicksPerStimulation();
    }

    public int applySyncRate(int baseCost) {
        return baseCost * 100 / syncRate;
    }

    public static DevMachineType fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            return NORMAL;
        }
        return values()[ordinal];
    }
}
