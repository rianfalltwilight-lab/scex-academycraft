package com.mohistmc.academy.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Namespaced custom-data access for the clean-room ExtraAcC item behaviours. */
public final class ExtraItemData {
    private static final String ACTIVE = "academy_extra_active";
    private static final String TELEPORT_SET = "academy_extra_teleport_set";
    private static final String TELEPORT_DIMENSION = "academy_extra_teleport_dimension";
    private static final String TELEPORT_X = "academy_extra_teleport_x";
    private static final String TELEPORT_Y = "academy_extra_teleport_y";
    private static final String TELEPORT_Z = "academy_extra_teleport_z";
    private static final String HARVEST_POS = "academy_extra_harvest_pos";
    private static final String HARVEST_PROGRESS = "academy_extra_harvest_progress";
    private static final String LAST_USE = "academy_extra_last_use";
    private static final String ENERGY = "academy_extra_energy";

    private ExtraItemData() {}

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void update(ItemStack stack, java.util.function.Consumer<CompoundTag> change) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, change);
    }

    public static boolean isActive(ItemStack stack) {
        return tag(stack).getBoolean(ACTIVE);
    }

    public static void setActive(ItemStack stack, boolean active) {
        update(stack, nbt -> nbt.putBoolean(ACTIVE, active));
    }

    public static void toggleActive(ItemStack stack) {
        setActive(stack, !isActive(stack));
    }

    public static void setTeleport(ItemStack stack, ResourceLocation dimension,
                                   double x, double y, double z) {
        update(stack, nbt -> {
            nbt.putBoolean(TELEPORT_SET, true);
            nbt.putString(TELEPORT_DIMENSION, dimension.toString());
            nbt.putDouble(TELEPORT_X, x);
            nbt.putDouble(TELEPORT_Y, y);
            nbt.putDouble(TELEPORT_Z, z);
        });
    }

    public static void clearTeleport(ItemStack stack) {
        update(stack, nbt -> {
            nbt.remove(TELEPORT_SET);
            nbt.remove(TELEPORT_DIMENSION);
            nbt.remove(TELEPORT_X);
            nbt.remove(TELEPORT_Y);
            nbt.remove(TELEPORT_Z);
        });
    }

    public static TeleportTarget teleport(ItemStack stack) {
        CompoundTag nbt = tag(stack);
        if (!nbt.getBoolean(TELEPORT_SET)) return null;
        ResourceLocation dimension = ResourceLocation.tryParse(nbt.getString(TELEPORT_DIMENSION));
        if (dimension == null) return null;
        double x = nbt.getDouble(TELEPORT_X), y = nbt.getDouble(TELEPORT_Y), z = nbt.getDouble(TELEPORT_Z);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        return new TeleportTarget(dimension, x, y, z);
    }

    public static BlockPos harvestPos(ItemStack stack) {
        CompoundTag nbt = tag(stack);
        return nbt.contains(HARVEST_POS) ? BlockPos.of(nbt.getLong(HARVEST_POS)) : null;
    }

    public static float harvestProgress(ItemStack stack) {
        float progress = tag(stack).getFloat(HARVEST_PROGRESS);
        return Float.isFinite(progress) && progress >= 0 ? progress : 0;
    }

    public static void setHarvest(ItemStack stack, BlockPos pos, float progress) {
        update(stack, nbt -> {
            if (pos == null) {
                nbt.remove(HARVEST_POS);
                nbt.remove(HARVEST_PROGRESS);
            } else {
                nbt.putLong(HARVEST_POS, pos.asLong());
                nbt.putFloat(HARVEST_PROGRESS, Math.max(0, progress));
            }
        });
    }

    public static long lastUse(ItemStack stack) {
        CompoundTag nbt = tag(stack);
        return nbt.contains(LAST_USE) ? nbt.getLong(LAST_USE) : Long.MIN_VALUE;
    }

    public static void setLastUse(ItemStack stack, long gameTime) {
        update(stack, nbt -> nbt.putLong(LAST_USE, gameTime));
    }

    /** Reads the new independent store, falling back to the 0.0.16 durability encoding. */
    public static int energy(ItemStack stack, int capacity) {
        CompoundTag nbt = tag(stack);
        if (nbt.contains(ENERGY)) return Math.clamp(nbt.getInt(ENERGY), 0, capacity);
        if (stack.has(DataComponents.DAMAGE)) {
            return Math.clamp(capacity - stack.getOrDefault(DataComponents.DAMAGE, 0), 0, capacity);
        }
        // The legacy IF NBT manager treated an absent tag as an empty item.
        return 0;
    }

    public static void setEnergy(ItemStack stack, int energy, int capacity) {
        update(stack, nbt -> nbt.putInt(ENERGY, Math.clamp(energy, 0, capacity)));
        // Old builds encoded IF as durability. It must not remain eligible for armor damage.
        stack.remove(DataComponents.DAMAGE);
    }

    public record TeleportTarget(ResourceLocation dimension, double x, double y, double z) {}
}
