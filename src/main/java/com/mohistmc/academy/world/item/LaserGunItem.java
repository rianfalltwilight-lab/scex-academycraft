package com.mohistmc.academy.world.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Eight-block continuous IF mining beam with server-side permission checks. */
public final class LaserGunItem extends ExtraEnergyItem {
    public LaserGunItem() { super(100_000, 200); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (ExtraItemData.isActive(stack)) stop(stack);
            else if (player.getAbilities().instabuild || getEnergyStored(stack) > 20)
                ExtraItemData.setActive(stack, true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !ExtraItemData.isActive(stack)) return;
        if (!(entity instanceof ServerPlayer player) || !selected) {
            stop(stack);
            return;
        }
        if (!player.getAbilities().instabuild && !consume(stack, 2)) {
            stop(stack);
            return;
        }

        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 end = start.add(direction.scale(8));
        BlockHitResult result = level.clip(new ClipContext(start, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double beamLength = result.getType() == HitResult.Type.MISS ? 8 : start.distanceTo(result.getLocation());
        ExtraItemActions.beam((ServerLevel) level, start, direction, beamLength);
        if (result.getType() != HitResult.Type.BLOCK) {
            ExtraItemData.setHarvest(stack, null, 0);
            return;
        }
        BlockPos pos = result.getBlockPos();
        BlockState state = level.getBlockState(pos);
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0 || state.isAir() || !player.mayUseItemAt(pos, result.getDirection(), stack)) {
            ExtraItemData.setHarvest(stack, null, 0);
            return;
        }
        float progress = pos.equals(ExtraItemData.harvestPos(stack))
                ? ExtraItemData.harvestProgress(stack) + 0.2F : 0.2F;
        if (progress + 1.0e-5F < hardness) {
            ExtraItemData.setHarvest(stack, pos, progress);
            return;
        }
        if (player.gameMode.destroyBlock(pos)) {
            level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.BLOCKS, 0.18F, 1.8F);
        }
        ExtraItemData.setHarvest(stack, null, 0);
    }

    private static void stop(ItemStack stack) {
        ExtraItemData.setActive(stack, false);
        ExtraItemData.setHarvest(stack, null, 0);
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
