package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.crafting.AcademyRecipeTypes;
import com.mohistmc.academy.crafting.ImagFusorRecipe;
import com.mohistmc.academy.crafting.ImagFusorRecipeInput;
import com.mohistmc.academy.energy.api.block.IWirelessReceiver;
import com.mohistmc.academy.world.AcademyBlockEntities;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.AcademyFluids;
import com.mohistmc.academy.capability.EnergyItemHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 想象熔炉方块实体 —— 可实现 IWirelessReceiver 从 IF 能源网络获取能量。
 * @author Mgazul
 */
public class ImagFusorBlockEntity extends AcademyContainerBlockEntity implements IWirelessReceiver {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int FLUID_INPUT_SLOT = 2;
    public static final int ENERGY_INPUT_SLOT = 3;
    public static final int EMPTY_UNIT_SLOT = 4;

    private static final int MAX_FLUID = 8000;
    private static final int PHASE_LIQUID_PER_UNIT = 1000;
    private static final int PROCESSING_DURATION = 120;
    private static final double CONSUME_PER_TICK = 12;
    private static final double MAX_ENERGY = 2000;

    private int processingTime = 0;
    private double energy = 0;
    private final FluidTank tank = new FluidTank(MAX_FLUID,
            fs -> fs.getFluid() == AcademyFluids.PHASE_LIQUID.get());

