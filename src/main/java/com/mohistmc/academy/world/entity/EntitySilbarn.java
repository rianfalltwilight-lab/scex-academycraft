package com.mohistmc.academy.world.entity;

import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.AcademyParticles;
import com.mohistmc.academy.world.AcademySounds;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Legacy Silbarn projectile: heavily damped for 50 ticks, then pulled down. */
public final class EntitySilbarn extends Entity implements ItemSupplier {
    public static final int GRAVITY_DELAY_TICKS = 50;
    public static final int IMPACT_LIFETIME_TICKS = 10;
    public static final int MAX_LIFETIME_TICKS = 20 * 15;
    public static final double LINEAR_DRAG = 0.8D;
    public static final double DELAYED_GRAVITY = 0.12D;

    private static final EntityDataAccessor<Boolean> HIT =
            SynchedEntityData.defineId(EntitySilbarn.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(EntitySilbarn.class, EntityDataSerializers.OPTIONAL_UUID);
    private int impactAge;

    public EntitySilbarn(EntityType<? extends EntitySilbarn> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public void launch(LivingEntity owner) {
        Vec3 look = owner.getLookAngle().normalize();
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        setPos(owner.getX() + look.x * 0.45D, owner.getEyeY() - 0.1D + look.y * 0.45D,
                owner.getZ() + look.z * 0.45D);
        setDeltaMovement(look);
        setYRot(owner.getYHeadRot());
        setXRot(owner.getXRot());
        hasImpulse = true;
    }

    public UUID ownerId() { return entityData.get(OWNER).orElse(null); }
    public boolean isHit() { return entityData.get(HIT); }

    /**
     * The 1.0.7 Ray Barrage posted a synthetic entity collision to its selected
     * Silbarn. Keep that special interaction server-owned and one-shot.
     */
    public boolean breakByRayBarrage() {
        if (level().isClientSide || isHit() || !isAlive()) return false;
        impact(new EntityHitResult(this));
        return isHit();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(HIT, false);
        builder.define(OWNER, Optional.empty());
    }

    @Override
    public ItemStack getItem() {
        // The 1.0.7 renderer hid the model after impact while fragments remained for ten ticks.
        return isHit() ? ItemStack.EMPTY : new ItemStack(AcademyItems.SILBARN.get());
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > MAX_LIFETIME_TICKS) {
            discard();
            return;
        }
        if (isHit()) {
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide && ++impactAge >= IMPACT_LIFETIME_TICKS) discard();
            return;
        }

        Vec3 velocity = getDeltaMovement();
        if (!level().isClientSide) {
            if (!level().hasChunkAt(BlockPos.containing(position().add(velocity)))) {
                discard();
                return;
            }
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) {
                setPos(hit.getLocation());
                impact(hit);
                return;
            }
        }
        move(MoverType.SELF, velocity);
        ProjectileUtil.rotateTowardsMovement(this, 0.2F);
        Vec3 next = velocity.scale(LINEAR_DRAG);
        if (tickCount >= GRAVITY_DELAY_TICKS) next = next.add(0, -DELAYED_GRAVITY, 0);
        setDeltaMovement(next);
        hasImpulse = true;
    }

    private boolean canHitEntity(Entity entity) {
        UUID owner = ownerId();
        return entity != this && entity.isAlive() && entity.isPickable() && !entity.isSpectator()
                && (owner == null || !owner.equals(entity.getUUID()));
    }

    private void impact(HitResult result) {
        if (level().isClientSide || isHit()) return;
        entityData.set(HIT, true);
        impactAge = 0;
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        noPhysics = true;
        boolean heavy = result instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof EntitySilbarn;
        AcademySounds.playSound(level(), getX(), getY(), getZ(),
                heavy ? AcademySounds.ENTITY_SILBARN_HEAVY : AcademySounds.ENTITY_SILBARN_LIGHT,
                SoundSource.PLAYERS, 0.5F, 1.0F);
        if (level() instanceof ServerLevel server) {
            // Exact legacy family: 18..27 small, upward-biased, freely rotating
            // shards using entities/silbarn_frag rather than an item-break sprite.
            int count = 18 + random.nextInt(10);
            for (int i = 0; i < count; i++) {
                double speed = 0.08D + random.nextDouble() * 0.10D;
                double speedSq = speed * speed;
                double vx = random.nextDouble() * speed;
                double vy = random.nextDouble() * Math.sqrt(Math.max(0, speedSq - vx * vx));
                double vz = Math.sqrt(Math.max(0, speedSq - vx * vx - vy * vy));
                if (random.nextBoolean()) vx = -vx;
                if (random.nextBoolean()) vy = -vy;
                if (random.nextBoolean()) vz = -vz;
                vy += 0.20D;
                server.sendParticles(AcademyParticles.SILBARN_FRAGMENT.get(), getX(), getY(), getZ(),
                        0, vx, vy, vz, 1.0D);
            }
        }
    }

    @Override public boolean isPickable() { return true; }
    @Override public boolean shouldBeSaved() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // This short-lived combat projectile was non-persistent in 1.0.7.
        if (!level().isClientSide) discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
