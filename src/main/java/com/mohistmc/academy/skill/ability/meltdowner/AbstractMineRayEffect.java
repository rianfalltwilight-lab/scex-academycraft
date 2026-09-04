package com.mohistmc.academy.skill.ability.meltdowner;

import com.mohistmc.academy.config.DynamicSkillRules;

import com.mohistmc.academy.world.effect.EffectHelper;
import com.mohistmc.academy.world.AcademySounds;
import com.mohistmc.academy.skill.ChargingSkillEffect;
import com.mohistmc.academy.skill.PlayerAbilityData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

abstract class AbstractMineRayEffect implements ChargingSkillEffect {
    private record Mining(BlockPos pos, float left) {}
    private final Map<UUID, Mining> mining = new HashMap<>();
    private final Map<UUID, Float> overloadFloors = new HashMap<>();
    private final Map<UUID, UUID> visualBeams = new HashMap<>();
    protected abstract float range(); protected abstract int harvestLevel();
    protected abstract float speed(float exp); protected abstract float cpPerTick(float exp);
    protected abstract float startOverload(float exp); protected int fortuneLevel(){return 0;}
    @Override public boolean appliesBaseResourceCost(){return false;}
    @Override public boolean grantsActivationProficiency(){return false;}
    @Override public int getMinChargeTicks(){return 0;} @Override public int getMaxChargeTicks(){return 1;}
    @Override public int getSessionTimeoutTicks(PlayerAbilityData d){return Integer.MAX_VALUE;}
    @Override public TickResult getSessionTimeoutResult(ServerPlayer p,PlayerAbilityData d,int t){return TickResult.ABORT_RESOURCE;}
    @Override public boolean canStartCharging(ServerPlayer p,PlayerAbilityData d){return DynamicSkillRules.canPay(d,getId(),0,startOverload(d.getProficiency(getId())));}
    @Override public void onChargingStart(ServerPlayer p, PlayerAbilityData d){
        mining.remove(p.getUUID());
        overloadFloors.remove(p.getUUID());
        discardVisual(p);
        if (DynamicSkillRules.tryPay(d,getId(),0,startOverload(d.getProficiency(getId())))) {
            overloadFloors.put(p.getUUID(),d.getCurrentOverload());
            UUID beam=EffectHelper.startFollowingMineRay(p,getId());if(beam!=null)visualBeams.put(p.getUUID(),beam);
            var startup = switch (getId()) {
                case "mine_ray_basic" -> AcademySounds.MD_MINE_BASIC_STARTUP;
                case "mine_ray_luck" -> AcademySounds.MD_MINE_LUCK_STARTUP;
                case "mine_ray_expert" -> AcademySounds.MD_MINE_EXPERT_STARTUP;
                default -> throw new IllegalStateException("Unknown legacy mine ray: " + getId());
            };
            p.serverLevel().playSound(null,p.getX(),p.getY(),p.getZ(),startup,SoundSource.PLAYERS,.4f,1f);
        }
    }
    @Override public boolean onChargingTick(ServerPlayer p, PlayerAbilityData d, int ticks){
        Float floor=overloadFloors.get(p.getUUID());if(floor==null)return false;
        if(!d.isDevMode()&&d.getCurrentOverload()<floor)d.setCurrentOverload(floor);
        float exp=d.getProficiency(getId()), cp=cpPerTick(exp);
        // MRContext terminated on failed upkeep but continued the already
        // executing server tick, including target progress and break logic.
        boolean maintained=DynamicSkillRules.tryPay(d,getId(),cp,0);
        ServerLevel level=p.serverLevel(); Vec3 from=p.getEyePosition();
        BlockHitResult hit=level.clip(new ClipContext(from,from.add(p.getLookAngle().scale(range())),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        // Presentation is one persistent owner-following entity, matching the
        // old client context instead of spawning overlapping network entities.
        if(hit.getType()!=HitResult.Type.BLOCK){mining.remove(p.getUUID());return maintained;}
        BlockPos pos=hit.getBlockPos(); BlockState state=level.getBlockState(pos); float hardness=state.getDestroySpeed(level,pos);
        Mining old=mining.get(p.getUUID());
        if(old==null||!old.pos.equals(pos)){
            if(hardness<0||!canHarvest(p,state)||!preflight(level,p,pos,state)){mining.remove(p.getUUID());return maintained;}
            mining.put(p.getUUID(),new Mining(pos.immutable(),hardness));
            return maintained;
        }
        float left=old.left-speed(exp);
        EffectHelper.meltdownBurst(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,
                3+p.getRandom().nextInt(2),.65);
        if(left<=0){boolean broken=DynamicSkillRules.destroysBlocks(level,getId())&&breakBlock(level,p,pos,state);mining.remove(p.getUUID());if(broken&&!d.isDevMode())DynamicSkillRules.addExp(p,d, getId(), getId().endsWith("basic")?.0005f:.0003f);}
        else mining.put(p.getUUID(),new Mining(pos.immutable(),left)); return maintained;
    }
    private ItemStack harvestTool(ServerPlayer p){ItemStack tool=new ItemStack(harvestLevel()<=2?Items.IRON_PICKAXE:Items.NETHERITE_PICKAXE);if(fortuneLevel()>0){var fortune=p.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.FORTUNE);tool.enchant(fortune,fortuneLevel());}return tool;}
    private boolean canHarvest(ServerPlayer p,BlockState state){return !state.requiresCorrectToolForDrops()||harvestTool(p).isCorrectToolForDrops(state);}
    private boolean preflight(ServerLevel l,ServerPlayer p,BlockPos pos,BlockState state){if(!l.hasChunkAt(pos)||!l.getWorldBorder().isWithinBounds(pos)||!p.mayInteract(l,pos))return false;BlockEvent.BreakEvent event=new BlockEvent.BreakEvent(l,pos,state,p);NeoForge.EVENT_BUS.post(event);return !event.isCanceled()&&l.getBlockState(pos)==state;}
    private boolean breakBlock(ServerLevel l,ServerPlayer p,BlockPos pos,BlockState state){
        if(l.getBlockState(pos)!=state)return false;
        ItemStack tool=harvestTool(p);
        var blockEntity=l.getBlockEntity(pos);var drops=net.minecraft.world.level.block.Block.getDrops(state,l,pos,blockEntity,p,tool);
        if(!l.removeBlock(pos,false))return false;
        for(ItemStack drop:drops)net.minecraft.world.level.block.Block.popResource(l,pos,drop);
        state.getBlock().destroy(l,pos,state);
        var sound=state.getSoundType(l,pos,p);l.playSound(null,pos,sound.getBreakSound(),SoundSource.BLOCKS,.5f,1f);
        return true;
    }
    @Override public TickResult getTickResult(ServerPlayer p,PlayerAbilityData d,int t){return onChargingTick(p,d,t)?TickResult.CONTINUE:TickResult.ABORT_RESOURCE;}
    @Override public void onChargingRelease(ServerPlayer p,PlayerAbilityData d,int t){mining.remove(p.getUUID());overloadFloors.remove(p.getUUID());discardVisual(p);}
    @Override public boolean tryRelease(ServerPlayer p,PlayerAbilityData d,int t){onChargingRelease(p,d,t);return true;}
    @Override public void onChargingAbort(ServerPlayer p,PlayerAbilityData d){mining.remove(p.getUUID());overloadFloors.remove(p.getUUID());discardVisual(p);if(!d.isDevMode())d.setCooldown(getId(),getCooldownTicks(d.getProficiency(getId())));}
    private void discardVisual(ServerPlayer p){UUID id=visualBeams.remove(p.getUUID());if(id==null)return;Entity entity=p.serverLevel().getEntity(id);if(entity!=null)entity.discard();}
    @Override public void execute(ServerPlayer p,PlayerAbilityData d){}
}

