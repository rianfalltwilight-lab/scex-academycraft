package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.world.AcademyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 改良无线虚能节点 —— 中等性能（与 1.0.7：50,000 IF / 300 IF/t / 12格 / 10连接）。
 * @author Mgazul
 */
public class NodeStandardBlockEntity extends BaseNodeBlockEntity {
    public NodeStandardBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyBlockEntities.NODE_STANDARD.get(), pos, state);
        setMaxEnergy(50000);
        setBandwidth(300);
    }

    @Override
    public double getRange() {
        return 12.0;
    }

    @Override
    public int getCapacity() {
        return 10;
    }
}
