package com.mohistmc.academy.world.item;

import com.mohistmc.academy.capability.IEnergyItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Shared bounded IF storage used by the ExtraAcC-compatible tools. */
public class ExtraEnergyItem extends AcademyItem implements IEnergyItem {
    private final int capacity;
    private final int transferLimit;

    protected ExtraEnergyItem(int capacity, int transferLimit) {
        this(new Item.Properties(), capacity, transferLimit);
    }

    protected ExtraEnergyItem(Item.Properties properties, int capacity, int transferLimit) {
        // 1.12.2 IFItemManager read a missing energy tag as zero.  The 1.21
        // durability bridge therefore has to default to maximum damage;
        // otherwise every crafted device would materialise fully charged.
        super(properties.durability(capacity).component(DataComponents.DAMAGE, capacity));
        this.capacity = capacity;
        this.transferLimit = Math.max(1, transferLimit);
    }

    @Override public int getEnergyStored(ItemStack stack) {
        return Math.clamp(stack.getMaxDamage() - stack.getDamageValue(), 0, capacity);
    }

    @Override public int getMaxEnergyStored(ItemStack stack) { return capacity; }

    @Override public void setEnergy(ItemStack stack, int energy) {
        stack.setDamageValue(capacity - Math.clamp(energy, 0, capacity));
    }

    @Override public int extractEnergy(ItemStack stack, int maxExtract, boolean simulate) {
        int extracted = Math.min(getEnergyStored(stack), Math.min(Math.max(0, maxExtract), transferLimit));
        if (!simulate && extracted > 0) setEnergy(stack, getEnergyStored(stack) - extracted);
        return extracted;
    }

    @Override public int receiveEnergy(ItemStack stack, int maxReceive, boolean simulate) {
        int received = Math.min(capacity - getEnergyStored(stack),
                Math.min(Math.max(0, maxReceive), transferLimit));
        if (!simulate && received > 0) setEnergy(stack, getEnergyStored(stack) + received);
        return received;
    }

    /** Internal consumption is not constrained by per-tick charging bandwidth. */
    protected final boolean consume(ItemStack stack, int amount) {
        if (amount <= 0) return true;
        int stored = getEnergyStored(stack);
        if (stored < amount) return false;
        setEnergy(stack, stored - amount);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.literal(getEnergyStored(stack) + "/" + capacity + " IF")
                .withStyle(ChatFormatting.AQUA));
    }
}
