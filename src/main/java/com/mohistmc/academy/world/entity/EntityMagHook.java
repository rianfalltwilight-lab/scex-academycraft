package com.mohistmc.academy.world.entity;

import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.world.AcademyItems;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-owned final 1.12.2 magnetic hook projectile and recoverable block anchor. */
public final class EntityMagHook extends Entity implements ItemSupplier {
    public static final float ENTITY_HIT_DAMAGE = 4.0F;
    public static final int MAX_FLIGHT_TICKS = 20 * 30;
    private static final double GRAVITY = 0.05D;
    private static final double DRAG = 0.99D;

    private static final EntityDataAccessor<Boolean> HIT =
            SynchedEntityData.defineId(EntityMagHook.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> RETURN_ITEM =
            SynchedEntityData.defineId(EntityMagHook.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> OWNER =
            SynchedEntityData.defineId(EntityMagHook.class, EntityDataSerializers.OPTIONAL_UUID);
    private BlockPos anchor = BlockPos.ZERO;
    // Final 1.12.2 commit 8dee31a4 initialized hitSide to DOWN so the first
    // synchronized/render tick can never expose a null/undefined collision face.
    private Direction anchorFace = Direction.DOWN;

    public EntityMagHook(EntityType<? extends EntityMagHook> type, Level level) {
        super(type, level);
    }

    public void launch(Player owner, boolean returnItem) {
        Vec3 look = owner.getLookAngle().normalize();
        entityData.set(OWNER, Optional.of(owner.getUUID()));
        entityData.set(RETURN_ITEM, returnItem);
        setPos(owner.getX() + look.x * 0.45D, owner.getEyeY() - 0.1D + look.y * 0.45D,
                owner.getZ() + look.z * 0.45D);
        setDeltaMovement(look.scale(2.0D));
        setYRot(owner.getYHeadRot());
        setXRot(owner.getXRot());
        hasImpulse = true;
    }

    public boolean isHit() { return entityData.get(HIT); }
    public BlockPos anchor() { return anchor; }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(HIT, false);
        builder.define(RETURN_ITEM, true);
        builder.define(OWNER, Optional.empty());
    }

    @Override public ItemStack getItem() { return new ItemStack(AcademyItems.MAG_HOOK.get()); }

    @Override
    public void tick() {
        super.tick();
        if (isHit()) {
            setDeltaMovement(Vec3.ZERO);
            noPhysics = true;
            setNoGravity(true);
            if (!level().isClientSide && level().hasChunkAt(anchor) && level().getBlockState(anchor).isAir()) {
                recover(null);
            }
            return;
        }
        if (!level().isClientSide && tickCount > MAX_FLIGHT_TICKS) {
            recover(null);
            return;
        }

        Vec3 velocity = getDeltaMovement();
        if (!level().isClientSide) {
            if (!level().hasChunkAt(BlockPos.containing(position().add(velocity)))) {
                recover(null);
                return;
            }
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit instanceof EntityHitResult entityHit) {
                hitEntity(entityHit.getEntity());
                return;
            }
            if (hit instanceof BlockHitResult blockHit && hit.getType() != HitResult.Type.MISS) {
                fixToBlock(blockHit);
                return;
            }
        }
        move(MoverType.SELF, velocity);
        ProjectileUtil.rotateTowardsMovement(this, 0.2F);
        setDeltaMovement(velocity.add(0, -GRAVITY, 0).scale(DRAG));
        hasImpulse = true;
    }

    private boolean canHitEntity(Entity entity) {
        UUID owner = entityData.get(OWNER).orElse(null);
        return entity != this && entity.isAlive() && entity.isPickable() && !entity.isSpectator()
                && (owner == null || !owner.equals(entity.getUUID()));
    }

    private void hitEntity(Entity target) {
        if (target instanceof EntityMagHook other && !other.isHit()) return;
        if (!(target instanceof EntityMagHook)) {
            Entity owner = entityData.get(OWNER).map(level()::getPlayerByUUID).orElse(null);
            // A loaded/orphaned hook must not invent an anonymous damage source,
            // and item PvP follows the same live AcademyCraft server policy as
            // every ability-originated hit.
            if (owner instanceof ServerPlayer player) {
                AcademyDamageHelper.hurt(player, target,
                        player.damageSources().playerAttack(player), ENTITY_HIT_DAMAGE);
            }
        }
        recover(null);
    }

    private void fixToBlock(BlockHitResult hit) {
        anchor = hit.getBlockPos();
        anchorFace = hit.getDirection();
        entityData.set(HIT, true);
        Vec3 normal = Vec3.atLowerCornerOf(anchorFace.getNormal()).scale(0.01D);
        setPos(hit.getLocation().add(normal));
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && isHit() && source.getEntity() instanceof Player player) {
            recover(player);
            return true;
        }
        return false;
    }

    @Override
    public void playerTouch(Player player) {
        if (!level().isClientSide && isHit() && tickCount > 5) recover(player);
    }

    private void recover(Player collector) {
        if (isRemoved()) return;
        if (entityData.get(RETURN_ITEM)) {
            ItemEntity item = new ItemEntity(level(), getX(), getY(), getZ(),
                    new ItemStack(AcademyItems.MAG_HOOK.get()));
            if (level().addFreshEntity(item) && collector != null) item.playerTouch(collector);
        }
        discard();
    }

    @Override public boolean isPickable() { return isHit(); }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(HIT, tag.getBoolean("Hit"));
        entityData.set(RETURN_ITEM, !tag.contains("ReturnItem") || tag.getBoolean("ReturnItem"));
        if (tag.hasUUID("Owner")) entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
        if (tag.contains("Anchor")) anchor = BlockPos.of(tag.getLong("Anchor"));
        anchorFace = Direction.from3DDataValue(Math.clamp(tag.getInt("AnchorFace"), 0, 5));
        if (isHit()) {
            setDeltaMovement(Vec3.ZERO);
            setNoGravity(true);
            noPhysics = true;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("Hit", isHit());
        tag.putBoolean("ReturnItem", entityData.get(RETURN_ITEM));
        entityData.get(OWNER).ifPresent(id -> tag.putUUID("Owner", id));
        tag.putLong("Anchor", anchor.asLong());
        tag.putInt("AnchorFace", anchorFace.get3DDataValue());
    }
}
