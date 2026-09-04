package com.mohistmc.academy.world.entity;

import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Fail-closed policy for moving block-entity state. */
public final class MagManipTransferPolicy {
    private static final Set<ResourceLocation> VANILLA_ALLOWLIST = Set.of(
            ResourceLocation.withDefaultNamespace("hopper"),
            ResourceLocation.withDefaultNamespace("dispenser")
    );
    private MagManipTransferPolicy() {}

    public static ResourceLocation typeId(BlockEntity be) {
        return BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
    }

    public static boolean mayMove(BlockEntity be) {
        if (be instanceof MagManipTransportable transportable) return transportable.academy$canMagManip();
        ResourceLocation id = typeId(be);
        return id != null && VANILLA_ALLOWLIST.contains(id);
    }

    public static CompoundTag capture(BlockEntity be, net.minecraft.core.HolderLookup.Provider registries) {
        if (!mayMove(be)) return null;
        CompoundTag payload = be instanceof MagManipTransportable transportable
                ? transportable.academy$saveMagManipPayload().copy()
                : be.saveWithFullMetadata(registries);
        return sanitize(payload);
    }

    public static CompoundTag sanitize(CompoundTag payload) {
        CompoundTag clean = payload.copy();
        clean.remove("id");
        clean.remove("x"); clean.remove("y"); clean.remove("z");
        return clean;
    }

    public static boolean sameType(BlockEntity target, ResourceLocation expected) {
        return expected != null && expected.equals(typeId(target));
    }
}
