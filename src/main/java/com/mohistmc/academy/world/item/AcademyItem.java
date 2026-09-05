package com.mohistmc.academy.world.item;

import com.mohistmc.academy.capability.IEnergyItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class AcademyItem extends Item {
    public AcademyItem(Properties p_41383_) {
        super(p_41383_);
    }


    /** Energy devices retain their charge bar even though DAMAGE is not repairable wear. */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        if (this instanceof IEnergyItem energy) {
            return energy.getEnergyStored(stack) < energy.getMaxEnergyStored(stack);
        }
        return super.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        if (this instanceof IEnergyItem energy) {
            return Math.round(13F * energyFraction(stack, energy));
        }
        return super.getBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        if (this instanceof IEnergyItem energy) {
            return Mth.hsvToRgb(energyFraction(stack, energy) / 3F, 1F, 1F);
        }
        return super.getBarColor(stack);
    }

    private static float energyFraction(ItemStack stack, IEnergyItem energy) {
        int capacity = energy.getMaxEnergyStored(stack);
        return capacity <= 0 ? 0F : Math.clamp((float) energy.getEnergyStored(stack) / capacity, 0F, 1F);
    }

    @Override
    public void appendHoverText(ItemStack p_41421_, Item.TooltipContext p_333372_, List<Component> p_41423_, TooltipFlag p_41424_) {
        String key = getDescriptionId() + ".desc";
        Component tag = Component.translatable(key);
        if (!key.equalsIgnoreCase(tag.getString())) {
            p_41423_.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(p_41421_, p_333372_, p_41423_, p_41424_);
    }
}
