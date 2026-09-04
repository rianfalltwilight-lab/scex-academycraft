package com.mohistmc.academy.world.menu;

import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.world.AcademyMenus;
import com.mohistmc.academy.world.block.entity.WindGenBaseBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class WindGenBaseMenu extends AcademyMenu {
    private static final int DATA_COUNT = 6;
    private final ContainerData machineData;

    public WindGenBaseMenu(int windowId, Inventory inv, FriendlyByteBuf data) {
        super(AcademyMenus.WIND_BASE_MENU.get(), windowId, inv, data, true);

        if (!inv.player.level().isClientSide()
                && inv.player.level().getBlockEntity(pos) instanceof WindGenBaseBlockEntity be) {
            machineData = new ContainerData() {
                @Override public int get(int index) {
                    return switch (index) {
                        case 0 -> MenuDataWords.low(be.getEnergyStored());
                        case 1 -> MenuDataWords.high(be.getEnergyStored());
                        case 2 -> MenuDataWords.low(be.getMaxEnergyStored());
                        case 3 -> MenuDataWords.high(be.getMaxEnergyStored());
                        case 4 -> (be.isValidMain() ? 1 : 0)
                                | (be.isValidMiddle() ? 2 : 0)
                                | (be.isWorking() ? 4 : 0);
                        case 5 -> (int) Math.round(Math.clamp(be.getGenerationRate(), 0.0, 15.0) * 1000.0);
                        default -> 0;
                    };
                }
                @Override public void set(int index, int value) {}
                @Override public int getCount() { return DATA_COUNT; }
            };
        } else {
            machineData = new SimpleContainerData(DATA_COUNT);
        }
        addDataSlots(machineData);

        addAcademySlot(new Slot(container, 0, 42, 80) {
            @Override
            public boolean mayPlace(ItemStack item) {
                return EnergyItemHelper.isEnergyItem(item);
            }
        });
    }

    public int getEnergy() { return Math.max(0, MenuDataWords.join(machineData.get(0), machineData.get(1))); }
    public int getMaxEnergy() { return Math.max(1, MenuDataWords.join(machineData.get(2), machineData.get(3))); }
    public boolean isStructureComplete() { return (machineData.get(4) & 1) != 0; }
    public boolean isMiddleComplete() { return (machineData.get(4) & 2) != 0; }
    public boolean isWorking() { return (machineData.get(4) & 4) != 0; }
    public double getGenerationRate() { return Math.max(0, machineData.get(5)) / 1000.0; }
}
