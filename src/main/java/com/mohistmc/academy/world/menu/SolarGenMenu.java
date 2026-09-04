package com.mohistmc.academy.world.menu;

import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.world.AcademyMenus;
import com.mohistmc.academy.world.block.entity.SolarGenBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

public class SolarGenMenu extends AcademyMenu {
    private final ContainerData machineData;

    public SolarGenMenu(int windowId, Inventory inv, FriendlyByteBuf data) {
        super(AcademyMenus.SOLAR_GEN_MENU.get(), windowId, inv, data, true);
        if (!inv.player.level().isClientSide()
                && inv.player.level().getBlockEntity(pos) instanceof SolarGenBlockEntity be) {
            machineData = new ContainerData() {
                @Override public int get(int index) {
                    return switch (index) {
                        // Both values are bounded to 1000 today and therefore
                        // fit the signed-short menu transport without word
                        // splitting. Keep the status in the same authoritative
                        // channel instead of reading weather from a client BE.
                        case 0 -> be.getEnergyStored();
                        case 1 -> be.getMaxEnergyStored();
                        case 2 -> be.getStatus().ordinal();
                        default -> 0;
                    };
                }
                @Override public void set(int index, int value) {}
                @Override public int getCount() { return 3; }
            };
        } else {
            machineData = new SimpleContainerData(3);
        }
        addDataSlots(machineData);
        addAcademySlot(new Slot(container, 0, 42, 81) {
            @Override
            public boolean mayPlace(ItemStack item) {
                return EnergyItemHelper.isEnergyItem(item);
            }
        });
    }

    public int getEnergy() { return Math.max(0, machineData.get(0)); }
    public int getMaxEnergy() { return Math.max(1, machineData.get(1)); }
    public SolarGenBlockEntity.SolarStatus getStatus() {
        var values = SolarGenBlockEntity.SolarStatus.values();
        return values[Math.clamp(machineData.get(2), 0, values.length - 1)];
    }
}
