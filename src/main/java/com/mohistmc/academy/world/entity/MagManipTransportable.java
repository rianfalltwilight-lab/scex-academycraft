package com.mohistmc.academy.world.entity;

import net.minecraft.nbt.CompoundTag;

/**
 * Explicit opt-in for third-party block entities that are safe to move.
 * Implementations must return a self-contained payload and must not retain
 * world coordinates or an identity belonging to another block-entity type.
 */
public interface MagManipTransportable {
    boolean academy$canMagManip();
    CompoundTag academy$saveMagManipPayload();
}
