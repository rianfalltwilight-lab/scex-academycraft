package com.mohistmc.academy.world.menu;

import com.mohistmc.academy.world.AcademyMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.mohistmc.academy.world.block.entity.DevNormalBlockEntity;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;

public class DevNormalMenu extends AcademyMenu {
    private final DevNormalBlockEntity machine;
    private final ContainerData machineData;

    public DevNormalMenu(int windowId, Inventory inv, FriendlyByteBuf data) {
        // The normal developer has no item inventory.  Keeping a fake player
        // inventory here made this screen look like a second, broken machine
        // GUI even though legacy 1.0.7 used one developer workflow for both
        // tiers.
        super(AcademyMenus.DEV_NORMAL_MENU.get(), windowId, inv, data, false);
        machine = pos != null && inv.player.level().getBlockEntity(pos) instanceof DevNormalBlockEntity be
                ? be : null;
        if (!inv.player.level().isClientSide && machine != null) {
            machineData = new ContainerData() {
                @Override public int get(int index) {
                    return switch (index) {
                        case 0 -> MenuDataWords.low(machine.getEnergyStored());
                        case 1 -> MenuDataWords.high(machine.getEnergyStored());
                        case 2 -> MenuDataWords.low(machine.getMaxEnergyStored());
                        case 3 -> MenuDataWords.high(machine.getMaxEnergyStored());
                        default -> 0;
                    };
                }
                @Override public void set(int index, int value) {}
                @Override public int getCount() { return 4; }
            };
        } else {
            machineData = new SimpleContainerData(4);
        }
        addDataSlots(machineData);
    }

    @Override
    public boolean stillValid(Player player) {
        return pos != null && machine != null && !machine.isRemoved() && player.level().isLoaded(pos)
                && player.level().getBlockEntity(pos) == machine
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64
                && player.level().mayInteract(player, pos);
    }

    public int getEnergy() { return Math.max(0, MenuDataWords.join(machineData.get(0), machineData.get(1))); }
    public int getMaxEnergy() { return Math.max(1, MenuDataWords.join(machineData.get(2), machineData.get(3))); }
    public boolean isBoundTo(BlockEntity entity) { return machine != null && entity == machine && !machine.isRemoved(); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
