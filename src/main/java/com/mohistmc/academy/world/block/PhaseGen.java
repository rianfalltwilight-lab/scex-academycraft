package com.mohistmc.academy.world.block;

import com.mohistmc.academy.world.AcademyBlockEntities;
import com.mohistmc.academy.world.block.entity.PhaseGenBlockEntity;
import com.mohistmc.academy.world.block.entity.AcademyContainerBlockEntity;
import com.mohistmc.academy.world.menu.PhaseGenMenu;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class PhaseGen extends BaseEntityBlock {
    public static final MapCodec<PhaseGen> CODEC = simpleCodec(PhaseGen::new);
    private static final IntegerProperty WORKING = IntegerProperty.create("working", 0, 4);
    private static final BooleanProperty LIT = BooleanProperty.create("lit");

    public PhaseGen(Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any()
                .setValue(WORKING, 0)
                .setValue(LIT, false));
    }

    @Override
    protected MapCodec<PhaseGen> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WORKING, LIT);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            player.openMenu(getMenuProvider(state, level, pos), pos);
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
                return Component.translatable("block.academy.phase_gen");
            }

            @Override
            public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player player) {
                return new PhaseGenMenu(windowId, inv, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos));
            }
        };
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PhaseGenBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof PhaseGenBlockEntity phaseGen) {
                if (level instanceof net.minecraft.server.level.ServerLevel server) {
                    com.mohistmc.academy.energy.impl.WirelessSystem.unlinkUser(server, phaseGen);
                }
                AcademyContainerBlockEntity.dropAndClearContents(level, pos, phaseGen);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, AcademyBlockEntities.PHASE_GEN.get(), (lvl, pos, st, be) -> {
            if (be instanceof PhaseGenBlockEntity pgbe) {
                pgbe.tick();
                boolean working = pgbe.isWorking();
                // The 1.0.7 TESR selected one of five model textures from the
                // tank fill ratio: round(4 * amount / capacity).  The modern
                // blockstate already exposes the same five `working` variants,
                // but leaving this property at its default made every world
                // model permanently look empty regardless of tank contents.
                int frame = Mth.clamp(Math.round(4.0f * pgbe.getFluidAmount()
                        / Math.max(1, pgbe.getTankSize())), 0, 4);
                BlockState current = lvl.getBlockState(pos);
                if (current.getValue(LIT) != working || current.getValue(WORKING) != frame) {
                    lvl.setBlock(pos, current.setValue(LIT, working).setValue(WORKING, frame), 3);
                }
            }
        });
    }
}
