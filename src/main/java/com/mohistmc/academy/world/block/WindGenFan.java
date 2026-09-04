package com.mohistmc.academy.world.block;

import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.entity.WindGenFanBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

public class WindGenFan extends BaseEntityBlock {
    public static final MapCodec<WindGenFan> CODEC = simpleCodec(WindGenFan::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public WindGenFan(Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any()
                .setValue(FACING, Direction.NORTH));
    }

    /** The invisible head proxies are one logical wind-generator main block. */
    @Override public String getDescriptionId() { return "block.academy.windgen_main"; }

    @Override
    protected MapCodec<WindGenFan> codec() {
        return CODEC;
    }

    @Override
    public void animateTick(BlockState state, Level p_220828_, BlockPos p_220829_, RandomSource p_220830_) {

    }


    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p_153215_, BlockState p_153216_) {
        return new WindGenFanBlockEntity(p_153215_, p_153216_);
    }

    @Override
    public RenderShape getRenderShape(BlockState p_49232_) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(AcademyItems.WINDGEN_MAIN.get());
    }

    /** Locate the centre block which owns this invisible multiblock proxy. */
    @Nullable
    public static BlockPos findMain(BlockGetter level, BlockPos proxy) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos candidate = proxy.relative(direction);
            BlockState state = level.getBlockState(candidate);
            if (state.getBlock() instanceof WindGenMain
                    && WindGenMain.proxyPositions(candidate, state).contains(proxy)) {
                return candidate.immutable();
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
        if (!(mainState.getBlock() instanceof WindGenMain main)) return InteractionResult.PASS;
        return main.useWithoutItem(mainState, level, mainPos, player, hitResult);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        if (!level.isClientSide) {
            BlockPos mainPos = findMain(level, pos);
            if (mainPos != null) {
                // The proxy itself has an empty loot table.  Destroying it
                // destroys the logical machine and yields exactly one centre
                // item (or none in creative), just like 1.0.7 BlockMulti.
                if (!player.getAbilities().instabuild) {
                    Block.popResource(level, mainPos, new ItemStack(AcademyItems.WINDGEN_MAIN.get()));
                }
                level.destroyBlock(mainPos, false, player);
                return false;
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos neighbor, boolean movedByPiston) {
        if (!level.isClientSide && findMain(level, pos) == null) {
            level.destroyBlock(pos, false);
        }
        super.neighborChanged(state, level, pos, block, neighbor, movedByPiston);
    }

    @Override
    public BlockState rotate(BlockState p_48722_, Rotation p_48723_) {
        return p_48722_.setValue(FACING, p_48723_.rotate(p_48722_.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState p_48719_, Mirror p_48720_) {
        return p_48719_.rotate(p_48720_.getRotation(p_48719_.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_49915_) {
        p_49915_.add(FACING);
        super.createBlockStateDefinition(p_49915_);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext p_49820_) {
        return this.defaultBlockState().setValue(FACING, p_49820_.getHorizontalDirection().getOpposite());
    }
}
