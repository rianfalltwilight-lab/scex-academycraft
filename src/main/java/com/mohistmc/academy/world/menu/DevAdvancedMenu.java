package com.mohistmc.academy.world.menu;

import com.mohistmc.academy.world.AcademyMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import com.mohistmc.academy.world.block.entity.DevAdvancedBlockEntity;

/**
 * Slotless authenticated bridge used only by the developer wireless page.
 * The 1.0.7 advanced developer has no machine/player inventory page.
 */
public class DevAdvancedMenu extends AcademyMenu {
    private final ContainerData machineData;

    public DevAdvancedMenu(int windowId, Inventory inv, FriendlyByteBuf data) {
        super(AcademyMenus.DEV_ADVANCED_MENU.get(), windowId, inv, data, false);
        if (!inv.player.level().isClientSide && inv.player.level().getBlockEntity(pos) instanceof DevAdvancedBlockEntity be) {
            machineData = new ContainerData() {
                public int get(int i) {
                    return switch (i) {
                        case 0 -> MenuDataWords.low(be.getEnergyStored());
                        case 1 -> MenuDataWords.high(be.getEnergyStored());
                        case 2 -> MenuDataWords.low(be.getMaxEnergyStored());
                        case 3 -> MenuDataWords.high(be.getMaxEnergyStored());
                        default -> 0;
                    };
                }
                public void set(int i, int v) {}
                public int getCount() { return 4; }
            };
        } else machineData = new SimpleContainerData(4);
        addDataSlots(machineData);
    }
    public int getEnergy() { return Math.max(0, MenuDataWords.join(machineData.get(0), machineData.get(1))); }
    public int getMaxEnergy() { return Math.max(1, MenuDataWords.join(machineData.get(2), machineData.get(3))); }
}
