package com.mohistmc.academy.world.block;

import com.mohistmc.academy.energy.api.block.IWirelessUser;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.world.block.entity.EnergyBridgeOutputBlockEntity;
import com.mohistmc.academy.world.menu.EnergyBridgeMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Shared interaction shell for the two 1.0.7 RF energy bridges. */
public abstract class EnergyBridgeBlock extends BaseEntityBlock {
    private final String translationKey;

    protected EnergyBridgeBlock(Properties properties, String translationKey) {
        super(properties);
        this.translationKey = translationKey;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                             Player player, BlockHitResult hit) {
        if (!level.isClientSide()) player.openMenu(getMenuProvider(state, level, pos), pos);
        return InteractionResult.CONSUME;
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new MenuProvider() {
            @Override public Component getDisplayName() {
                return Component.translatable(translationKey);
            }

            @Override public AbstractContainerMenu createMenu(int windowId, Inventory inventory,
                                                               Player player) {
                return new EnergyBridgeMenu(windowId, inventory,
                        new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos));
            }
        };
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (tickLevel, pos, tickState, entity) -> {
            if (entity instanceof EnergyBridgeOutputBlockEntity output) output.serverTick();
        };
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (level instanceof ServerLevel server && entity instanceof IWirelessUser user) {
                WirelessSystem.unlinkUser(server, user);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
