package com.mohistmc.academy.world.menu;

import com.mohistmc.academy.crafting.AcademyRecipeTypes;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.AcademyMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import com.mohistmc.academy.world.block.entity.ImagFusorBlockEntity;
import net.minecraft.world.item.ItemStack;

public class ImagFusorMenu extends AcademyMenu {
    private final ContainerData machineData;

    public ImagFusorMenu(int windowId, Inventory inv, FriendlyByteBuf data) {
        super(AcademyMenus.IMAG_FUSOR_MENU.get(), windowId, inv, data, true);
        if (!inv.player.level().isClientSide && inv.player.level().getBlockEntity(pos) instanceof ImagFusorBlockEntity be) {
            machineData = new ContainerData() {
                public int get(int i) { return switch(i) {
                    case 0 -> be.getFluidAmount(); case 1 -> be.getMaxFluid();
                    case 2 -> be.getProcessingTime(); case 3 -> be.getProcessingDuration();
                    case 4 -> (int) be.getEnergy(); case 5 -> (int) be.getMaxEnergy();
                    case 6 -> be.getCurrentRecipePhaseLiquid(); default -> 0; }; }
                public void set(int i, int v) {}
                public int getCount() { return 7; }
            };
        } else machineData = new SimpleContainerData(7);
        addDataSlots(machineData);

        addAcademySlot(new Slot(container, ImagFusorBlockEntity.FLUID_INPUT_SLOT, 13, 10) {
            @Override
            public boolean mayPlace(ItemStack item) {
                return item.is(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get());
            }
        });

        addAcademySlot(new Slot(container, ImagFusorBlockEntity.EMPTY_UNIT_SLOT, 143, 10) {
            @Override
            public boolean mayPlace(ItemStack item) {
                return false;
            }
        });

        addAcademySlot(new Slot(container, ImagFusorBlockEntity.INPUT_SLOT, 13, 49) {
            @Override
            public boolean mayPlace(ItemStack item) {
                // Accept ingredient kinds before the player has assembled the full recipe count.
                // The machine's processing recipe remains responsible for quantity checks.
                return inv.player.level().getRecipeManager()
                        .getAllRecipesFor(AcademyRecipeTypes.IMAG_FUSING.get()).stream()
                        .anyMatch(holder -> holder.value().input().test(item));
            }
        });

        addAcademySlot(new Slot(container, ImagFusorBlockEntity.OUTPUT_SLOT, 143, 49) {
            @Override
            public boolean mayPlace(ItemStack item) {
                return false;
            }
        });
        addAcademySlot(new Slot(container, ImagFusorBlockEntity.ENERGY_INPUT_SLOT, 42, 80) {
            @Override public boolean mayPlace(ItemStack item) {
                return com.mohistmc.academy.capability.EnergyItemHelper.isEnergyItem(item);
            }
        });
    }
    public int getFluidAmount() { return Math.max(0, machineData.get(0)); }
    public int getMaxFluid() { return Math.max(1, machineData.get(1)); }
    public int getProcessingTime() { return Math.max(0, machineData.get(2)); }
    public int getProcessingDuration() { return Math.max(1, machineData.get(3)); }
    public int getEnergy() { return Math.max(0, machineData.get(4)); }
    public int getMaxEnergy() { return Math.max(1, machineData.get(5)); }
    public int getCurrentRecipePhaseLiquid() { return Math.max(0, machineData.get(6)); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            int machineStart = getMachineSlotStart();
            int machineEnd = getMachineSlotEnd();
            if (index >= machineStart && index < machineEnd) {
                if (!this.moveItemStackTo(itemstack1, getPlayerSlotStart(), getPlayerSlotEnd(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.moveItemStackTo(itemstack1, machineStart, machineEnd, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }
}
