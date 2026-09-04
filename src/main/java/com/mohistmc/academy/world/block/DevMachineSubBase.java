package com.mohistmc.academy.world.block;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

public abstract class DevMachineSubBase extends BaseEntityBlock implements IDevMachine {

    public DevMachineSubBase(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        BlockPos mainPos = findMainBlock((Level) level, pos);
        if (mainPos != null) {
            Item item = level.getBlockState(mainPos).getBlock().asItem();
            if (item != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(item);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, net.minecraft.world.level.material.FluidState fluid) {
        if (!level.isClientSide) {
            BlockPos mainPos = findMainBlock(level, pos);
            if (mainPos != null) {
                level.destroyBlock(mainPos, !player.getAbilities().instabuild, player);
                return false;
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide() && !newState.is(state.getBlock())) {
            BlockPos mainPos = findMainBlock(level, pos, state.getBlock());
            if (mainPos != null) {
                BlockState mainState = level.getBlockState(mainPos);
                // A non-player removal has no vanilla loot path. Drop exactly
                // one main-machine item, then let the main block's onRemove own
                // proxy and inventory cleanup.  findMainBlock has already
                // proven that this exact proxy belongs to this oriented main.
                Item mainItem = mainState.getBlock().asItem();
                if (mainItem != net.minecraft.world.item.Items.AIR) {
                    Block.popResource(level, mainPos, new ItemStack(mainItem));
                }
                level.destroyBlock(mainPos, false);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos mainPos = findMainBlock(level, pos);
        if (mainPos != null) {
            BlockState mainState = level.getBlockState(mainPos);
            if (mainState.getBlock() instanceof DevMachineBase base) {
                return base.useWithoutItem(mainState, level, mainPos, player, hitResult);
            }
        }
        return InteractionResult.PASS;
    }

    @Nullable
    private BlockPos findMainBlock(Level level, BlockPos subPos) {
        return findMainBlock(level, subPos, level.getBlockState(subPos).getBlock());
    }

    /**
     * Never trust only the persisted UUID/mainPos pair.  Structure blocks can
     * be copied by admin/world-edit tools and old/corrupt NBT can contain an
     * arbitrary mainPos.  Requiring the same tier and exact rotated footprint
     * prevents one proxy from opening or destroying an unrelated developer.
     */
    @Nullable
    private BlockPos findMainBlock(Level level, BlockPos subPos, Block expectedSubBlock) {
        BlockEntity be = level.getBlockEntity(subPos);
        if (!(be instanceof IDevSubStructure sub)) return null;
        BlockEntity main = validatedMainForProxy(level, subPos, expectedSubBlock, sub);
        return main == null ? null : main.getBlockPos();
    }

    /** Shared by proxy interaction/removal and the NeoForge energy capability. */
    @Nullable
    public static BlockEntity validatedMainForProxy(Level level, BlockPos subPos,
                                                     Block expectedSubBlock, IDevSubStructure sub) {
        if (level == null || subPos == null || expectedSubBlock == null || sub == null) return null;
        BlockPos mainPos = sub.getMainPos();
        if (mainPos == null || !level.isLoaded(mainPos)
                || Math.abs(mainPos.getX() - subPos.getX()) > 2
                || Math.abs(mainPos.getY() - subPos.getY()) > 2
                || Math.abs(mainPos.getZ() - subPos.getZ()) > 2) return null;
        BlockState mainState = level.getBlockState(mainPos);
        if (!(mainState.getBlock() instanceof DevMachineBase base)
                || base.getStructureSubBlock() != expectedSubBlock) return null;
        Direction direction = mainState.getValue(DevMachineBase.FACING).getOpposite();
        boolean exactProxy = base.getRotatedSubBlocks(direction).stream()
                .map(offset -> mainPos.offset(offset.dx(), offset.dy(), offset.dz()))
                .anyMatch(subPos::equals);
        if (!exactProxy) return null;
        BlockEntity mainBe = level.getBlockEntity(mainPos);
        if (!(mainBe instanceof IDevStructure main)) return null;
        UUID subId = sub.getStructureId();
        return subId != null && subId.equals(main.getStructureId()) ? mainBe : null;
    }
}
