package com.mohistmc.academy.world.entity;

import com.mohistmc.academy.world.AcademyItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Recoverable paper glider; Perfect Paper turns impact into a terrain-safe blast. */
public final class ExtraPaperPlaneEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Boolean> REINFORCED =
            SynchedEntityData.defineId(ExtraPaperPlaneEntity.class, EntityDataSerializers.BOOLEAN);

    public ExtraPaperPlaneEntity(EntityType<? extends ExtraPaperPlaneEntity> type, Level level) {
        super(type, level);
    }

    public void launch(LivingEntity owner, boolean reinforced) {
        setOwner(owner);
        setPos(owner.getX(), owner.getEyeY() - 0.1, owner.getZ());
        entityData.set(REINFORCED, reinforced);
        Vec3 look = owner.getLookAngle().normalize();
        setDeltaMovement(look.scale(reinforced ? 0.6 : 0.3));
        hasImpulse = true;
    }

    public boolean isReinforced() { return entityData.get(REINFORCED); }

    @Override protected Item getDefaultItem() { return AcademyItems.PAPER_PLANE.get(); }
    @Override protected double getDefaultGravity() { return 0.01; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(REINFORCED, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (getDeltaMovement().y <= 0) {
            Vec3 horizontal = new Vec3(getDeltaMovement().x, 0, getDeltaMovement().z);
            if (horizontal.lengthSqr() > 1.0e-6) {
                double retained = isReinforced() ? 0.99 : 0.98;
                Vec3 next = getDeltaMovement().scale(retained)
                        .add(horizontal.normalize().scale(0.01));
                setDeltaMovement(next);
            }
        }
        if (!level().isClientSide && tickCount > 20 * 60) impact(position());
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) impact(result.getLocation());
    }

    private void impact(Vec3 at) {
        if (isRemoved()) return;
        if (isReinforced()) {
            level().explode(getOwner(), at.x, at.y, at.z, 2.0F, ExplosionInteraction.NONE);
        } else {
            level().addFreshEntity(new ItemEntity(level(), getX(), getY(), getZ(),
                    new ItemStack(AcademyItems.PAPER_PLANE.get())));
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Reinforced", isReinforced());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(REINFORCED, tag.getBoolean("Reinforced"));
    }
}
