package com.mohistmc.academy.world.block;

import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.entity.MatrixSubBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

public class MatrixSubBlock extends BaseEntityBlock {
    public static final MapCodec<MatrixSubBlock> CODEC = simpleCodec(MatrixSubBlock::new);
    public MatrixSubBlock(Properties properties) {
        super(properties);
    }

    @Override public String getDescriptionId() { return "block.academy.matrix"; }

    /** Every visible 2x2x2 part is the same logical Matrix to pick/Jade. */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level,
                                       BlockPos pos, Player player) {
        return new ItemStack(AcademyBlocks.MATRIX.get());
    }

    @Override
    protected MapCodec<MatrixSubBlock> codec() {
        return CODEC;
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, net.minecraft.world.level.material.FluidState fluid) {
        if (!level.isClientSide) {
            BlockPos main = findMain(level, pos);
            if (main != null) {
                level.destroyBlock(main, !player.getAbilities().instabuild, player);
                return false;
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    static BlockPos findMain(Level level, BlockPos sub) {
        // Upper proxy blocks belong to a main block one level below.  Search
        // both possible layers and then require the oriented footprint to
        // contain this exact proxy; this avoids capturing an adjacent matrix.
        for (BlockPos main : BlockPos.betweenClosed(sub.offset(-1, -1, -1), sub.offset(1, 0, 1))) {
            BlockState candidate = level.getBlockState(main);
            if (candidate.getBlock() instanceof Matrix && Matrix.structurePositions(main, candidate).contains(sub)) {
                return main.immutable();
            }
        }
        return null;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                            BlockHitResult hitResult) {
        BlockPos mainPos = findMain(level, pos);
        if (mainPos == null) return InteractionResult.PASS;

        BlockState mainState = level.getBlockState(mainPos);
        if (!(mainState.getBlock() instanceof Matrix matrix)) return InteractionResult.PASS;

        // A formed matrix is one logical machine. Forward interaction from every
        // visible 2x2 part while keeping menu creation authoritative at the main BE.
        return matrix.useWithoutItem(mainState, level, mainPos, player, hitResult);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                // Several legacy 2x2x2 proxies are diagonal from the main and
                // therefore cannot notify it through vanilla's six-neighbour
                // neighborChanged path. Resolve ownership while the structure
                // still exists and defer the same authoritative completeness
                // check used by directly adjacent proxies.
                BlockPos mainPos = findMain(level, pos);
                if (mainPos != null) {
                    BlockState mainState = level.getBlockState(mainPos);
                    if (mainState.getBlock() instanceof Matrix matrix) {
                        level.scheduleTick(mainPos, matrix, 1);
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbor, boolean p_60514_) {
        if (!level.isClientSide && findMain(level, pos) == null) {
            // A sub-block belongs to the main whose oriented footprint contains it.
            // Unrelated neighbouring matrix changes must never invalidate it.
            level.destroyBlock(pos, false);
        }
        super.neighborChanged(state, level, pos, block, neighbor, p_60514_);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p_153215_, BlockState p_153216_) {
        return new MatrixSubBlockEntity(p_153215_, p_153216_);
    }


}
