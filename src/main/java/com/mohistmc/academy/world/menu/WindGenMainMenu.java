package com.mohistmc.academy.world.menu;

import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.AcademyMenus;
import com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class WindGenMainMenu extends AcademyMenu {
    private final ContainerData machineData;

    public WindGenMainMenu(int windowId, Inventory inv, FriendlyByteBuf data) {
        super(AcademyMenus.WIND_MAIN_MENU.get(), windowId, inv, data, true);

        if (!inv.player.level().isClientSide()
                && inv.player.level().getBlockEntity(pos) instanceof WindGenMainBlockEntity be) {
            machineData = new ContainerData() {
                @Override public int get(int index) {
                    return (be.isStructureComplete() ? 1 : 0)
                            | (be.isFanInstalled() ? 2 : 0)
                            | (be.isWorking() ? 4 : 0)
                            | (be.isVisualFanVisible() ? 8 : 0);
                }
                @Override public void set(int index, int value) {}
                @Override public int getCount() { return 1; }
            };
        } else {
            machineData = new SimpleContainerData(1);
        }
        addDataSlots(machineData);

        addAcademySlot(new Slot(container, 0, 78, 9) {
            @Override
            public boolean mayPlace(ItemStack item) {
                return item.is(AcademyItems.WINDGEN_FAN.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
    }

    public boolean isStructureComplete() { return (machineData.get(0) & 1) != 0; }
    public boolean isFanInstalled() { return (machineData.get(0) & 2) != 0; }
    public boolean isWorking() { return (machineData.get(0) & 4) != 0; }
    public boolean isVisualFanVisible() { return (machineData.get(0) & 8) != 0; }
}
