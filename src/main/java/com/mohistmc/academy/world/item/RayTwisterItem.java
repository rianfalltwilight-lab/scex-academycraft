package com.mohistmc.academy.world.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Hotbar-powered invisibility device from ExtraAcC, reimplemented for 1.21.1. */
public final class RayTwisterItem extends ExtraEnergyItem {
    public RayTwisterItem() { super(10_000, 100); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player,
                                                   InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (ExtraItemData.isActive(stack)) ExtraItemData.setActive(stack, false);
            else if (player.getAbilities().instabuild || getEnergyStored(stack) > 40)
                ExtraItemData.setActive(stack, true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !ExtraItemData.isActive(stack)) return;
        if (!(entity instanceof ServerPlayer player) || slot >= 9) {
            ExtraItemData.setActive(stack, false);
            return;
        }
        if (!player.getAbilities().instabuild && !consume(stack, 1)) {
            ExtraItemData.setActive(stack, false);
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 39, 0,
                false, false, false));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(ExtraItemData.isActive(stack)
                ? "item.academy.extra.enabled" : "item.academy.extra.disabled")
                .withStyle(ExtraItemData.isActive(stack) ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override public boolean isFoil(ItemStack stack) { return ExtraItemData.isActive(stack); }
}
