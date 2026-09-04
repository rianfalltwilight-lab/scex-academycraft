package com.mohistmc.academy.world.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** Restores the 1.0.7 converter direction tooltip. */
public final class EnergyBridgeBlockItem extends BlockItem {
    private final boolean input;

    public EnergyBridgeBlockItem(Block block, Item.Properties properties, boolean input) {
        super(block, properties);
        this.input = input;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(input
                ? "item.academy.energy_bridge.input_desc"
                : "item.academy.energy_bridge.output_desc"));
        tooltip.add(Component.translatable("item.academy.energy_bridge.ratio"));
    }
}
