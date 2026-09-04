package com.mohistmc.academy.world.item;

import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Drinkable 2,000 CP recovery capped at one half of maximum CP. */
public final class CpPotionItem extends AcademyItem {
    public CpPotionItem() { super(new Properties().stacksTo(16)); }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.DRINK; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 50; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (data.getCurrentCp() >= data.getMaxCp() * 0.5F) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity living) {
        if (living instanceof ServerPlayer player) {
            PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            float before = data.getCurrentCp();
            data.restoreCp(Math.min(2_000F, Math.max(0, data.getMaxCp() * 0.5F - before)));
            data.syncTo(player);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
                if (stack.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
                if (!player.getInventory().add(new ItemStack(Items.GLASS_BOTTLE)))
                    player.drop(new ItemStack(Items.GLASS_BOTTLE), false);
            }
            player.playSound(SoundEvents.GENERIC_DRINK, 0.5F, 1.0F);
        }
        return stack;
    }
}
