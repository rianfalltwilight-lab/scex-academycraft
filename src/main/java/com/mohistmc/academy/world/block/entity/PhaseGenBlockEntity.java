package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.energy.api.block.IWirelessGenerator;
import com.mohistmc.academy.world.AcademyBlockEntities;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.AcademyFluids;
import com.mohistmc.academy.capability.EnergyItemHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 虚相位能量发生机 —— 消耗 PhaseLiquid 单元产生 IF 能量,实现 IWirelessGenerator 向无线能源网络供电。
 * @author Mgazul
 */
public class PhaseGenBlockEntity extends AcademyContainerBlockEntity implements IWirelessGenerator {

    public static final int TANK_SIZE = 8000;
    public static final int PER_UNIT = 1000;
    public static final int CONSUME_PER_TICK = 100;
    public static final double GEN_PER_MB = 0.5;
    private static final double MAX_BANDWIDTH = 50; // 100 mB * .5 IF/mB
    private static final int MAX_STORAGE = 6000;

    private float storedEnergy = 0;
    private final FluidTank tank = new FluidTank(TANK_SIZE,
            fs -> fs.getFluid() == AcademyFluids.PHASE_LIQUID.get());

    public PhaseGenBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyBlockEntities.PHASE_GEN.get(), pos, state);
        setItems(NonNullList.withSize(getContainerSize(), ItemStack.EMPTY));
    }

    @Override
    public int getContainerSize() {
        return 3; // liquid input, empty container output, energy item
    }

    // ==================== Tick Logic ====================

    public void tick() {
        if (level == null || level.isClientSide) return;

        boolean changed = false;

        // TileGeneratorBase in 1.0.7 fills its 6000 IF buffer every server
        // tick, before the machine handles containers and charges its output
        // item.  Draining the tank only when NodeConn asked for energy left an
        // unbound generator permanently at 0 IF and made its battery slot a
        // no-op.
        double required = Math.max(0.0, MAX_STORAGE - storedEnergy);
        int maxDrain = Math.min(CONSUME_PER_TICK, (int) (required / GEN_PER_MB));
        if (maxDrain > 0) {
            FluidStack drained = tank.drain(maxDrain,
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            if (!drained.isEmpty()) {
                storedEnergy = Math.min(MAX_STORAGE,
                        storedEnergy + (float) (drained.getAmount() * GEN_PER_MB));
                changed = true;
            }
        }

        ItemStack input = getItems().get(0);
        ItemStack output = getItems().get(1);

        if (!input.isEmpty() && input.is(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get())) {
            boolean canOutput = output.isEmpty()
                    || (output.is(AcademyItems.MATTER_UNIT_NONE.get())
                    && output.getCount() < output.getMaxStackSize());

            if (canOutput && tank.getFluidAmount() + PER_UNIT <= TANK_SIZE) {
                tank.fill(new FluidStack(AcademyFluids.PHASE_LIQUID.get(), PER_UNIT),
                        net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                input.shrink(1);
                if (output.isEmpty()) getItems().set(1, new ItemStack(AcademyItems.MATTER_UNIT_NONE.get()));
                else output.grow(1);
                changed = true;
            }
        }
        ItemStack energyItem = getItems().get(2);
        if (EnergyItemHelper.isEnergyItem(energyItem) && storedEnergy >= 1) {
            int charged = EnergyItemHelper.receiveEnergy(energyItem, Math.min((int) storedEnergy, (int) MAX_BANDWIDTH), false);
            storedEnergy -= charged;
            if (charged > 0) changed = true;
        }
        if (changed) setChanged();
    }

    // ==================== IWirelessGenerator ====================

    @Override
    public double getProvidedEnergy(double req) {
        if (!Double.isFinite(req) || req <= 0) return 0;
        // Wireless transport pulls from the already generated buffer.  The
        // NodeConn also applies this generator's bandwidth, but keep the bound
        // here for safe direct callers.
        double give = Math.min(Math.min(req, MAX_BANDWIDTH), storedEnergy);
        storedEnergy -= (float) give;
        if (give > 0) setChanged();
        return give;
    }

    @Override
    public double getBandwidth() {
        return MAX_BANDWIDTH;
    }

    // ==================== Accessors ====================

    public float getStoredEnergy() { return storedEnergy; }
    public int getMaxEnergy() { return MAX_STORAGE; }
    public int getFluidAmount() { return tank.getFluidAmount(); }
    public int getTankSize() { return TANK_SIZE; }
    /** Compatibility for older menu/tests; the bar now represents tank fill. */
    public int getProgress() { return tank.getFluidAmount(); }
    public int getProcessTicks() { return TANK_SIZE; }
    public FluidTank getFluidTank() { return tank; }

    /** 是否正在工作中 */
    public boolean isWorking() {
        return tank.getFluidAmount() > 0 && storedEnergy < MAX_STORAGE;
    }

    // ==================== NBT ====================

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        storedEnergy = MachineStateSanitizer.clampFinite(tag.getFloat("storedEnergy"), MAX_STORAGE);
        if (tag.contains("tank")) tank.readFromNBT(provider, tag.getCompound("tank"));
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putFloat("storedEnergy", storedEnergy);
        tag.put("tank", tank.writeToNBT(provider, new CompoundTag()));
    }
}
