package com.mohistmc.academy.api.event;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Fired before a matter unit harvest commits. Cancellation leaves block and inventory unchanged. */
public final class MatterUnitHarvestEvent extends Event implements ICancellableEvent {
    public final Player player; public final String material; public final BlockPos pos; public final BlockState state;
    public MatterUnitHarvestEvent(Player p,String material,BlockPos pos,BlockState state){this.player=p;this.material=material;this.pos=pos.immutable();this.state=state;}
}
