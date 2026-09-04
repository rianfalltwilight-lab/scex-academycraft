package com.mohistmc.academy.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Synchronized ten-frame counterpart of 1.0.7's client-only EntityBloodSplash. */
public final class BloodSplashEntity extends Entity {
    public static final int FRAME_COUNT = 10;
    private static final EntityDataAccessor<Float> SCALE = SynchedEntityData.defineId(
            BloodSplashEntity.class, EntityDataSerializers.FLOAT);

    public BloodSplashEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SCALE, 1.0f);
    }

    public BloodSplashEntity configure(float scale) {
        entityData.set(SCALE, Math.clamp(scale, .1f, 4f));
        return this;
    }

    public float scale() {
        return entityData.get(SCALE);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount >= FRAME_COUNT) discard();
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 4096; }
}
