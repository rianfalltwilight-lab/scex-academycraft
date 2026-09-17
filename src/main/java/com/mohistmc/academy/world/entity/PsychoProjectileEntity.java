package com.mohistmc.academy.world.entity;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.effect.EffectHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.joml.Vector3f;

/** Server-authoritative ExtraAcC projectiles. An unsettled return remains an owned, saved item. */
public final class PsychoProjectileEntity extends Projectile implements ItemSupplier {
    public enum Kind { STONE, NEEDLE }
    private static final EntityDataAccessor<ItemStack> ITEM = SynchedEntityData.defineId(PsychoProjectileEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Vector3f> ACCELERATION = SynchedEntityData.defineId(PsychoProjectileEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> BOOST_LEFT = SynchedEntityData.defineId(PsychoProjectileEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> RETURNING = SynchedEntityData.defineId(PsychoProjectileEntity.class, EntityDataSerializers.BOOLEAN);
    private static final ResourceKey<DamageType> NEEDLE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("academy", "psycho_needle"));
    private final Kind kind;
    private float proficiency;
    private boolean armed;
    private boolean settling;
    private int retryTicks;

    public PsychoProjectileEntity(EntityType<? extends PsychoProjectileEntity> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ITEM, ItemStack.EMPTY);
        builder.define(ACCELERATION, new Vector3f());
        builder.define(BOOST_LEFT, 0);
        builder.define(RETURNING, false);
    }

    public void prepare(ServerPlayer owner, ItemStack item, float p) {
        setOwner(owner);
        setPos(owner.getEyePosition());
        entityData.set(ITEM, item.copyWithCount(1));
        proficiency = Float.isFinite(p) ? Math.clamp(p, 0, 1) : 0;
        float acceleration = (0.1F + 0.05F * proficiency) * (etched() ? 1.5F : 1.0F);
        Vec3 direction = owner.getLookAngle().normalize();
        entityData.set(ACCELERATION, direction.scale(acceleration).toVector3f());
        // 1.12.2 World increments age before update; EntityFlying increments it again.
        // The upstream <= 20 condition therefore accelerates on ten real game ticks.
        entityData.set(BOOST_LEFT, 10);
        shoot(direction.x, direction.y, direction.z, acceleration, 0);
    }

    /** Called only after entity admission and ammunition consumption both succeed. */
    public void arm() { armed = true; }
    public Kind kind() { return kind; }
    @Override public ItemStack getItem() { return entityData.get(ITEM); }
    public boolean isReturning() { return entityData.get(RETURNING); }
    private boolean etched() { return kind == Kind.STONE && getItem().is(AcademyItems.ETCHED_COBBLESTONE.get()); }
    private String skill() { return kind == Kind.NEEDLE ? "psycho_needling" : "psycho_throwing"; }
    @Override protected boolean canHitEntity(Entity entity) {
        return entity != getOwner() && super.canHitEntity(entity);
    }

    @Override public void tick() {
        if (isRemoved() || (!level().isClientSide && !armed)) return;
        if (isReturning()) {
            if (!level().isClientSide && --retryTicks <= 0) settleReturn();
            return;
        }
        if (!level().isClientSide && (!(getOwner() instanceof ServerPlayer owner) || !owner.isAlive()
                || owner.level() != level())) {
            beginReturn(); settleReturn(); return;
        }
        super.tick();
        if (isRemoved() || isReturning()) return;
        int boost = entityData.get(BOOST_LEFT);
        if (boost > 0) {
            setDeltaMovement(getDeltaMovement().add(new Vec3(entityData.get(ACCELERATION))));
            entityData.set(BOOST_LEFT, boost - 1);
        }
        Vec3 from = position();
        Vec3 movement = getDeltaMovement();
        Vec3 to = from.add(movement);
        // Never force chunks while tracing or leave an unrecoverable item below the world.
        var swept = getBoundingBox().expandTowards(movement).inflate(1);
        if (!Double.isFinite(movement.lengthSqr()) || movement.lengthSqr() > 64 * 64
                || to.y < level().getMinBuildHeight() || to.y >= level().getMaxBuildHeight()
                || !level().getWorldBorder().isWithinBounds(BlockPos.containing(to))
                || !level().hasChunksAt(BlockPos.containing(swept.minX, swept.minY, swept.minZ),
                        BlockPos.containing(swept.maxX, swept.maxY, swept.maxZ))) {
            if (!level().isClientSide) { beginReturn(); settleReturn(); }
            return;
        }
        if (!level().isClientSide) {
            Entity ownerBefore = getOwner();
            // Re-trace after breaking each fragile block so a second wall cannot be skipped.
            for (int impacts = 0; impacts < 16; impacts++) {
                // Legacy rayTraceBlocks also sees non-solid outlines (for example torches).
                // Portals stay passable; Entity's inside-block lifecycle handles traversal.
                HitResult hit = level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, this) {
                    @Override public VoxelShape getBlockShape(BlockState state, BlockGetter world, BlockPos pos) {
                        return state.is(Blocks.NETHER_PORTAL) ? Shapes.empty() : super.getBlockShape(state, world, pos);
                    }
                });
                Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
                // Vanilla's default 0.3 margin can reach through a thin pane. Test actual boxes.
                EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level(), this, from, end,
                        swept, this::canHitEntity, 0);
                if (entityHit != null) {
                    Vec3 contact = entityHit.getEntity().getBoundingBox().clip(from, end).orElse(from);
                    if (hit.getType() == HitResult.Type.MISS || from.distanceToSqr(contact) < from.distanceToSqr(end))
                        hit = new EntityHitResult(entityHit.getEntity(), contact);
                }
                if (hit.getType() != HitResult.Type.MISS) {
                    boolean canceled = EventHooks.onProjectileImpact(this, hit);
                    if (isRemoved() || isReturning() || !position().equals(from) || getOwner() != ownerBefore
                            || ownerBefore == null || ownerBefore.level() != level() || !getDeltaMovement().equals(movement)) return;
                    if (!canceled && hit instanceof BlockHitResult block && breakFragile(block)) {
                        if (impacts == 15) { beginReturn(); settleReturn(); return; }
                        continue;
                    }
                    if (!canceled) {
                        if (isRemoved() || !position().equals(from)) return;
                        setPos(hit.getLocation());
                        beginReturn(); // Set before any damage/block callback can reenter tick.
                        super.onHit(hit);
                        if (!isRemoved()) {
                            EffectHelper.psychoBurst((ServerLevel) level(), getX(), getY(), getZ(), 8, .2);
                            settleReturn();
                        }
                        return;
                    }
                    if (isRemoved() || isReturning() || !position().equals(from)) return;
                }
                break;
            }
        }
        checkInsideBlocks();
        if (isRemoved() || isReturning()) return;
        updateRotation();
        setPos(to);
        double drag = isInWater() ? .8F : kind == Kind.NEEDLE ? 1 : .99F;
        setDeltaMovement(movement.scale(drag).add(0, kind == Kind.NEEDLE ? 0 : -.03F, 0));
    }

    private boolean breakFragile(BlockHitResult hit) {
        if (!(getOwner() instanceof ServerPlayer owner) || !DynamicSkillRules.destroysBlocks(level(), skill())) return false;
        BlockPos pos = hit.getBlockPos();
        var state = level().getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level(), pos) != 0 || !owner.hasCorrectToolForDrops(state)
                || !owner.mayInteract(level(), pos) || !level().mayInteract(owner, pos)
                || !owner.getAbilities().mayBuild) return false;
        var event = new BlockEvent.BreakEvent(level(), pos, state, owner);
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled() && !isRemoved() && !isReturning() && owner.level() == level()
                && level().getBlockState(pos) == state && level().destroyBlock(pos, true, owner);
    }

    @Override protected void onHitEntity(EntityHitResult hit) {
        if (!(getOwner() instanceof ServerPlayer owner) || owner.level() != level() || !owner.isAlive()) return;
        float damage = kind == Kind.NEEDLE ? 4 + 4 * proficiency : (12 + 4 * proficiency) * (etched() ? 1.25F : 1);
        var type = level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(kind == Kind.NEEDLE ? NEEDLE_DAMAGE : DamageTypes.THROWN);
        AcademyDamageHelper.hurt(owner, hit.getEntity(), new DamageSource(type, this, owner),
                DynamicSkillRules.damage(skill(), damage));
    }

    private void beginReturn() {
        entityData.set(RETURNING, true);
        entityData.set(BOOST_LEFT, 0);
        setDeltaMovement(Vec3.ZERO);
        hasImpulse = true;
    }

    private void settleReturn() {
        if (settling || isRemoved() || level().isClientSide) return;
        settling = true;
        retryTicks = 20;
        try {
            if (getItem().isEmpty()) { discard(); return; }
            BlockPos at = blockPosition();
            var drop = new ItemEntity(level(), at.getX(), at.getY(), at.getZ(), getItem().copy());
            drop.setDefaultPickUpDelay();
            if (level().addFreshEntity(drop)) {
                entityData.set(ITEM, ItemStack.EMPTY);
                discard();
                return;
            }
            // The projectile owns this copy, not a stale reference to a consumed inventory slot.
            if (getOwner() instanceof ServerPlayer owner && owner.isAlive() && owner.level() == level()
                    && !owner.hasInfiniteMaterials()) {
                ItemStack remainder = getItem().copy();
                owner.getInventory().add(remainder);
                entityData.set(ITEM, remainder);
                if (remainder.isEmpty()) discard();
            }
        } finally { settling = false; }
    }

    @Override protected void onBelowWorld() {
        if (!level().isClientSide) {
            setPos(getX(), level().getMinBuildHeight(), getZ());
            beginReturn(); settleReturn();
        }
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!getItem().isEmpty()) tag.put("Item", getItem().save(registryAccess()));
        tag.putFloat("Proficiency", proficiency);
        Vector3f acceleration = entityData.get(ACCELERATION);
        tag.putFloat("AccelerationX", acceleration.x); tag.putFloat("AccelerationY", acceleration.y); tag.putFloat("AccelerationZ", acceleration.z);
        tag.putInt("BoostLeft", entityData.get(BOOST_LEFT));
        tag.putBoolean("Returning", isReturning());
        tag.putBoolean("Armed", armed);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(ITEM, ItemStack.parse(registryAccess(), tag.getCompound("Item")).orElse(ItemStack.EMPTY).copyWithCount(1));
        float p = tag.getFloat("Proficiency");
        proficiency = Float.isFinite(p) ? Math.clamp(p, 0, 1) : 0;
        var acceleration = new Vector3f(tag.getFloat("AccelerationX"), tag.getFloat("AccelerationY"), tag.getFloat("AccelerationZ"));
        entityData.set(ACCELERATION, acceleration.isFinite() && acceleration.lengthSquared() < 1 ? acceleration : new Vector3f());
        entityData.set(BOOST_LEFT, Math.clamp(tag.getInt("BoostLeft"), 0, 10));
        entityData.set(RETURNING, tag.getBoolean("Returning"));
        armed = tag.getBoolean("Armed");
        if (isReturning()) setDeltaMovement(Vec3.ZERO);
    }
}
