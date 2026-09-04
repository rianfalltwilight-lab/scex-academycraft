package com.mohistmc.academy.world.block;

import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.entity.AcademyContainerBlockEntity;
import com.mohistmc.academy.world.block.entity.WindGenBaseBlockEntity;
import com.mohistmc.academy.world.menu.WindGenBaseMenu;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class WindGenBase extends BaseEntityBlock {
    public static final MapCodec<WindGenBase> CODEC = simpleCodec(WindGenBase::new);
    /** AcademyCraft 1.0.7 WindGenerator.MIN_PILLARS / MAX_PILLARS. */
    public static final int MIN_PILLARS = 8;
    public static final int MAX_PILLARS = 40;
    /** Runtime structure state used by the block model and synced to clients. */
    public static final BooleanProperty ENABLE = BooleanProperty.create("enable");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public WindGenBase(Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any().setValue(ENABLE, false).setValue(FACING, Direction.NORTH));

    }

    @Override
    protected MapCodec<WindGenBase> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_49915_) {
        p_49915_.add(ENABLE, FACING);
        super.createBlockStateDefinition(p_49915_);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext p_49820_) {
        return this.defaultBlockState().setValue(FACING, p_49820_.getHorizontalDirection().getOpposite());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState p_60569_, boolean p_60570_) {
        // Structure placement is owned by WindGenBaseBlockItem's atomic transaction.
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level p_153212_, BlockState p_153213_, BlockEntityType<T> p_153214_) {
        return (level, pos, p_155255_, p_155256_) -> {
            int mainHeight = 0;
            int mainY = pos.getY() + 2;
            boolean middleComplete = false;
            boolean structureComplete = false;
            boolean working = false;
            // The two-block base occupies y and y+1.  Pillars start at y+2,
            // and the legacy turbine accepts exactly 8..40 of them.  Stop as
            // soon as the maximum is exceeded instead of scanning a tall
            // pillar column every tick.
            for (int i = 2; i <= MAX_PILLARS + 2; i++) {
                BlockPos scanPos = pos.above(i);
                Block block = level.getBlockState(scanPos).getBlock();
                if (block instanceof WindGenPillar) {
                    mainHeight++;
                    middleComplete = mainHeight >= MIN_PILLARS;
                    if (mainHeight > MAX_PILLARS) break;
                    continue;
                } else if (block instanceof WindGenMain) {
                    mainY = scanPos.getY();
                    if (mainHeight >= MIN_PILLARS && mainHeight <= MAX_PILLARS) {
                        BlockEntity mainEntity = level.getBlockEntity(scanPos);
                        if (mainEntity instanceof com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity wind) {
                            working = wind.refreshIfDue(level, scanPos,
                                    level.getBlockState(scanPos).getValue(WindGenMain.FACING));
                            structureComplete = wind.isStructureComplete();
                        }
                    }
                    break;
                }
                break;
            }

            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof WindGenBaseBlockEntity blockEntity) {
                blockEntity.tick(structureComplete, middleComplete, working, mainY);
            }
        };
    }

    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockPos subPos = pos.above();
        if (level.getBlockState(subPos).getBlock() instanceof WindGenBaseSubBlock) {
            level.destroyBlock(subPos, false);
        }
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult p_60508_) {
        if (!level.isClientSide()) {
            player.openMenu(getMenuProvider(state, level, pos), pos);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {

        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("block.academy.windgen_base");
            }

            @Override
            public AbstractContainerMenu createMenu(int p_39954_, Inventory inv, Player p_39956_) {
                return new WindGenBaseMenu(p_39954_, inv, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos));
            }
        };
    }


    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbor, boolean p_60514_) {
        if (!level.isClientSide && neighbor.equals(pos.above())
                && !(level.getBlockState(neighbor).getBlock() instanceof WindGenBaseSubBlock)) {
            Block.popResource(level, pos, new net.minecraft.world.item.ItemStack(this));
            level.destroyBlock(pos, false);
        }
        super.neighborChanged(state, level, pos, block, neighbor, p_60514_);
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
    public BlockEntity newBlockEntity(BlockPos p_153215_, BlockState p_153216_) {
        return new WindGenBaseBlockEntity(p_153215_, p_153216_);
    }

    @Override
    public RenderShape getRenderShape(BlockState p_49232_) {
        return RenderShape.MODEL;
    }

    @Override
    public void onRemove(BlockState p_60515_, Level world, BlockPos pos, BlockState p_60518_, boolean p_60519_) {
        if (!p_60515_.is(p_60518_.getBlock())) {
            BlockEntity entity = world.getBlockEntity(pos);
            if (!world.isClientSide && entity instanceof AcademyContainerBlockEntity blockEntity) {
                if (world instanceof net.minecraft.server.level.ServerLevel server
                        && blockEntity instanceof com.mohistmc.academy.energy.api.block.IWirelessUser user) {
                    com.mohistmc.academy.energy.impl.WirelessSystem.unlinkUser(server, user);
                }
                blockEntity
                        .getItems()
                        .forEach(item -> {
                            world.addFreshEntity(new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, item));
                        });
                blockEntity.getItems().clear();
            }
            if (!world.isClientSide && world.getBlockState(pos.above()).getBlock() instanceof WindGenBaseSubBlock) {
                world.destroyBlock(pos.above(), false);
            }
            super.onRemove(p_60515_, world, pos, p_60518_, p_60519_);
        }

    }
}
