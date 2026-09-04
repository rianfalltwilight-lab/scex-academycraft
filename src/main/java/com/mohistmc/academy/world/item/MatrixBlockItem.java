package com.mohistmc.academy.world.item;

import com.mohistmc.academy.world.block.Matrix;
import com.mohistmc.academy.world.AcademyBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Places the Matrix and its internal blocks as one permission-checked transaction. */
public final class MatrixBlockItem extends TransactionalStructureBlockItem {
    public MatrixBlockItem(Matrix block, Properties properties) { super(block, properties); }
    @Override protected List<BlockPos> subPositions(BlockPos main, BlockState state) {
        return Matrix.structurePositions(main, state);
    }
    @Override protected BlockState subState() { return AcademyBlocks.MATRIX_SUB.get().defaultBlockState(); }
    @Override protected boolean committed(net.minecraft.world.level.Level level, BlockPos main,
                                          BlockState state, List<BlockPos> subs) {
        return level.getBlockEntity(main) instanceof com.mohistmc.academy.world.block.entity.MatrixBlockEntity
                && subs.stream().allMatch(pos -> level.getBlockEntity(pos)
                instanceof com.mohistmc.academy.world.block.entity.MatrixSubBlockEntity);
    }
}