    public ImagFusorBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyBlockEntities.IMAG_FUSOR.get(), pos, state);
        setItems(NonNullList.withSize(getContainerSize(), ItemStack.EMPTY));
    }

    @Override
    public int getContainerSize() {
        return 5;
    }

    public int getFluidAmount() { return tank.getFluidAmount(); }
    public int getProcessingTime() { return processingTime; }
    public boolean isWorking() { return processingTime > 0; }
    public int getMaxFluid() { return MAX_FLUID; }
    public int getProcessingDuration() { return PROCESSING_DURATION; }

    /** Phase-liquid requirement displayed by the original top-centre label. */
    public int getCurrentRecipePhaseLiquid() {
        if (level == null) return 0;
        ItemStack input = getItems().get(INPUT_SLOT);
        if (input.isEmpty()) return 0;
        return level.getRecipeManager()
                .getRecipeFor(AcademyRecipeTypes.IMAG_FUSING.get(),
                        new ImagFusorRecipeInput(input), level)
                .map(net.minecraft.world.item.crafting.RecipeHolder::value)
                .map(ImagFusorRecipe::phaseLiquid)
                .orElse(0);
    }

    // ==================== IWirelessReceiver ====================

    @Override
    public double getRequiredEnergy() {
        return Math.max(0, MAX_ENERGY - energy);
    }

    @Override
    public double injectEnergy(double amt) {
        if (!Double.isFinite(amt) || amt <= 0) return amt;
        double accepted = Math.min(amt, MAX_ENERGY - energy);
        energy += accepted;
        if (accepted > 0) setChanged();
        return amt - accepted;
    }

    @Override
    public double pullEnergy(double amt) {
        if (!Double.isFinite(amt) || amt <= 0) return 0;
        double pulled = Math.min(amt, energy);
        energy -= pulled;
        if (pulled > 0) setChanged();
        return pulled;
    }

    @Override
    public double getBandwidth() {
        return 50;
    }

    // ==================== Tick ====================

    public void tick() {
        if (level == null || level.isClientSide) return;

        ItemStack fluidInput = getItems().get(FLUID_INPUT_SLOT);
        if (!fluidInput.isEmpty() && fluidInput.is(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get())) {
            ItemStack emptySlot = getItems().get(EMPTY_UNIT_SLOT);
            boolean canReturnContainer = emptySlot.isEmpty()
                    || (emptySlot.is(AcademyItems.MATTER_UNIT_NONE.get())
                    && emptySlot.getCount() < emptySlot.getMaxStackSize());
            if (canReturnContainer && tank.getFluidAmount() + PHASE_LIQUID_PER_UNIT <= MAX_FLUID) {
                tank.fill(new FluidStack(AcademyFluids.PHASE_LIQUID.get(), PHASE_LIQUID_PER_UNIT),
                        net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                fluidInput.shrink(1);
                if (emptySlot.isEmpty()) {
                    getItems().set(EMPTY_UNIT_SLOT, new ItemStack(AcademyItems.MATTER_UNIT_NONE.get()));
                } else {
                    emptySlot.grow(1);
                }
                setChanged();
            }
        }

        ItemStack battery = getItems().get(ENERGY_INPUT_SLOT);
        if (EnergyItemHelper.isEnergyItem(battery) && energy < MAX_ENERGY) {
            int gained = EnergyItemHelper.extractEnergy(battery,
                    Math.min((int) Math.ceil(MAX_ENERGY - energy), (int) getBandwidth()), false);
            if (gained > 0) injectEnergy(gained);
        }

        ItemStack input = getItems().get(INPUT_SLOT);
        ItemStack output = getItems().get(OUTPUT_SLOT);

        if (input.isEmpty()) {
            processingTime = 0;
            return;
        }

        ImagFusorRecipe recipe = level.getRecipeManager()
                .getRecipeFor(AcademyRecipeTypes.IMAG_FUSING.get(), new ImagFusorRecipeInput(input), level)
                .map(net.minecraft.world.item.crafting.RecipeHolder::value).orElse(null);
        if (recipe == null) {
            processingTime = 0;
            return;
        }

        if (tank.getFluidAmount() < recipe.phaseLiquid()) {
            processingTime = 0;
            return;
        }

        ItemStack result = recipe.output().copy();
        if (!canAcceptOutput(output, result)) {
            processingTime = 0;
            return;
        }

        if (pullEnergy(CONSUME_PER_TICK) != CONSUME_PER_TICK) {
            processingTime = 0;
            return;
        }
        processingTime++;
        if (processingTime >= PROCESSING_DURATION) {
            processingTime = 0;
            tank.drain(recipe.phaseLiquid(), net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            input.shrink(1);
            if (output.isEmpty()) {
                getItems().set(OUTPUT_SLOT, result.copy());
            } else {
                output.grow(result.getCount());
            }
            setChanged();
        }
    }

    // ==================== NBT ====================

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        if (tag.contains("tank")) tank.readFromNBT(provider, tag.getCompound("tank"));
        else tank.setFluid(new FluidStack(AcademyFluids.PHASE_LIQUID.get(),
                MachineStateSanitizer.clampAmount(tag.getInt("fluidAmount"), MAX_FLUID)));
        processingTime = MachineStateSanitizer.clampCounter(tag.getInt("processingTime"), PROCESSING_DURATION);
        energy = MachineStateSanitizer.clampFinite((float) tag.getDouble("energy"), (float) MAX_ENERGY);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putInt("fluidAmount", tank.getFluidAmount());
        tag.put("tank", tank.writeToNBT(provider, new CompoundTag()));
        tag.putInt("processingTime", processingTime);
        tag.putDouble("energy", energy);
    }

    public FluidTank getFluidTank() { return tank; }
    public double getEnergy() { return energy; }
    public double getMaxEnergy() { return MAX_ENERGY; }

    // ==================== Legacy sided automation ====================

    private final IItemHandler handlerTop = new SidedItems(
            new int[]{INPUT_SLOT, FLUID_INPUT_SLOT}, false);
    private final IItemHandler handlerBottom = new SidedItems(
            new int[]{OUTPUT_SLOT, EMPTY_UNIT_SLOT, ENERGY_INPUT_SLOT}, true);
    private final IItemHandler handlerSide = new SidedItems(
            new int[]{ENERGY_INPUT_SLOT}, false);
    private final IItemHandler handlerInternal = new SidedItems(
            new int[]{INPUT_SLOT, OUTPUT_SLOT, FLUID_INPUT_SLOT, ENERGY_INPUT_SLOT, EMPTY_UNIT_SLOT}, true);

    public IItemHandler getHandlerForSide(Direction side) {
        if (side == null) return handlerInternal;
        return switch (side) {
            case UP -> handlerTop;
            case DOWN -> handlerBottom;
            default -> handlerSide;
        };
    }

    private boolean isValidAutomationInput(int machineSlot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return switch (machineSlot) {
            case INPUT_SLOT -> level != null && level.getRecipeManager()
                    .getAllRecipesFor(AcademyRecipeTypes.IMAG_FUSING.get()).stream()
                    .anyMatch(holder -> holder.value().input().test(stack));
            case FLUID_INPUT_SLOT -> stack.is(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get());
            case ENERGY_INPUT_SLOT -> EnergyItemHelper.isEnergyItem(stack);
            default -> false;
        };
    }

    private final class SidedItems implements IItemHandler {
        private final int[] slots;
        private final boolean canExtract;

        private SidedItems(int[] slots, boolean canExtract) {
            this.slots = slots;
            this.canExtract = canExtract;
        }

        @Override public int getSlots() { return slots.length; }

        @Override @NotNull public ItemStack getStackInSlot(int slot) {
            return validIndex(slot) ? getItems().get(slots[slot]) : ItemStack.EMPTY;
        }

        @Override @NotNull public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (!validIndex(slot) || stack.isEmpty() || !isItemValid(slot, stack)) return stack;
            int machineSlot = slots[slot];
            ItemStack existing = getItems().get(machineSlot);
            int limit = Math.min(stack.getMaxStackSize(), getSlotLimit(slot));
            if (existing.isEmpty()) {
                int moved = Math.min(limit, stack.getCount());
                if (!simulate) {
                    ItemStack inserted = stack.copy();
                    inserted.setCount(moved);
                    getItems().set(machineSlot, inserted);
                    setChanged();
                }
                ItemStack remainder = stack.copy();
                remainder.shrink(moved);
                return remainder;
            }
            if (!ItemStack.isSameItemSameComponents(existing, stack)) return stack;
            int moved = Math.min(stack.getCount(), Math.max(0, limit - existing.getCount()));
            if (moved <= 0) return stack;
            if (!simulate) { existing.grow(moved); setChanged(); }
            ItemStack remainder = stack.copy();
            remainder.shrink(moved);
            return remainder;
        }

        @Override @NotNull public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!validIndex(slot) || !canExtract || amount <= 0) return ItemStack.EMPTY;
            ItemStack existing = getItems().get(slots[slot]);
            if (existing.isEmpty()) return ItemStack.EMPTY;
            int moved = Math.min(amount, existing.getCount());
            ItemStack extracted = existing.copy();
            extracted.setCount(moved);
            if (!simulate) {
                existing.shrink(moved);
                if (existing.isEmpty()) getItems().set(slots[slot], ItemStack.EMPTY);
                setChanged();
            }
            return extracted;
        }

        @Override public int getSlotLimit(int slot) { return 64; }

        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return validIndex(slot) && isValidAutomationInput(slots[slot], stack);
        }

        private boolean validIndex(int slot) { return slot >= 0 && slot < slots.length; }
    }

    public static boolean canAcceptOutput(ItemStack existing, ItemStack result) {
        if (result == null || result.isEmpty() || result.getCount() <= 0
                || result.getCount() > result.getMaxStackSize()) return false;
        if (existing == null || existing.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(existing, result)
                && existing.getCount() <= existing.getMaxStackSize() - result.getCount();
    }
}
