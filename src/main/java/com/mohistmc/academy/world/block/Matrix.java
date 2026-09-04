package com.mohistmc.academy.world.block;

import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import com.mohistmc.academy.world.menu.MatrixMenu;
import com.mohistmc.academy.network.NetworkInputLimits;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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
import org.jetbrains.annotations.Nullable;

public class Matrix extends BaseEntityBlock {
    public static final MapCodec<Matrix> CODEC = simpleCodec(Matrix::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public Matrix(Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<Matrix> codec() {
        return CODEC;
    }


    @Override
    public void setPlacedBy(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity placer, net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer != null && level.getBlockEntity(pos) instanceof com.mohistmc.academy.world.block.entity.MatrixBlockEntity be) {
            be.setOwnerUUID(placer.getUUID());
        }
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof MatrixBlockEntity matrix) {
                matrix.claimLegacyOwnerIfAbsent(player);
                if (level instanceof net.minecraft.server.level.ServerLevel server && matrix.canManage(player)) {
                    var recovery = com.mohistmc.academy.energy.impl.WirelessSystem
                            .reconcileMatrixNetwork(server, matrix);
                    if (recovery == com.mohistmc.academy.energy.impl.WirelessSystem.MatrixNetworkState.RECOVERED) {
                        player.sendSystemMessage(Component.literal("§a已恢复无线矩阵网络；请重新连接需要的节点"));
                    } else if (recovery == com.mohistmc.academy.energy.impl.WirelessSystem.MatrixNetworkState.NEEDS_REINITIALIZATION) {
                        player.sendSystemMessage(Component.literal("§e矩阵网络数据缺失且组件不完整，请补齐组件后重新初始化"));
                    } else if (recovery == com.mohistmc.academy.energy.impl.WirelessSystem.MatrixNetworkState.RECOVERY_FAILED) {
                        player.sendSystemMessage(Component.literal("§c矩阵网络恢复被拒绝或失败，请稍后重试"));
                    }
                }
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.openMenu(getMenuProvider(state, level, pos),
                            buffer -> MatrixMenu.writeOpeningData(buffer, pos, matrix, player));
                }
            }
            return InteractionResult.CONSUME;
        }
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("block.academy.matrix");
            }

            @Override
            public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player player) {
                return new MatrixMenu(windowId, inv, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos));
            }
        };
    }

    @Override
    public void animateTick(BlockState p_220827_, Level p_220828_, BlockPos p_220829_, RandomSource p_220830_) {
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState p_60569_, boolean p_60570_) {
        // Player placement is committed by MatrixBlockItem only after the main block and
        // all seven proxy positions pass placement/permission checks. Keeping this hook mutation-free also means
        // cancellation of the main BlockItem transaction cannot strand sub-blocks.
    }

    public static java.util.List<BlockPos> structurePositions(BlockPos pos, BlockState state) {
        Direction d = state.getValue(FACING).getOpposite();
        java.util.List<BlockPos> lower = switch (d) {
            case NORTH -> java.util.List.of(pos.south(), pos.east(), pos.east().south());
            case SOUTH -> java.util.List.of(pos.north(), pos.west(), pos.west().north());
            case WEST -> java.util.List.of(pos.east(), pos.north(), pos.north().east());
            case EAST -> java.util.List.of(pos.west(), pos.south(), pos.south().west());
            default -> java.util.List.of();
        };
        if (lower.isEmpty()) return lower;
        // AcademyCraft 1.0.7 BlockMatrix reserves the entire 2x2x2 logical
        // footprint: three peers beside the main at y=0 and all four cells at
        // y=1.  Reserving only the lower peers let blocks overlap the visible
        // upper half and made those parts impossible to interact with.
        return java.util.List.of(
                lower.get(0), lower.get(1), lower.get(2),
                pos.above(), lower.get(0).above(), lower.get(1).above(), lower.get(2).above());
    }

    public void createStructure(Level level, BlockPos pos, BlockState state) {
        BlockState sub = AcademyBlocks.MATRIX_SUB.get().defaultBlockState();
        for (BlockPos target : structurePositions(pos, state)) level.setBlock(target, sub, 19);
    }

    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
        com.mohistmc.academy.network.MatrixNodesPacket.clearMatrix(pos);
        removeStructureParts(level, pos, state);
    }

    private static void removeStructureParts(LevelAccessor level, BlockPos pos, BlockState state) {
        for (BlockPos subPos : structurePositions(pos, state)) {
            if (level.getBlockState(subPos).getBlock() instanceof MatrixSubBlock) {
                level.destroyBlock(subPos, false);
            }
        }
    }

    private static boolean hasCompleteStructure(LevelAccessor level, BlockPos pos, BlockState state) {
        return structurePositions(pos, state).stream()
                .allMatch(part -> level.getBlockState(part).getBlock() instanceof MatrixSubBlock);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos neighbor, boolean p_60514_) {
        if (!level.isClientSide && structurePositions(pos, state).contains(neighbor)
                && !(level.getBlockState(neighbor).getBlock() instanceof MatrixSubBlock)) {
            // Only a missing part owned by this main block invalidates this matrix.
            // Looking at the type of an arbitrary changed neighbour made adjacent
            // matrices recursively destroy one another.  Defer teardown one tick
            // so a placement transaction or a same-tick repair can finish first.
            level.scheduleTick(pos, this, 1);
        }
        super.neighborChanged(state, level, pos, block, neighbor, p_60514_);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!hasCompleteStructure(level, pos, state)) {
            // Structural invalidation is not a player loot path. Materialize the
            // recoverable main block explicitly, then let onRemove drop the four
            // installed components and tear down the network exactly once.
            Block.popResource(level, pos, new ItemStack(this));
            level.destroyBlock(pos, false);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof MatrixBlockEntity matrix) {
                if (level instanceof ServerLevel server) {
                    com.mohistmc.academy.energy.impl.WirelessSystem.removeNetwork(server, matrix);
                }
                // Matrix components are installed parts in 1.0.7, not an
                // initialization cost. Drop the live stacks and clear the slots
                // before removing the BE so no alternate callback can duplicate them.
                for (ItemStack stack : matrix.getItems()) {
                    if (!stack.isEmpty()) {
                        Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5,
                                pos.getZ() + 0.5, stack);
                    }
                }
                matrix.getItems().clear();
                matrix.setChanged();
            }
            if (!level.isClientSide()) {
                com.mohistmc.academy.network.MatrixNodesPacket.clearMatrix(pos);
                removeStructureParts(level, pos, state);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
    @Override
    public RenderShape getRenderShape(BlockState p_49232_) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p_153215_, BlockState p_153216_) {
        return new MatrixBlockEntity(p_153215_, p_153216_);
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
