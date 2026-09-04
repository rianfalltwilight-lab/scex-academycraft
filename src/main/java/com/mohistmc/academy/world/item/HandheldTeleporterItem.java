package com.mohistmc.academy.world.item;

import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Overworld-only bind-and-return teleporter consuming 5,000 IF and one ender pearl. */
public final class HandheldTeleporterItem extends ExtraEnergyItem {
    public HandheldTeleporterItem() { super(10_000, 100); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.dimension().equals(Level.OVERWORLD)) return InteractionResultHolder.fail(stack);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.fail(stack);

        ExtraItemData.TeleportTarget target = ExtraItemData.teleport(stack);
        if (player.isShiftKeyDown()) {
            if (target == null) {
                ExtraItemData.setTeleport(stack, Level.OVERWORLD.location(),
                        player.getX(), player.getY(), player.getZ());
                player.sendSystemMessage(Component.translatable("item.academy.handheld_teleporter.bound"));
            } else {
                ExtraItemData.clearTeleport(stack);
                player.sendSystemMessage(Component.translatable("item.academy.handheld_teleporter.cleared"));
            }
            return InteractionResultHolder.consume(stack);
        }
        if (target == null || (!player.getAbilities().instabuild
                && (getEnergyStored(stack) < 5_000 || !ExtraItemActions.has(player, Items.ENDER_PEARL))))
            return InteractionResultHolder.fail(stack);

        ServerLevel destination = serverPlayer.server.getLevel(ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, target.dimension()));
        if (destination == null || destination != serverPlayer.serverLevel())
            return InteractionResultHolder.fail(stack);
        if (!player.getAbilities().instabuild) {
            if (!ExtraItemActions.consumeOne(player, Items.ENDER_PEARL)) return InteractionResultHolder.fail(stack);
            consume(stack, 5_000);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.8F, 1.0F);
        serverPlayer.teleportTo(destination, target.x(), target.y(), target.z(), Set.of(),
                player.getYRot(), player.getXRot());
        destination.playSound(null, serverPlayer.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.8F, 1.1F);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        ExtraItemData.TeleportTarget target = ExtraItemData.teleport(stack);
        if (target == null) tooltip.add(Component.translatable(
                "item.academy.handheld_teleporter.unbound").withStyle(ChatFormatting.GRAY));
        else tooltip.add(Component.translatable("item.academy.handheld_teleporter.position",
                (int) target.x(), (int) target.y(), (int) target.z()).withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, context, tooltip, flag);
    }

    @Override public boolean isFoil(ItemStack stack) { return ExtraItemData.teleport(stack) != null; }
}
