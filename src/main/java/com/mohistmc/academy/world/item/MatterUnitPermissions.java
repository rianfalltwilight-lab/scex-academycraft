package com.mohistmc.academy.world.item;

import com.mohistmc.academy.world.AcademyBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Standard NeoForge protection-event boundary shared by both matter units. */
public final class MatterUnitPermissions {
    private MatterUnitPermissions() {}

    public static boolean mayDrain(Level level, Player player, BlockPos pos) {
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player);
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }

    public static boolean tryPlace(Level level, Player player, BlockPos pos, Direction face) {
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos, 3);
        if (!level.setBlock(pos, AcademyBlocks.PHASE_LIQUID.get().defaultBlockState(), 3)) return false;
        if (EventHooks.onBlockPlace(player, snapshot, face)) {
            snapshot.restore();
            return false;
        }
        return true;
    }
}
