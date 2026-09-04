package com.mohistmc.academy.world.block;

import com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity;
import com.mohistmc.academy.world.menu.WindGenMainMenu;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WindGenMain extends BaseEntityBlock {
    public static final MapCodec<WindGenMain> CODEC = simpleCodec(WindGenMain::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public WindGenMain(Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any()
                .setValue(FACING, Direction.NORTH));
    }

    /**
     * The 1.0.7 wind head is a three-block multiblock.  The centre owns the
     * inventory/menu and the two blocks on its facing axis are interaction
     * proxies.  They are structural parts, not an installed-fan flag.
     */
    public static List<BlockPos> proxyPositions(BlockPos main, BlockState state) {
        Direction facing = state.getValue(FACING);
        return List.of(main.relative(facing), main.relative(facing.getOpposite()));
    }

    public static boolean hasCompleteProxySet(Level level, BlockPos main, BlockState state) {
        Direction facing = state.getValue(FACING);
        return proxyPositions(main, state).stream()
                .allMatch(pos -> level.getBlockState(pos).getBlock() instanceof WindGenFan
                        && level.getBlockState(pos).getValue(WindGenFan.FACING) == facing);
    }


    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p_153215_, BlockState p_153216_) {
        return new WindGenMainBlockEntity(p_153215_, p_153216_);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer != null && level.getBlockEntity(pos) instanceof WindGenMainBlockEntity wind) {
            wind.setOwnerUUID(placer.getUUID());
        }
    }

    @Override
    protected MapCodec<WindGenMain> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState p_49232_) {
        return RenderShape.MODEL;
    }


    @Override
    public BlockState rotate(BlockState p_48722_, Rotation p_48723_) {
        return p_48722_.setValue(FACING, p_48723_.rotate(p_48722_.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState p_48719_, Mirror p_48720_) {
        return p_48719_.rotate(p_48720_.getRotation(p_48719_.getValue(FACING)));
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level p_153212_, BlockState p_153213_, BlockEntityType<T> p_153214_) {
        return (level, pos, state, p_155256_) -> {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof WindGenMainBlockEntity blockEntity) {
                blockEntity.tick(this, level, pos, state.getValue(FACING));
            }
        };
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

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult p_60508_) {
        if (!level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof WindGenMainBlockEntity wind) {
                // Old rebuild saves may not contain an owner.  Only an
                // administrator may migrate them; a random visitor must not
                // gain authority over protected automatic fan placement.
                wind.claimLegacyOwnerIfAbsent(player);
            }
            player.openMenu(getMenuProvider(state, level, pos), pos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos neighbor, boolean movedByPiston) {
        if (!level.isClientSide && !hasCompleteProxySet(level, pos, state)) {
            // Defer one tick: the transactional item places the centre before
            // its two proxies, so an immediate check would tear down a valid
            // placement halfway through its transaction.
            level.scheduleTick(pos, this, 1);
        }
        super.neighborChanged(state, level, pos, block, neighbor, movedByPiston);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!hasCompleteProxySet(level, pos, state)) {
            Block.popResource(level, pos, new ItemStack(this));
            level.destroyBlock(pos, false);
        }
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {

        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("block.academy.windgen_main");
            }

            @Override
            public AbstractContainerMenu createMenu(int p_39954_, Inventory inv, Player p_39956_) {
                return new WindGenMainMenu(p_39954_, inv, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos));
            }
        };
    }

    @Override
    public void onRemove(BlockState p_60515_, Level world, BlockPos pos, BlockState p_60518_, boolean p_60519_) {
        if (!p_60515_.is(p_60518_.getBlock())) {
            BlockEntity entity = world.getBlockEntity(pos);
            if (!world.isClientSide && entity instanceof WindGenMainBlockEntity blockEntity) {
                blockEntity
                        .getItems()
                        .forEach(item -> {
                            world.addFreshEntity(new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item));
                        });
                blockEntity
                        .getItems().clear();
            }
            if (!world.isClientSide) {
                for (BlockPos proxy : proxyPositions(pos, p_60515_)) {
                    if (world.getBlockState(proxy).getBlock() instanceof WindGenFan) {
                        world.destroyBlock(proxy, false);
                    }
                }
            }
            super.onRemove(p_60515_, world, pos, p_60518_, p_60519_);
        }

    }
}
