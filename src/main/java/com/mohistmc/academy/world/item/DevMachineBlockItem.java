package com.mohistmc.academy.world.item;
import com.mohistmc.academy.world.block.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
public final class DevMachineBlockItem extends TransactionalStructureBlockItem {
 private final DevMachineBase dev;
 public DevMachineBlockItem(DevMachineBase block,Properties p){super(block,p);dev=block;}
 protected List<BlockPos> subPositions(BlockPos main,BlockState state){Direction d=state.getValue(DevMachineBase.FACING).getOpposite();return dev.getRotatedSubBlocks(d).stream().map(s->main.offset(s.dx(),s.dy(),s.dz())).toList();}
 protected BlockState subState(){return dev.getStructureSubBlock().defaultBlockState();}
 protected boolean committed(Level level,BlockPos main,BlockState state,List<BlockPos> subs){return dev.initializeStructure(level,main,subs);}
}
