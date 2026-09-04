package com.mohistmc.academy.skill.ability;
import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.skill.*;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.config.LegacyAbilityRules;
import com.mohistmc.academy.skill.ability.electromaster.ElectromasterMetalTargets;
import com.mohistmc.academy.world.*;
import com.mohistmc.academy.world.entity.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.*;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Legacy material Mag Manip, with a durable single-owner transaction. */
public final class MagManipEffect implements ChargingSkillEffect {
    private static final Map<UUID,MagManipBlockEntity> ACTIVE=new ConcurrentHashMap<>();
    private static final TagKey<Block> IMMOVABLE=TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID,"mag_manip_immovable"));
    public String getId(){return "mag_manip";}
    public static float cpCost(float e){return MagManipLegacyMath.cpCost(e);} public static float overloadCost(float e){return MagManipLegacyMath.overloadCost(e);}
    public static float throwSpeed(float e){return MagManipLegacyMath.throwSpeed(e);} public static float impactDamage(float e){return MagManipLegacyMath.impactDamage(e);}
    public static int legacyCooldown(float e){return MagManipLegacyMath.cooldown(e);}
    // 1.0.7 keeps the carried block alive until key-up/abort.  The HUD does
    // not own a charge duration and must never auto-throw the material.
    public int getMinChargeTicks(){return 0;} public int getMaxChargeTicks(){return 1;}
    @Override public int getSessionTimeoutTicks(PlayerAbilityData data){return Integer.MAX_VALUE;}
    public boolean appliesBaseResourceCost(){return false;} public boolean grantsActivationProficiency(){return false;}
    public boolean canStartCharging(ServerPlayer p,PlayerAbilityData d){
        LegacyAbilityRules.SkillTuning tuning=ACConfig.Server.skill(getId());if(!tuning.enabled())return false;
        // Legacy pays only when the player throws.  Starting with no CP/OL is
        // valid: abort/key-up simply restores the captured material.
        return source(p,tuning.destroyBlocks() && ACConfig.Server.mayDestroyBlocks(p.serverLevel()))!=null && MagManipBlockEntity.findOwned(p)==null
                && !MagManipTransactionData.get(p.serverLevel()).reserved(p.getUUID(),p.serverLevel().getGameTime());
    }
    public void onChargingStart(ServerPlayer p,PlayerAbilityData d){
        if(MagManipBlockEntity.findOwned(p)!=null)return;
        LegacyAbilityRules.SkillTuning tuning=ACConfig.Server.skill(getId());if(!tuning.enabled())return;Source src=source(p,tuning.destroyBlocks() && ACConfig.Server.mayDestroyBlocks(p.serverLevel())); if(src==null)return;
        float e=d.getProficiency(getId());
        MagManipBlockEntity carrier=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),p.serverLevel());
        carrier.setPos(p.getX(),p.getEyeY(),p.getZ()); UUID tx=UUID.randomUUID(); long now=p.serverLevel().getGameTime();
        MagManipTransactionData ledger=MagManipTransactionData.get(p.serverLevel());
        if(!ledger.reserve(p.getUUID(),tx,carrier.getUUID(),p.level().dimension().location().toString(),now))return;
        String hash=MagManipTransactionData.sourceHash(src.kind,src.state,"BLOCK".equals(src.kind)?src.payload:null,src.itemFingerprint,src.count);
        if(!ledger.prepare(tx,carrier.getUUID(),carrier.blockPosition(),src.pos,src.state,src.payload,src.beType,src.kind,src.slot,src.count,hash,now)){
            ledger.release(tx,carrier.getUUID());return;
        }
        if(!consumeSource(p,src)){ledger.abortPrepared(p.serverLevel(),tx,carrier.getUUID());return;}
        if(!ledger.markSourceConsumed(tx,carrier.getUUID(),hash,now)||!ledger.markActive(tx,carrier.getUUID(),now)){
            restoreImmediate(p,src);ledger.release(tx,carrier.getUUID());return;
        }
        ItemStack sourceItem=("INVENTORY".equals(src.kind)||"CREATIVE".equals(src.kind))&&src.payload!=null?ItemStack.parse(p.registryAccess(),src.payload).orElse(ItemStack.EMPTY):ItemStack.EMPTY;
        float carrierDamage=DynamicSkillRules.damage(getId(),impactDamage(e));
        carrier.initializeMaterial(p,tx,src.state,src.pos,("BLOCK".equals(src.kind)?src.payload:null),src.beType,sourceItem,carrierDamage);
        ledger.updateRuntime(tx,carrier.getUUID(),carrierDamage,false,0,0,0,now);
        if(!p.serverLevel().addFreshEntity(carrier)){carrier.recoverMaterial();return;}
        ACTIVE.put(p.getUUID(),carrier);
    }
    public boolean onChargingTick(ServerPlayer p,PlayerAbilityData d,int ticks){MagManipBlockEntity x=resolve(p);return x!=null&&x.belongsTo(p);}
    public TickResult getTickResult(ServerPlayer p,PlayerAbilityData d,int ticks){return onChargingTick(p,d,ticks)?TickResult.CONTINUE:TickResult.ABORT_RESOURCE;}
    public boolean tryRelease(ServerPlayer p,PlayerAbilityData d,int ticks){
        MagManipBlockEntity x=resolve(p);ACTIVE.remove(p.getUUID());if(x==null||!x.belongsTo(p))return false;
        LegacyAbilityRules.SkillTuning tuning=ACConfig.Server.skill(getId());if(!tuning.enabled()){x.recoverMaterial();return false;}float e=d.getProficiency(getId()),cp=cpCost(e),ol=overloadCost(e);
        if(p.distanceToSqr(x)>=25||!DynamicSkillRules.canPay(d,getId(),cp,ol)){x.dropFromHold(p);return false;}
        if(!DynamicSkillRules.tryPay(d,getId(),cp,ol)){x.dropFromHold(p);return false;}DynamicSkillRules.addExp(p,d,getId(),.005f);
        x.throwFrom(p,throwTarget(p),throwSpeed(e));
        AcademySounds.playSound(p.serverLevel(),p.getX(),p.getY(),p.getZ(),
                AcademySounds.EM_MAG_MANIP,SoundSource.PLAYERS,1.0f,1f);
        return true;
    }
    public void onChargingRelease(ServerPlayer p,PlayerAbilityData d,int ticks){tryRelease(p,d,ticks);}
    public void onChargingAbort(ServerPlayer p,PlayerAbilityData d){MagManipBlockEntity x=resolve(p);ACTIVE.remove(p.getUUID());if(x!=null)x.dropFromHold(p);}
    public void execute(ServerPlayer p,PlayerAbilityData d){} public int getCooldownTicks(float e){return legacyCooldown(e);}
    private static MagManipBlockEntity resolve(ServerPlayer p){MagManipBlockEntity x=ACTIVE.get(p.getUUID());if(x!=null&&x.isAlive())return x;ACTIVE.remove(p.getUUID());return MagManipBlockEntity.findOwned(p);}

    /** LambdaLib Raytrace.getLookingPos: nearest collider within 20, entity point raised by 60% eye height. */
    private static Vec3 throwTarget(ServerPlayer p){
        Vec3 start=p.getEyePosition(),intended=start.add(p.getLookAngle().scale(20));
        HitResult block=p.pick(20,0,false);Vec3 end=block.getType()==HitResult.Type.MISS?intended:block.getLocation();
        Entity nearest=null;double best=Double.MAX_VALUE;
        for(Entity entity:p.level().getEntities(p,new AABB(start,end).inflate(1),
                entity->entity!=p&&entity.isAlive()&&entity.isPickable())){
            var hit=entity.getBoundingBox().inflate(.3).clip(start,end);if(hit.isEmpty())continue;
            double distance=start.distanceToSqr(hit.get());if(distance<best){best=distance;nearest=entity;}
        }
        return nearest==null?end:nearest.position().add(0,nearest.getEyeHeight()*.6,0);
    }

    private record Source(String kind,int slot,int count,String itemFingerprint,BlockPos pos,BlockState state,CompoundTag payload,ResourceLocation beType){}
    private static Source source(ServerPlayer p,boolean allowWorldDestruction){
        ItemStack held=p.getMainHandItem();
        if(held.getItem() instanceof BlockItem bi&&magnetic(bi.getBlock().defaultBlockState())) {
            net.minecraft.nbt.Tag encoded = held.copyWithCount(1).save(p.registryAccess());
            if (!(encoded instanceof CompoundTag itemTag)) return null;
            return new Source(p.isCreative()?"CREATIVE":"INVENTORY",p.getInventory().selected,held.getCount(),held.getItem()+"|"+held.getComponents(),p.blockPosition(),bi.getBlock().defaultBlockState(),itemTag,null);
        }
        if(!allowWorldDestruction)return null;HitResult raw=p.pick(10,0,false);if(!(raw instanceof BlockHitResult h)||raw.getType()!=HitResult.Type.BLOCK)return null;
        ServerLevel level=p.serverLevel();BlockPos pos=h.getBlockPos();if(!level.hasChunkAt(pos)||!p.mayInteract(level,pos))return null;
        BlockState state=level.getBlockState(pos);if(!magnetic(state))return null;
        BlockEntity be=level.getBlockEntity(pos);CompoundTag payload=null;ResourceLocation type=null;
        if(be!=null){if(!MagManipTransferPolicy.mayMove(be))return null;payload=MagManipTransferPolicy.capture(be,level.registryAccess());if(payload==null)return null;type=MagManipTransferPolicy.typeId(be);}
        BlockEvent.BreakEvent event=new BlockEvent.BreakEvent(level,pos,state,p);NeoForge.EVENT_BUS.post(event);if(event.isCanceled())return null;
        return new Source("BLOCK",-1,0,"",pos,state,payload,type);
    }
    private static boolean consumeSource(ServerPlayer p,Source s){
        if("CREATIVE".equals(s.kind))return true;
        if("INVENTORY".equals(s.kind)){ItemStack live=p.getInventory().getItem(s.slot);if(live.isEmpty()||live.getCount()!=s.count)return false;live.shrink(1);return true;}
        ServerLevel level=p.serverLevel();if(level.getBlockState(s.pos)!=s.state)return false;
        return level.setBlock(s.pos,AcademyBlocks.MAG_MANIP_ESCROW.get().defaultBlockState(),3);
    }
    private static void restoreImmediate(ServerPlayer p,Source s){
        if("INVENTORY".equals(s.kind)){ItemStack stack=s.payload==null?ItemStack.EMPTY:ItemStack.parse(p.registryAccess(),s.payload).orElse(ItemStack.EMPTY);if(!stack.isEmpty()&&!p.getInventory().add(stack))p.drop(stack,false);}
        else if("BLOCK".equals(s.kind)&&p.serverLevel().getBlockState(s.pos).is(AcademyBlocks.MAG_MANIP_ESCROW.get()))p.serverLevel().setBlock(s.pos,s.state,3);
    }
    public static boolean magnetic(BlockState s){return ElectromasterMetalTargets.isAny(s)
            &&!(s.getBlock() instanceof DoorBlock)&&!s.is(IMMOVABLE);}
}
