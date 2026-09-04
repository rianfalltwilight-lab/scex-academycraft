package com.mohistmc.academy.entity;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Owner-following presentation of the flat EntityMdShield from 1.0.7. */
public final class ShieldEffectEntity extends Entity {
    public static final float SIZE = 1.8f;
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(
            ShieldEffectEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(
            ShieldEffectEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(
            ShieldEffectEntity.class, EntityDataSerializers.FLOAT);

    public ShieldEffectEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    public ShieldEffectEntity bind(UUID owner) {
        entityData.set(OWNER, Optional.of(owner));
        updateFromOwner(level().getPlayerByUUID(owner));
        return this;
    }

    public float shieldYaw() { return entityData.get(YAW); }
    public float shieldPitch() { return entityData.get(PITCH); }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
        builder.define(YAW, 0f);
        builder.define(PITCH, 0f);
    }

    @Override public void tick() {
        super.tick();
        UUID id = entityData.get(OWNER).orElse(null);
        Entity owner = id == null ? null : level().getPlayerByUUID(id);
        if (!level().isClientSide && (owner == null || !owner.isAlive())) { discard(); return; }
        if (owner != null) updateFromOwner(owner);
    }

    private void updateFromOwner(Entity owner) {
        if (owner == null) return;
        Vec3 position = owner.getEyePosition().add(owner.getLookAngle()).add(0, -.5, 0);
        setPos(position);
        entityData.set(YAW, owner.getYHeadRot());
        entityData.set(PITCH, owner.getXRot());
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) { if (!level().isClientSide) discard(); }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return true; }
}
