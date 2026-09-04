package com.mohistmc.academy.world.menu;

import com.mohistmc.academy.world.AcademyMenus;
import com.mohistmc.academy.world.block.entity.EnergyBridgeBlockEntity;
import com.mohistmc.academy.world.block.entity.EnergyBridgeInputBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;

/** Zero-slot menu carrying the authoritative bridge buffer into its wireless UI. */
public final class EnergyBridgeMenu extends AcademyMenu {
    private final ContainerData data;

    public EnergyBridgeMenu(int windowId, Inventory inventory, FriendlyByteBuf buffer) {
        super(AcademyMenus.ENERGY_BRIDGE_MENU.get(), windowId, inventory, buffer, false);
        if (!inventory.player.level().isClientSide()
                && inventory.player.level().getBlockEntity(pos) instanceof EnergyBridgeBlockEntity bridge) {
            data = new ContainerData() {
                @Override public int get(int index) {
                    return switch (index) {
                        case 0 -> bridge.getStoredFe();
                        case 1 -> EnergyBridgeBlockEntity.MAX_FE;
                        case 2 -> bridge instanceof EnergyBridgeInputBlockEntity ? 1 : 0;
                        default -> 0;
                    };
                }
                @Override public void set(int index, int value) {}
                @Override public int getCount() { return 3; }
            };
        } else {
            data = new SimpleContainerData(3);
        }
        addDataSlots(data);
    }

    public double getStoredIf() { return Math.max(0, data.get(0)) / 4.0; }
    public double getMaxIf() { return Math.max(1, data.get(1)) / 4.0; }
    public boolean isInput() { return data.get(2) != 0; }
}
