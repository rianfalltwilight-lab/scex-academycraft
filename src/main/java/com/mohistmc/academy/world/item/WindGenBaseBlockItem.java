package com.mohistmc.academy.world.item;
import com.mohistmc.academy.world.AcademyBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
public final class WindGenBaseBlockItem extends TransactionalStructureBlockItem {
 public WindGenBaseBlockItem(Block block,Properties p){super(block,p);}
 protected List<BlockPos> subPositions(BlockPos main,BlockState state){return List.of(main.above());}
 protected BlockState subState(){return AcademyBlocks.WIND_GEN_BASE_SUB.get().defaultBlockState();}
}
