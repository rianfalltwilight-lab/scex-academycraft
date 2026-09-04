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

/** Persistent server-owned Meltdowner charge ball used by Scatter Bomb and Electron Missile. */
public final class MdBallEntity extends Entity {
    /** EntityMdBall(EntityPlayer) in 1.0.7 used this effectively-unbounded life. */
    public static final int HELD_LIFETIME = 2_333_333;
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(MdBallEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(MdBallEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> ACTIVE = SynchedEntityData.defineId(MdBallEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> OFFSET_X = SynchedEntityData.defineId(MdBallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFFSET_Y = SynchedEntityData.defineId(MdBallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> OFFSET_Z = SynchedEntityData.defineId(MdBallEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(MdBallEntity.class, EntityDataSerializers.INT);

    // Intentionally client-local, like EntityMdBall.R in 1.0.7. None of this is
    // authoritative gameplay state and therefore it must not generate packets.
    private int clientTexture;
    private float clientAlphaWiggle = .8f;
    private float clientAlphaAcceleration;

    public MdBallEntity(EntityType<?> type, Level level) { super(type, level); noPhysics = true; noCulling = true; }

    public MdBallEntity bind(UUID owner, int slot, boolean active) {
        return bind(owner, slot, active, HELD_LIFETIME);
    }

    public MdBallEntity bind(UUID owner, int slot, boolean active, int life) {
        entityData.set(OWNER, Optional.of(owner)); entityData.set(SLOT, slot); entityData.set(ACTIVE, active);
        entityData.set(LIFE, Math.max(1, life));
        Entity source = level().getPlayerByUUID(owner);
        float yaw = source == null ? 0 : source.getYRot();
        float theta = (float) (-yaw / 180 * Math.PI
                + (level().random.nextFloat() * .9f - .45f) * Math.PI);
        float range = .8f + level().random.nextFloat() * .5f;
        entityData.set(OFFSET_X, (float) Math.sin(theta) * range);
        entityData.set(OFFSET_Z, (float) Math.cos(theta) * range);
        entityData.set(OFFSET_Y, .4f + level().random.nextFloat() * 1.4f);
        updatePosition(source);
        return this;
    }
    public UUID ownerId() { return entityData.get(OWNER).orElse(null); }
    public int slot() { return entityData.get(SLOT); }
    public boolean activeBall() { return entityData.get(ACTIVE); }
    public int lifetime() { return entityData.get(LIFE); }
    public int clientTexture() { return clientTexture; }
    public float clientAlphaWiggle() { return clientAlphaWiggle; }

    /** The old renderer added this small orbit only to the rendered quad. */
    public float clientRenderOffsetX(float partialTick) {
        float phase = (tickCount + partialTick) / 6.0f;
        return .03f * (float) Math.sin(phase);
    }
    public float clientRenderOffsetY(float partialTick) {
        float phase = (tickCount + partialTick) / 6.0f;
        return .04f * (float) Math.cos(phase * 1.4f + Math.PI / 3.5);
    }
    public float clientRenderOffsetZ(float partialTick) {
        float phase = (tickCount + partialTick) / 6.0f;
        return .03f * (float) Math.cos(phase);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(OWNER, Optional.empty()); b.define(SLOT, 0); b.define(ACTIVE, false);
        b.define(OFFSET_X, 0f); b.define(OFFSET_Y, 1f); b.define(OFFSET_Z, 0f);
        b.define(LIFE, HELD_LIFETIME);
    }
    @Override protected void readAdditionalSaveData(CompoundTag t) {
        // Sessions are memory-only; a reloaded legacy ball cannot prove ownership.
        if (!level().isClientSide) discard();
    }
    @Override public boolean shouldBeSaved() { return false; }
    /** EntityMdBall was a visual/skill context entity, never a ray or melee target. */
    @Override public boolean isPickable() { return false; }
    @Override protected void addAdditionalSaveData(CompoundTag t) {
        UUID owner = ownerId(); if (owner != null) t.putUUID("owner", owner);
        t.putInt("slot", slot()); t.putBoolean("active", activeBall()); t.putInt("life", lifetime());
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) updateClientAnimation();
        if (tickCount >= lifetime()) { if (!level().isClientSide) discard(); return; }
        UUID id = ownerId(); Entity owner = id == null ? null : level().getPlayerByUUID(id);
        if (owner == null || !owner.isAlive()) { if (!level().isClientSide) discard(); return; }
        updatePosition(owner);
    }

    private void updateClientAnimation() {
        // EntityMdBall changed alpha acceleration with probability 3/8 and
        // texture with probability 2/8 on each client update.
        if (random.nextInt(8) < 3) clientAlphaAcceleration = random.nextFloat() * 8f - 4f;
        clientAlphaWiggle += clientAlphaAcceleration * .05f;
        clientAlphaWiggle = Math.clamp(clientAlphaWiggle, 0f, 1f);
        if (random.nextInt(8) < 2) clientTexture = random.nextInt(5);
    }

    private void updatePosition(Entity owner) {
        if (owner == null) return;
        setPos(owner.getX() + entityData.get(OFFSET_X), owner.getY() + entityData.get(OFFSET_Y),
                owner.getZ() + entityData.get(OFFSET_Z));
    }
}
