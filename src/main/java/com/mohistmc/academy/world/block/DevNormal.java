package com.mohistmc.academy.world.block;

import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.entity.DevNormalBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import com.mohistmc.academy.world.menu.DevNormalMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionResult;

public class DevNormal extends DevMachineBase {
    public static final MapCodec<DevNormal> CODEC = simpleCodec(DevNormal::new);

    public DevNormal(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<DevNormal> codec() {
        return CODEC;
    }

    @Override
    protected Block getSubBlock() {
        return AcademyBlocks.DEV_NORMAL_SUB.get();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DevNormalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return new MenuProvider() {
            @Override public Component getDisplayName() { return Component.translatable("block.academy.dev_normal"); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
                return new DevNormalMenu(id, inv, new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos));
            }
        };
    }
}
