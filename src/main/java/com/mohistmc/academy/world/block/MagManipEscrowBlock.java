package com.mohistmc.academy.world.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

/**
 * Non-item, non-interactive source receipt for a Mag Manip transaction.
 * The payload and transaction identity remain in SavedData; this block is only
 * the independently persisted world-side half of the consume proof.
 */
public final class MagManipEscrowBlock extends Block {
    public MagManipEscrowBlock() {
        super(Properties.of().strength(-1.0F, 3_600_000.0F).noLootTable().noOcclusion());
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override public PushReaction getPistonPushReaction(BlockState state) { return PushReaction.BLOCK; }
}
