package com.mohistmc.academy.entity;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Synchronized owner-following lifetime for the four 1.0.7 StormWing vortices. */
public final class StormWingVisualEntity extends Entity {
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(
            StormWingVisualEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> CHARGE_TICKS = SynchedEntityData.defineId(
            StormWingVisualEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ACTIVE = SynchedEntityData.defineId(
            StormWingVisualEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> TERMINATING = SynchedEntityData.defineId(
            StormWingVisualEntity.class, EntityDataSerializers.BOOLEAN);
    private int terminateTicks;

    public StormWingVisualEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    public StormWingVisualEntity configure(Player owner, int chargeTicks) {
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        entityData.set(CHARGE_TICKS, Math.max(1, chargeTicks));
        follow(owner);
        return this;
    }

    public void activate() { entityData.set(ACTIVE, true); }
    public boolean isTerminating() { return entityData.get(TERMINATING); }
    public void terminate() {
        if (!entityData.get(TERMINATING)) {
            entityData.set(TERMINATING, true);
            terminateTicks = 0;
        }
    }

    public float alpha(float partialTick) {
        if (entityData.get(TERMINATING))
            return .7f * Math.max(0, 1 - (terminateTicks + partialTick) / 15f);
        if (entityData.get(ACTIVE)) return .7f;
        return .7f * Math.min(1, (tickCount + partialTick) / entityData.get(CHARGE_TICKS).floatValue());
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, Optional.empty());
        builder.define(CHARGE_TICKS, 70);
        builder.define(ACTIVE, false);
        builder.define(TERMINATING, false);
    }

    @Override public void tick() {
        super.tick();
        UUID ownerId = entityData.get(OWNER).orElse(null);
        Player owner = ownerId == null ? null : level().getPlayerByUUID(ownerId);
        if (owner == null || owner.isRemoved()) {
            if (!level().isClientSide) discard();
            return;
        }
        follow(owner);
        if (entityData.get(TERMINATING) && ++terminateTicks > 15 && !level().isClientSide) discard();
    }

    private void follow(Player owner) {
        setPos(owner.getX(), owner.getY() + 1.6, owner.getZ());
        setYRot(owner.getYRot());
        setXRot(owner.getXRot());
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 16384; }
}
