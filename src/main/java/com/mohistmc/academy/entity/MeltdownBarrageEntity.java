package com.mohistmc.academy.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** One synchronized render batch for the 25..29 rays in the 1.0.7 barrage. */
public final class MeltdownBarrageEntity extends Entity {
    public static final int LIFE_TICKS = 50;
    public static final double RAY_LENGTH = 15.0D;
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(MeltdownBarrageEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(MeltdownBarrageEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(MeltdownBarrageEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COUNT = SynchedEntityData.defineId(MeltdownBarrageEntity.class, EntityDataSerializers.INT);

    public MeltdownBarrageEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    public MeltdownBarrageEntity configure(float yaw, float pitch, int seed, int count) {
        entityData.set(YAW, yaw);
        entityData.set(PITCH, pitch);
        entityData.set(SEED, seed);
        entityData.set(COUNT, Math.clamp(count, 25, 29));
        return this;
    }

    public float barrageYaw() { return entityData.get(YAW); }
    public float barragePitch() { return entityData.get(PITCH); }
    public int barrageSeed() { return entityData.get(SEED); }
    public int rayCount() { return entityData.get(COUNT); }
    public float lengthFactor() { return Math.min(1.0F, tickCount / 2.0F); }
    public float alpha() { return tickCount > LIFE_TICKS - 6 ? Math.max(0.0F, (LIFE_TICKS - tickCount) / 6.0F) : 1.0F; }
    public float width() { return tickCount > LIFE_TICKS - 6 ? Math.max(0.0F, (LIFE_TICKS - tickCount) / 6.0F) : 1.0F; }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(YAW, 0.0F);
        builder.define(PITCH, 0.0F);
        builder.define(SEED, 0);
        builder.define(COUNT, 25);
    }

    @Override public void tick() {
        super.tick();
        if (tickCount >= LIFE_TICKS) discard();
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return true; }
}
