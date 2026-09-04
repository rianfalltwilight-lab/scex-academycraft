package com.mohistmc.academy.world.block;

import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import com.mohistmc.academy.world.AcademyBlockEntities;
import com.mohistmc.academy.world.block.entity.AbilityInterfererBlockEntity;
import com.mohistmc.academy.world.block.entity.AcademyContainerBlockEntity;
import com.mohistmc.academy.world.menu.AbilityInterfererMenu;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class AbilityInterferer extends BaseEntityBlock {
    public static final MapCodec<AbilityInterferer> CODEC = simpleCodec(AbilityInterferer::new);
    public static final IntegerProperty STATUS = IntegerProperty.create("status", 0, 1);

    public AbilityInterferer() {
        this(Properties.of().sound(SoundType.STONE).noOcclusion().strength(3.0f)
                .requiresCorrectToolForDrops());
    }

    public AbilityInterferer(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STATUS, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STATUS);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                             Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            if (level.getBlockEntity(pos) instanceof AbilityInterfererBlockEntity interferer) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    interferer.claimLegacyOwnerIfAbsent(serverPlayer);
                }
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                        && !interferer.canManage(serverPlayer)) {
                    player.sendSystemMessage(Component.translatable(
                            "message.academy.interferer.owner_only"));
                    return InteractionResult.CONSUME;
                }
            }
            player.openMenu(getMenuProvider(state, level, pos), pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("container.academy.ability_interferer");
            }

            @Override
            public AbstractContainerMenu createMenu(int windowId, Inventory inventory, Player player) {
                return new AbilityInterfererMenu(windowId, inventory,
                        new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos));
            }
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof AbilityInterfererBlockEntity interferer) {
            interferer.assignOwnerOnPlacement(player);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AbilityInterfererBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, AcademyBlockEntities.ABILITY_INTERFERER.get(),
                (ignoredLevel, ignoredPos, ignoredState, entity) -> entity.serverTick());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity raw = level.getBlockEntity(pos);
            if (raw instanceof AbilityInterfererBlockEntity interferer) {
                AbilityInterferenceService.remove(interferer);
                if (level instanceof net.minecraft.server.level.ServerLevel server) {
                    WirelessSystem.unlinkUser(server, interferer);
                }
                AcademyContainerBlockEntity.dropAndClearContents(level, pos, interferer);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
