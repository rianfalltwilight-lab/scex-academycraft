package com.mohistmc.academy.world.item;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Automatic emergency healer; it starts below half health and stops at full health. */
public final class AvalonItem extends ExtraEnergyItem {
    public AvalonItem() {
        super(new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC), 100_000, 200);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof LivingEntity living) || level.getGameTime() % 10 != 0) return;
        if (getEnergyStored(stack) <= 2_000) {
            ExtraItemData.setActive(stack, false);
            return;
        }
        if (living.getHealth() <= living.getMaxHealth() * 0.5F) ExtraItemData.setActive(stack, true);
        else if (living.getHealth() >= living.getMaxHealth()) ExtraItemData.setActive(stack, false);
        if (ExtraItemData.isActive(stack) && consume(stack, 2_000)) living.heal(1.0F);
    }

    @Override public boolean isFoil(ItemStack stack) { return ExtraItemData.isActive(stack); }
}
