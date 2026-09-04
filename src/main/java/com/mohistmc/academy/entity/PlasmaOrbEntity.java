package com.mohistmc.academy.entity;

import com.mohistmc.academy.config.DynamicSkillRules;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-authoritative 1.0.7 plasma body: charging overhead, then moving toward an immutable destination. */
public final class PlasmaOrbEntity extends Entity implements ItemSupplier {
    private static final EntityDataAccessor<Float> DEST_X = SynchedEntityData.defineId(PlasmaOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DEST_Y = SynchedEntityData.defineId(PlasmaOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DEST_Z = SynchedEntityData.defineId(PlasmaOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DAMAGE = SynchedEntityData.defineId(PlasmaOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(PlasmaOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> ARMED = SynchedEntityData.defineId(PlasmaOrbEntity.class, EntityDataSerializers.BOOLEAN);

    private UUID owner;
    private int flightTicks;

    public PlasmaOrbEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public void configureCharging(ServerPlayer player, Vec3 chargePosition) {
        owner = player.getUUID();
        setPos(chargePosition);
        entityData.set(ARMED, false);
        flightTicks = 0;
    }

    public boolean arm(Vec3 destination, float damage, float radius) {
        if (level().isClientSide || !valid(destination, damage, radius) || !isAlive()) return false;
        BlockPos target = BlockPos.containing(destination);
        if (!level().getWorldBorder().isWithinBounds(target) || !level().hasChunkAt(target)) return false;
        entityData.set(DEST_X, (float) destination.x);
        entityData.set(DEST_Y, (float) destination.y);
        entityData.set(DEST_Z, (float) destination.z);
        entityData.set(DAMAGE, damage);
        entityData.set(RADIUS, radius);
        entityData.set(ARMED, true);
        flightTicks = 0;
        hasImpulse = true;
        return true;
    }

    public boolean isArmed() {
        return entityData.get(ARMED);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DEST_X, 0f);
        builder.define(DEST_Y, 0f);
        builder.define(DEST_Z, 0f);
        builder.define(DAMAGE, 80f);
        builder.define(RADIUS, 12f);
        builder.define(ARMED, false);
    }

    @Override public ItemStack getItem() { return new ItemStack(Items.GLOWSTONE_DUST); }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) owner = tag.getUUID("Owner");
        Vec3 destination = new Vec3(tag.getFloat("DestX"), tag.getFloat("DestY"), tag.getFloat("DestZ"));
        float damage = tag.getFloat("Damage");
        float radius = tag.getFloat("Radius");
        if (!valid(destination, damage, radius)) {
            discard();
            return;
        }
        entityData.set(DEST_X, (float) destination.x);
        entityData.set(DEST_Y, (float) destination.y);
        entityData.set(DEST_Z, (float) destination.z);
        entityData.set(DAMAGE, damage);
        entityData.set(RADIUS, radius);
        entityData.set(ARMED, tag.getBoolean("Armed"));
        flightTicks = Math.clamp(tag.getInt("FlightTicks"), 0, 240);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putFloat("DestX", entityData.get(DEST_X));
        tag.putFloat("DestY", entityData.get(DEST_Y));
        tag.putFloat("DestZ", entityData.get(DEST_Z));
        tag.putFloat("Damage", entityData.get(DAMAGE));
        tag.putFloat("Radius", entityData.get(RADIUS));
        tag.putBoolean("Armed", entityData.get(ARMED));
        tag.putInt("FlightTicks", flightTicks);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        ServerLevel serverLevel = (ServerLevel) level();
        ServerPlayer player = owner == null ? null : serverLevel.getServer().getPlayerList().getPlayer(owner);
        if (player == null || player.level() != serverLevel) {
            discard();
            return;
        }
        if (!isArmed()) return;

        Vec3 destination = destination();
        Vec3 old = position();
        Vec3 rawDelta = destination.subtract(old);
        Vec3 next = rawDelta.length() < 1 ? destination : old.add(rawDelta.normalize());
        if (!serverLevel.hasChunkAt(BlockPos.containing(next))) {
            discard();
            return;
        }

        HitResult blockHit = serverLevel.clip(new ClipContext(old, next,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean entityHit = serverLevel.getEntities(this, new AABB(old, next).inflate(.75),
                        entity -> entity != player && entity.isAlive() && entity.isPickable())
                .stream().anyMatch(entity -> entity.getBoundingBox().inflate(.3).clip(old, next).isPresent());
        setPos(next);
        flightTicks++;
        if (blockHit.getType() != HitResult.Type.MISS || entityHit
                || rawDelta.length() < 1.5 || flightTicks >= 240) {
            explodeAtDestination(serverLevel, player);
        }
    }

    private void explodeAtDestination(ServerLevel level, ServerPlayer player) {
        Vec3 destination = destination();
        BlockPos center = BlockPos.containing(destination);
        if (!level.hasChunkAt(center)) {
            discard();
            return;
        }
        // PlasmaCannonContext.explode() always used destination, even when the flight ray hit early.
        setPos(destination);

        float damage = entityData.get(DAMAGE);
        for (Entity entity : level.getEntities(this, getBoundingBox().inflate(10),
                entity -> entity.isAlive() && entity.distanceToSqr(this) <= 100)) {
            AcademyDamageHelper.hurt(player, entity, player.damageSources().explosion(player, player), damage);
            entity.invulnerableTime = -1;
        }

        float radius = entityData.get(RADIUS);
        int wholeRadius = (int) Math.ceil(radius);
        BlockPos min = center.offset(-wholeRadius, -wholeRadius, -wholeRadius);
        BlockPos max = center.offset(wholeRadius, wholeRadius, wholeRadius);
        boolean mayBreak = DynamicSkillRules.destroysBlocks(level, "plasma_cannon")
                && level.hasChunksAt(min, max);
        level.explode(player, destination.x, destination.y, destination.z, radius, false,
                mayBreak ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE);
        discard();
    }

    private Vec3 destination() {
        return new Vec3(entityData.get(DEST_X), entityData.get(DEST_Y), entityData.get(DEST_Z));
    }

    private static boolean valid(Vec3 destination, float damage, float radius) {
        return Double.isFinite(destination.x) && Double.isFinite(destination.y) && Double.isFinite(destination.z)
                && Float.isFinite(damage) && Float.isFinite(radius)
                && Math.abs(destination.x) <= 29_999_984 && Math.abs(destination.z) <= 29_999_984
                && destination.y >= -2048 && destination.y <= 2048
                && damage >= 0 && damage <= 1000 && radius >= 1 && radius <= 32;
    }
}
