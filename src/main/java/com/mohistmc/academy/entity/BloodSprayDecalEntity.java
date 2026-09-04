package com.mohistmc.academy.entity;

import com.mohistmc.academy.world.AcademyEntities;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Short-lived synchronized presentation decal; deliberately not persisted. */
public final class BloodSprayDecalEntity extends Entity {
    public static final int LIFE_TICKS=40;
    private static final EntityDataAccessor<Integer> FACE=SynchedEntityData.defineId(BloodSprayDecalEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> VARIANT=SynchedEntityData.defineId(BloodSprayDecalEntity.class,EntityDataSerializers.INT);
    public BloodSprayDecalEntity(EntityType<?> type,Level level){super(type,level);noPhysics=true;noCulling=true;}
    protected void defineSynchedData(SynchedEntityData.Builder b){b.define(FACE,Direction.UP.get3DDataValue());b.define(VARIANT,0);}
    protected void readAdditionalSaveData(CompoundTag tag){}
    protected void addAdditionalSaveData(CompoundTag tag){}
    public void tick(){super.tick();if(tickCount>=LIFE_TICKS)discard();}
    public boolean shouldBeSaved(){return false;}
    public boolean shouldRenderAtSqrDistance(double distance){return distance<4096;}
    public Direction face(){return Direction.from3DDataValue(entityData.get(FACE));}
    public int variant(){return Math.floorMod(entityData.get(VARIANT),3);}
    public static void spawn(ServerLevel level,Vec3 position,Direction face){
        BloodSprayDecalEntity e=new BloodSprayDecalEntity(AcademyEntities.BLOOD_SPRAY_DECAL.get(),level);
        e.entityData.set(FACE,face.get3DDataValue());e.entityData.set(VARIANT,level.random.nextInt(3));
        Vec3 n=Vec3.atLowerCornerOf(face.getNormal()).scale(.012);e.setPos(position.add(n));level.addFreshEntity(e);
    }
}
