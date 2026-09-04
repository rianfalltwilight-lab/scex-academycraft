package com.mohistmc.academy.world.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/**
 * 单个矿物高亮实体 —— 固定在矿石方块位置，渲染一个带颜色着色的纹理立方体后自动消失。
 */
public class OreHighlightEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_HARVEST_LEVEL =
            SynchedEntityData.defineId(OreHighlightEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_RANGE =
            SynchedEntityData.defineId(OreHighlightEntity.class, EntityDataSerializers.FLOAT);

    private static final int LIFETIME = 100;
    private int age = 0;
    private UUID owner;

    public OreHighlightEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_HARVEST_LEVEL, 0);
        builder.define(DATA_RANGE, 15.0f);
    }

    public void setData(int harvestLevel, float range) {
        this.entityData.set(DATA_HARVEST_LEVEL, harvestLevel);
        this.entityData.set(DATA_RANGE, range);
    }

    public void setOwner(UUID owner) { this.owner = owner; }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return owner != null && owner.equals(player.getUUID());
    }

    public int getHarvestLevel() {
        return this.entityData.get(DATA_HARVEST_LEVEL);
    }

    public float getRange() {
        return this.entityData.get(DATA_RANGE);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        age++;
        if (age >= LIFETIME) {
            discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.hasUUID("Owner")) owner = compound.getUUID("Owner");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        if (owner != null) compound.putUUID("Owner", owner);
    }

}
