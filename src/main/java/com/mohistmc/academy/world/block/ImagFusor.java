package com.mohistmc.academy.world.block;

import com.mohistmc.academy.world.AcademyBlockEntities;
import com.mohistmc.academy.world.block.entity.ImagFusorBlockEntity;
import com.mohistmc.academy.world.block.entity.AcademyContainerBlockEntity;
import com.mohistmc.academy.world.menu.ImagFusorMenu;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ImagFusor extends BaseEntityBlock {

    public static final MapCodec<ImagFusor> CODEC = simpleCodec(ImagFusor::new);
    private static final IntegerProperty WORKING = IntegerProperty.create("working", 0, 4);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public ImagFusor(Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any().setValue(WORKING, 0).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_49915_) {
        p_49915_.add(WORKING, FACING);
        super.createBlockStateDefinition(p_49915_);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext p_49820_) {
        return this.defaultBlockState().setValue(FACING, p_49820_.getHorizontalDirection().getOpposite());
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
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            // 必须带 pos 重载，否则客户端 Menu 收到的 buf 为空，menu.pos 为 null
            player.openMenu(getMenuProvider(state, level, pos), pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new MenuProvider() {
            @Override
            public net.minecraft.network.chat.Component getDisplayName() {
                return net.minecraft.network.chat.Component.translatable("container.academy.imag_fusor");
            }

            @Override
            public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player player) {
                return new ImagFusorMenu(windowId, inv, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos));
            }
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ImagFusorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, AcademyBlockEntities.IMAG_FUSOR.get(), (lvl, pos, st, be) -> {
            if (be instanceof ImagFusorBlockEntity fusor) {
                if (lvl.isClientSide) {
                    com.mohistmc.academy.client.sound.MachineLoopSoundManager.tickImagFusor(fusor);
                    return;
                }
                fusor.tick();
                // Legacy BlockImagFusor used ief_off while idle and cycled
                // ief_working_1..4 every 400 ms while processing. Eight
                // server ticks provide the same cadence and synchronize a
                // deterministic baked-model state to every client.
                int frame = fusor.isWorking()
                        ? 1 + (int) ((lvl.getGameTime() / 8L) % 4L)
                        : 0;
                BlockState current = lvl.getBlockState(pos);
                if (current.getValue(WORKING) != frame) {
                    lvl.setBlock(pos, current.setValue(WORKING, frame), 3);
                }
            }
        });
    }

    public static boolean isWorkingState(BlockState state) {
        return state != null && state.getBlock() instanceof ImagFusor
                && state.hasProperty(WORKING) && state.getValue(WORKING) > 0;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ImagFusorBlockEntity blockEntity) {
                if (level instanceof net.minecraft.server.level.ServerLevel server) {
                    com.mohistmc.academy.energy.impl.WirelessSystem.unlinkUser(server, blockEntity);
                }
                AcademyContainerBlockEntity.dropAndClearContents(level, pos, blockEntity);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
