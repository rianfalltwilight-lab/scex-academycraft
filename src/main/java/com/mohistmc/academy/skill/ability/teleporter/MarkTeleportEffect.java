package com.mohistmc.academy.skill.ability.teleporter;

import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.world.effect.EffectHelper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.mohistmc.academy.world.AcademyParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static com.mohistmc.academy.utils.MathUtils.lerpf;

/** 1.0.7 MTContext: the server advances a visible mark by two blocks per held tick, then teleports on key-up. */
public final class MarkTeleportEffect implements ChargingSkillEffect {
    private static final Map<UUID,State> ACTIVE=new ConcurrentHashMap<>();
    private record State(ResourceKey<Level> dimension,Vec3 destination){}
    public static double computeMaxDistance(float exp,float cp,int ticks){return Math.min((ticks+1)*2.0,Math.min(lerpf(25,60,exp),cp/lerpf(12,4,exp)));}
    @Override public String getId(){return "mark_teleport";}
    @Override public boolean canStartCharging(ServerPlayer p,PlayerAbilityData d){return com.mohistmc.academy.config.DynamicSkillRules.enabled(getId());}
    @Override public void onChargingStart(ServerPlayer p,PlayerAbilityData d){ACTIVE.put(p.getUUID(),new State(p.level().dimension(),p.position()));}
    @Override public boolean onChargingTick(ServerPlayer p,PlayerAbilityData d,int ticks){
        State old=ACTIVE.get(p.getUUID());if(old==null||!old.dimension.equals(p.level().dimension()))return false;
        float exp=d.getProficiency(getId());
        double distance=d.isDevMode()?Math.min((ticks+1)*2.0,lerpf(25,60,exp)):computeMaxDistance(exp,d.getCurrentCp(),ticks);
        Vec3 dest=FlashingTargeting.destination(p,p.getLookAngle(),distance);
        if(dest==null)return true;
        ACTIVE.put(p.getUUID(),new State(old.dimension,dest));
        return true;
    }
    @Override public TickResult getTickResult(ServerPlayer p,PlayerAbilityData d,int ticks){return onChargingTick(p,d,ticks)?TickResult.CONTINUE:TickResult.ABORT_RESOURCE;}
    @Override public boolean tryRelease(ServerPlayer p,PlayerAbilityData d,int ticks){
        State state=ACTIVE.remove(p.getUUID());if(state==null||!state.dimension.equals(p.level().dimension()))return false;
        float exp=d.getProficiency(getId());double cpb=lerpf(12,4,exp);
        Vec3 dest=state.destination; // immutable last server-validated marker; do not release at a newer view ray
        BlockPos block=BlockPos.containing(dest);
        if(!p.serverLevel().hasChunkAt(block)||!p.serverLevel().getWorldBorder().isWithinBounds(block))return false;
        double distance=p.position().distanceTo(dest);if(distance<3||!com.mohistmc.academy.config.DynamicSkillRules.payForced(d,getId(),(float)(distance*cpb),lerpf(40,20,exp)))return false;
        ServerLevel level=p.serverLevel();
        TeleportSkillHelper.teleport(p,dest);
        level.playSound(null,p.getX(),p.getY(),p.getZ(),AcademySounds.TP_TP,SoundSource.PLAYERS,.5f,1f);
        com.mohistmc.academy.config.DynamicSkillRules.addExp(p,d,getId(),(float)(.00018*distance));return true;
    }
    @Override public void onChargingRelease(ServerPlayer p,PlayerAbilityData d,int ticks){tryRelease(p,d,ticks);}
    @Override public void onChargingAbort(ServerPlayer p,PlayerAbilityData d){ACTIVE.remove(p.getUUID());}
    @Override public void execute(ServerPlayer p,PlayerAbilityData d){}
    @Override public boolean appliesBaseResourceCost(){return false;}
    @Override public boolean grantsActivationProficiency(){return false;}
    @Override public int getMinChargeTicks(){return 0;}
    @Override public int getMaxChargeTicks(){return 30;}
    @Override public int getMaxChargeTicks(PlayerAbilityData data){return Math.max(1,(int)Math.ceil(lerpf(25,60,data.getProficiency(getId()))/2.0));}
    @Override public int getSessionTimeoutTicks(PlayerAbilityData data){return Integer.MAX_VALUE;}
    @Override public int getCooldownTicks(float exp,int ticks){return(int)lerpf(30,0,exp);}

}

