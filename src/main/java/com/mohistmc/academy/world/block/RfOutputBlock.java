package com.mohistmc.academy.world.block;

import com.mohistmc.academy.world.block.entity.EnergyBridgeOutputBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** AcademyCraft IF to NeoForge FE (the modern RF API) bridge. */
public final class RfOutputBlock extends EnergyBridgeBlock {
    public static final MapCodec<RfOutputBlock> CODEC = simpleCodec(RfOutputBlock::new);

    public RfOutputBlock(Properties properties) {
        super(properties, "block.academy.rf_output");
    }

    @Override protected MapCodec<RfOutputBlock> codec() { return CODEC; }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergyBridgeOutputBlockEntity(pos, state);
    }
}
