package com.mohistmc.academy.world.item;

import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.WindGenFan;
import com.mohistmc.academy.world.block.WindGenMain;
import com.mohistmc.academy.world.block.entity.WindGenFanBlockEntity;
import com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Atomically places the legacy three-block wind-generator head. */
public final class WindGenMainBlockItem extends TransactionalStructureBlockItem {
    public WindGenMainBlockItem(WindGenMain block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected List<BlockPos> subPositions(BlockPos main, BlockState state) {
        return WindGenMain.proxyPositions(main, state);
    }

    @Override
    protected BlockState subState() {
        return AcademyBlocks.WINDGEN_FAN.get().defaultBlockState();
    }

    @Override
    protected boolean committed(Level level, BlockPos main, BlockState state, List<BlockPos> proxies) {
        if (!(level.getBlockEntity(main) instanceof WindGenMainBlockEntity)) return false;
        BlockState proxyState = AcademyBlocks.WINDGEN_FAN.get().defaultBlockState()
                .setValue(WindGenFan.FACING, state.getValue(WindGenMain.FACING));
        for (BlockPos proxy : proxies) {
            BlockState current = level.getBlockState(proxy);
            if (!current.is(AcademyBlocks.WINDGEN_FAN.get())) return false;
            if (!current.equals(proxyState) && !level.setBlock(proxy, proxyState, 3)) return false;
            if (!(level.getBlockEntity(proxy) instanceof WindGenFanBlockEntity)) {
                return false;
            }
        }
        return true;
    }
}
