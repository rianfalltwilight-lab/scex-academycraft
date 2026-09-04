package com.mohistmc.academy.world.block;

import com.mohistmc.academy.world.block.entity.EnergyBridgeInputBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** NeoForge FE (the modern RF API) to AcademyCraft IF bridge. */
public final class RfInputBlock extends EnergyBridgeBlock {
    public static final MapCodec<RfInputBlock> CODEC = simpleCodec(RfInputBlock::new);

    public RfInputBlock(Properties properties) {
        super(properties, "block.academy.rf_input");
    }

    @Override protected MapCodec<RfInputBlock> codec() { return CODEC; }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergyBridgeInputBlockEntity(pos, state);
    }
}
