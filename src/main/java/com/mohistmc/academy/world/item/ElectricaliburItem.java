package com.mohistmc.academy.world.item;

import com.mohistmc.academy.skill.AcademyDamageHelper;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/** Energy sword with the add-on's coin-fed 15-block railgun alternate fire. */
public final class ElectricaliburItem extends ExtraEnergyItem {
    public ElectricaliburItem() {
        super(new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)
                .attributes(SwordItem.createAttributes(Tiers.IRON, 12, -2.8F)), 100_000, 200);
    }

    @Override
    public float getAttackDamageBonus(Entity target, float baseAttackDamage, DamageSource source) {
        Entity attacker = source.getEntity();
        if (!(attacker instanceof LivingEntity living)) return 0;
        ItemStack held = living.getMainHandItem();
        return held.is(this) && getEnergyStored(held) >= 500 ? 1.0F : -11.0F;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return attacker instanceof Player player && (player.getAbilities().instabuild || consume(stack, 500));
    }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72_000; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild
                && (!ExtraItemActions.has(player, com.mohistmc.academy.world.AcademyItems.COIN.get())
                || getEnergyStored(stack) < 10_000))
            return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (!(living instanceof ServerPlayer player) || getUseDuration(stack, living) - timeLeft < 20) return;
        if (!player.getAbilities().instabuild) {
            if (getEnergyStored(stack) < 10_000
                    || !ExtraItemActions.consumeOne(player, com.mohistmc.academy.world.AcademyItems.COIN.get())) return;
            consume(stack, 10_000);
        }
        LivingEntity target = ExtraItemActions.firstLivingOnRay(player, 15, 0.45);
        if (target != null) {
            AcademyDamageHelper.hurtNonAbility(target, player.damageSources().playerAttack(player), 30);
        }
        ExtraItemActions.beam(player.serverLevel(), player.getEyePosition(), player.getLookAngle(), 15);
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.PLAYERS, 0.65F, 1.45F);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.academy.electricalibur.railgun")
                .withStyle(ChatFormatting.GOLD));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
