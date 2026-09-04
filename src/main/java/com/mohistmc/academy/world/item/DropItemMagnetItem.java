package com.mohistmc.academy.world.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Hold-use item magnet with the original eight-block working radius. */
public final class DropItemMagnetItem extends ExtraEnergyItem {
    public DropItemMagnetItem() { super(10_000, 100); }

    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72_000; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.getAbilities().instabuild && getEnergyStored(stack) < 2)
            return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remainingTicks) {
        if (!(living instanceof Player player) || level.isClientSide) return;
        if (!player.getAbilities().instabuild && !consume(stack, 2)) {
            player.stopUsingItem();
            return;
        }
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(8), entity -> entity.isAlive() && !entity.hasPickUpDelay())) {
            Vec3 delta = player.position().add(0, 0.7, 0).subtract(item.position());
            if (delta.lengthSqr() < 1) continue;
            Vec3 velocity = item.getDeltaMovement().scale(0.82).add(delta.scale(0.02));
            item.setDeltaMovement(velocity);
            item.hurtMarked = true;
        }
    }
}
