package com.mohistmc.academy.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Short-lived, non-persistent synchronized geometry used by legacy ability presentations. */
public class LegacyFieldEffectEntity extends Entity {
    public static final int ARC=0, SHOCKWAVE=1, WAVE=2, WIND=3, PSYCHO=4, TETHER=5, JET=6, INTENSIFY=7,
            TEXTURED_WAVE=8;
    private static final int LIFE_TICKS=12;
    private static final EntityDataAccessor<Integer> KIND=SynchedEntityData.defineId(LegacyFieldEffectEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR=SynchedEntityData.defineId(LegacyFieldEffectEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE=SynchedEntityData.defineId(LegacyFieldEffectEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DX=SynchedEntityData.defineId(LegacyFieldEffectEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DY=SynchedEntityData.defineId(LegacyFieldEffectEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DZ=SynchedEntityData.defineId(LegacyFieldEffectEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFETIME=SynchedEntityData.defineId(LegacyFieldEffectEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COUNT=SynchedEntityData.defineId(LegacyFieldEffectEntity.class,EntityDataSerializers.INT);

    public LegacyFieldEffectEntity(EntityType<?> type, Level level){super(type,level);noPhysics=true;noCulling=true;}
    public LegacyFieldEffectEntity configure(int kind,float size,int color,Vec3 direction){
        return configure(kind,size,color,direction,kind==INTENSIFY?15:LIFE_TICKS);
    }
    public LegacyFieldEffectEntity configure(int kind,float size,int color,Vec3 direction,int lifetime){
        entityData.set(KIND,kind);entityData.set(SIZE,Math.max(.05f,Math.min(size,32f)));entityData.set(COLOR,color);
        Vec3 d=direction.lengthSqr()<1e-6?new Vec3(0,0,1):direction.normalize();
        entityData.set(DX,(float)d.x);entityData.set(DY,(float)d.y);entityData.set(DZ,(float)d.z);
        entityData.set(LIFETIME,Math.clamp(lifetime,1,100));return this;
    }
    public LegacyFieldEffectEntity configureWave(float size,Vec3 direction,int rings){
        configure(TEXTURED_WAVE,size,0xffffff,direction,15);
        entityData.set(COUNT,Math.clamp(rings,1,6));
        return this;
    }
    public int kind(){return entityData.get(KIND);} public int color(){return entityData.get(COLOR);}
    public float size(){return entityData.get(SIZE);} public Vec3 direction(){return new Vec3(entityData.get(DX),entityData.get(DY),entityData.get(DZ));}
    public int count(){return entityData.get(COUNT);}
    private int lifetime(){return entityData.get(LIFETIME);}
    public float alpha(float partial){float age=(tickCount+partial)/lifetime();return Math.max(0,1-age);}
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){b.define(KIND,ARC);b.define(COLOR,0xff99ddff);b.define(SIZE,1f);b.define(DX,0f);b.define(DY,0f);b.define(DZ,1f);b.define(LIFETIME,LIFE_TICKS);b.define(COUNT,1);}
    @Override protected void readAdditionalSaveData(CompoundTag tag){} @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public void tick(){super.tick();if(tickCount>=lifetime())discard();}
    @Override public boolean shouldBeSaved(){return false;} @Override public boolean shouldRenderAtSqrDistance(double d){return d<16384;}
}
