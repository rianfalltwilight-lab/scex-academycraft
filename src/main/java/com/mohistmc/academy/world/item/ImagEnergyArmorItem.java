package com.mohistmc.academy.world.item;

import com.mohistmc.academy.capability.IEnergyItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** 100,000 IF powered armour; damage absorption is settled atomically by the event handler. */
public final class ImagEnergyArmorItem extends ArmorItem implements IEnergyItem {
    public static final int MAX_ENERGY = 100_000;

    public ImagEnergyArmorItem(Type type) {
        super(ExtraArmorMaterials.IMAGINARY, type, new Item.Properties().rarity(net.minecraft.world.item.Rarity.UNCOMMON)
                .stacksTo(1));
    }

    @Override public int getEnergyStored(ItemStack stack) {
        return ExtraItemData.energy(stack, MAX_ENERGY);
    }
    @Override public int getMaxEnergyStored(ItemStack stack) { return MAX_ENERGY; }
    @Override public void setEnergy(ItemStack stack, int energy) {
        ExtraItemData.setEnergy(stack, energy, MAX_ENERGY);
    }
    @Override public int extractEnergy(ItemStack stack, int maximum, boolean simulate) {
        int extracted = Math.min(getEnergyStored(stack), Math.min(Math.max(0, maximum), 200));
        if (!simulate && extracted > 0) setEnergy(stack, getEnergyStored(stack) - extracted);
        return extracted;
    }
    @Override public int receiveEnergy(ItemStack stack, int maximum, boolean simulate) {
        int received = Math.min(MAX_ENERGY - getEnergyStored(stack), Math.min(Math.max(0, maximum), 200));
        if (!simulate && received > 0) setEnergy(stack, getEnergyStored(stack) + received);
        return received;
    }

    int consumeForDamage(ItemStack stack, int amount) {
        int consumed = Math.min(getEnergyStored(stack), Math.max(0, amount));
        if (consumed > 0) setEnergy(stack, getEnergyStored(stack) - consumed);
        return consumed;
    }

    @Override public boolean isBarVisible(ItemStack stack) {
        return getEnergyStored(stack) < MAX_ENERGY;
    }

    @Override public int getBarWidth(ItemStack stack) {
        return Math.round(13F * getEnergyStored(stack) / MAX_ENERGY);
    }

    @Override public int getBarColor(ItemStack stack) { return 0x38C7FF; }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(getEnergyStored(stack) + "/" + MAX_ENERGY + " IF")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.academy.imag_energy_armor.desc")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
