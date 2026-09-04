package com.mohistmc.academy.world.block.entity;

import com.mohistmc.academy.world.AcademyBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 高级无线虚能节点 —— 最高性能（与 1.0.7：200,000 IF / 900 IF/t / 19格 / 20连接）。
 * @author Mgazul
 */
public class NodeAdvancedBlockEntity extends BaseNodeBlockEntity {
    public NodeAdvancedBlockEntity(BlockPos pos, BlockState state) {
        super(AcademyBlockEntities.NODE_ADVANCED.get(), pos, state);
        setMaxEnergy(200000);
        setBandwidth(900);
    }

    @Override
    public double getRange() {
        return 19.0;
    }

    @Override
    public int getCapacity() {
        return 20;
    }
}
