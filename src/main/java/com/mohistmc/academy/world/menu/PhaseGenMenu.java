package com.mohistmc.academy.world.menu;

import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.AcademyMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import com.mohistmc.academy.world.block.entity.PhaseGenBlockEntity;
import net.minecraft.world.item.ItemStack;

public class PhaseGenMenu extends AcademyMenu {
    private final ContainerData machineData;
    public PhaseGenMenu(int windowId, Inventory inv, FriendlyByteBuf data) {
        super(AcademyMenus.PHASE_GEN_MENU.get(), windowId, inv, data, true);
        if (!inv.player.level().isClientSide && inv.player.level().getBlockEntity(pos) instanceof PhaseGenBlockEntity be) {
            machineData = new ContainerData() {
                public int get(int i) { return switch(i) {
                    case 0 -> be.getFluidAmount(); case 1 -> be.getTankSize();
                    case 2 -> (int) be.getStoredEnergy(); case 3 -> be.getMaxEnergy(); default -> 0; }; }
                public void set(int i, int v) {}
                public int getCount() { return 4; }
            };
        } else machineData = new SimpleContainerData(4);
        addDataSlots(machineData);
        addAcademySlot(new Slot(container, 0, 45, 12) {
            @Override
            public boolean mayPlace(ItemStack item) {
                return item.is(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get());
            }
        });
        addAcademySlot(new Slot(container, 1, 112, 51) {
            @Override
            public boolean mayPlace(ItemStack item) {
                return false;
            }
        });
        addAcademySlot(new Slot(container, 2, 42, 80) {
            @Override public boolean mayPlace(ItemStack item) {
                return com.mohistmc.academy.capability.EnergyItemHelper.isEnergyItem(item);
            }
        });
    }
    public int getFluidAmount() { return Math.max(0, machineData.get(0)); }
    public int getTankSize() { return Math.max(1, machineData.get(1)); }
    /** Compatibility aliases for the original temporary GUI implementation. */
    public int getProgress() { return getFluidAmount(); }
    public int getProcessTicks() { return getTankSize(); }
    public int getEnergy() { return Math.max(0, machineData.get(2)); }
    public int getMaxEnergy() { return Math.max(1, machineData.get(3)); }
}
