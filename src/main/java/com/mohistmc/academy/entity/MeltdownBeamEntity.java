package com.mohistmc.academy.entity;

import com.mohistmc.academy.world.AcademyParticles;
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
import net.minecraft.server.level.ServerLevel;

/** Side-neutral synchronized state for the client-only legacy beam renderer. */
public class MeltdownBeamEntity extends Entity {
    public static final int MAIN = 0;
    public static final int LUCK = 1;
    public static final int EXPERT = 2;
    public static final int SMALL = 3;
    public static final int BARRAGE_PRE = 4;
    /** EntityMDRay in 1.0.7 lived for 50 ticks. */
    private static final int DEFAULT_LIFE = 50;

    private static final EntityDataAccessor<Float> SX = SynchedEntityData.defineId(MeltdownBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SY = SynchedEntityData.defineId(MeltdownBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SZ = SynchedEntityData.defineId(MeltdownBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DX = SynchedEntityData.defineId(MeltdownBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DY = SynchedEntityData.defineId(MeltdownBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DZ = SynchedEntityData.defineId(MeltdownBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LEN = SynchedEntityData.defineId(MeltdownBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(MeltdownBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(MeltdownBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> FOLLOW_OWNER = SynchedEntityData.defineId(MeltdownBeamEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    public MeltdownBeamEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        noCulling = true;
    }

    public void setBeam(Vec3 start, Vec3 direction, double length) {
        setBeam(start, direction, length, DEFAULT_LIFE, MAIN);
    }

    public void setBeam(Vec3 start, Vec3 direction, double length, int life) {
        setBeam(start, direction, length, life, MAIN);
    }

    public void setBeam(Vec3 start, Vec3 direction, double length, int life, int variant) {
        entityData.set(SX, (float) start.x);
        entityData.set(SY, (float) start.y);
        entityData.set(SZ, (float) start.z);
        entityData.set(DX, (float) direction.x);
        entityData.set(DY, (float) direction.y);
        entityData.set(DZ, (float) direction.z);
        entityData.set(LEN, (float) Math.max(0, length));
        entityData.set(LIFE, Math.clamp(life, 1, 2_333_333));
        entityData.set(VARIANT, Math.clamp(variant, MAIN, BARRAGE_PRE));
        entityData.set(FOLLOW_OWNER, Optional.empty());
    }

    /** Persistent client presentation used by all three 1.0.7 mining rays. */
    public void setFollowingPlayer(UUID owner, int variant) {
        entityData.set(FOLLOW_OWNER, Optional.of(owner));
        entityData.set(VARIANT, Math.clamp(variant, MAIN, BARRAGE_PRE));
        entityData.set(LEN, 15f);
        entityData.set(LIFE, 233_333);
    }

    public Vec3 getStartPos() {
        Entity owner = followingOwner();
        return owner == null ? new Vec3(entityData.get(SX), entityData.get(SY), entityData.get(SZ))
                : owner.getEyePosition();
    }
    public Vec3 getBeamDirection() {
        Entity owner = followingOwner();
        return owner == null ? new Vec3(entityData.get(DX), entityData.get(DY), entityData.get(DZ))
                : owner.getLookAngle();
    }
    public double getBeamLength() { return entityData.get(LEN); }
    public int getLifetime() { return entityData.get(LIFE); }
    public int getVariant() { return entityData.get(VARIANT); }
    public float getLifeProgress() { return Math.min((float) tickCount / getLifetime(), 1); }
    public boolean followsPlayer() { return entityData.get(FOLLOW_OWNER).isPresent(); }
    private Entity followingOwner() {
        UUID owner = entityData.get(FOLLOW_OWNER).orElse(null);
        return owner == null ? null : level().getPlayerByUUID(owner);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        for (EntityDataAccessor<Float> accessor : new EntityDataAccessor[]{SX, SY, SZ, DX, DY, DZ, LEN}) {
            builder.define(accessor, 0f);
        }
        builder.define(LIFE, DEFAULT_LIFE);
        builder.define(VARIANT, MAIN);
        builder.define(FOLLOW_OWNER, Optional.empty());
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void tick() {
        super.tick();
        Entity owner = followingOwner();
        if (followsPlayer()) {
            if (!level().isClientSide && (owner == null || !owner.isAlive())) { discard(); return; }
            if (owner != null) setPos(owner.getEyePosition());
        }
        if (!level().isClientSide && level() instanceof ServerLevel server) spawnLegacyParticle(server);
        if (tickCount >= getLifetime()) discard();
    }

    private void spawnLegacyParticle(ServerLevel server) {
        if (getVariant() == BARRAGE_PRE) return;
        float chance;
        if (followsPlayer()) chance = getVariant() == SMALL ? .5f : .6f;
        else chance = getVariant() == MAIN ? .8f : 1f;
        if (random.nextFloat() >= chance) return;
        Vec3 start = getStartPos();
        Vec3 point = start.add(getBeamDirection().normalize().scale(random.nextDouble() * Math.min(10, getBeamLength())));
        double spread = getVariant() == SMALL && !followsPlayer() ? .015 : .03;
        var particle = getVariant() == LUCK ? AcademyParticles.MELTDOWN_LUCK.get() : AcademyParticles.MELTDOWN.get();
        server.sendParticles(particle, point.x, point.y, point.z, 0,
                (random.nextDouble() * 2 - 1) * spread,
                (random.nextDouble() * 2 - 1) * spread,
                (random.nextDouble() * 2 - 1) * spread, 1.0);
    }

    @Override public boolean shouldRenderAtSqrDistance(double distance) { return true; }
    @Override public boolean shouldBeSaved() { return false; }
}
