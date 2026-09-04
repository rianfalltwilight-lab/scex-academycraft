package com.mohistmc.academy.world.item;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 500 IF, ten-second reusable forward dash. */
public final class AirJetDeviceItem extends ExtraEnergyItem {
    public static final int COOLDOWN_TICKS = 200;
    public AirJetDeviceItem() { super(10_000, 100); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        long elapsed = level.getGameTime() - ExtraItemData.lastUse(stack);
        if (elapsed >= 0 && elapsed < COOLDOWN_TICKS
                || (!player.getAbilities().instabuild && getEnergyStored(stack) < 500))
            return InteractionResultHolder.fail(stack);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!player.getAbilities().instabuild) consume(stack, 500);
            Vec3 impulse = player.getLookAngle().normalize().scale(2.0);
            player.push(impulse.x, impulse.y, impulse.z);
            player.hurtMarked = true;
            ExtraItemData.setLastUse(stack, level.getGameTime());
            serverPlayer.serverLevel().sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY() + 0.7, player.getZ(), 18, 0.3, 0.35, 0.3, 0.08);
            level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH,
                    SoundSource.PLAYERS, 0.6F, 1.4F);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
