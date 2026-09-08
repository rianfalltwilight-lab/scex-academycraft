package com.mohistmc.academy.gametest;

import com.mohistmc.academy.capability.EnergyItemHelper;
import com.mohistmc.academy.skill.*;
import com.mohistmc.academy.skill.ability.electromaster.ChargingEffect;
import com.mohistmc.academy.world.*;
import com.mohistmc.academy.world.block.entity.DevNormalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Isolated real capability/raycast fixture. Vanilla capabilities are default-off. */
@GameTestHolder("academy_charging")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy", bus=EventBusSubscriber.Bus.MOD)
public final class ChargingOutputGameTests {
    private static final Map<ItemStack,EnergyStorage> ITEMS = new IdentityHashMap<>();
    private static final Map<BlockPos,EnergyStorage> BLOCKS = new HashMap<>();
    @SubscribeEvent public static void capabilities(RegisterCapabilitiesEvent event) {
        if (!Boolean.getBoolean("academy.chargingOutputGate")) return;
        event.registerItem(Capabilities.EnergyStorage.ITEM,(stack,context)->ITEMS.get(stack),Items.STICK);
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,(level,pos,state,be,side)->BLOCKS.get(pos),Blocks.GOLD_BLOCK);
    }
    @GameTest(template="empty") public static void ifItemAllLevelsAndSteps(GameTestHelper h){run(h,0);}
    @GameTest(template="empty") public static void ifBlockAllLevelsAndSteps(GameTestHelper h){run(h,1);}
    @GameTest(template="empty") public static void feItemAllLevelsAndSteps(GameTestHelper h){run(h,2);}
    @GameTest(template="empty") public static void feBlockAllLevelsAndSteps(GameTestHelper h){run(h,3);}
    private static void run(GameTestHelper h,int path) {
        if(!Boolean.getBoolean("academy.chargingOutputGate")) throw new IllegalStateException("explicit isolated fixture required");
        ServerPlayer player=h.makeMockServerPlayerInLevel();
        PlayerAbilityData data=player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.ELECTROMASTER);data.setAbilityActive(true);data.setDevMode(true);data.learnSkill("charging");
        BlockPos pos=h.absolutePos(new BlockPos(2,2,5));
        player.setPos(pos.getX()+.5,pos.getY()-player.getEyeHeight()+.5,pos.getZ()-3);
        player.setYRot(0);player.setXRot(0);player.yRotO=0;player.xRotO=0;player.xo=player.getX();player.yo=player.getY();player.zo=player.getZ();
        ItemStack item=new ItemStack(path==2?Items.STICK:AcademyItems.ENERGY_UNIT.get());
        EnergyStorage fe=new EnergyStorage(1000000);
        DevNormalBlockEntity nativeBlock=null;
        if(path==0||path==2) player.setItemInHand(InteractionHand.MAIN_HAND,item);
        else {player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);h.getLevel().setBlock(pos,(path==1?AcademyBlocks.DEV_NORMAL.get():Blocks.GOLD_BLOCK).defaultBlockState(),3);}
        if(path==1) nativeBlock=(DevNormalBlockEntity)h.getLevel().getBlockEntity(pos);
        if(path==2) ITEMS.put(item,fe);
        if(path==3) {BLOCKS.put(pos,fe);h.getLevel().invalidateCapabilities(pos);}
        ChargingEffect effect=new ChargingEffect();
        try {
            for(int level=1;level<=5;level++) {
                data.setPlayerLevel(level);
                for(int step=0;step<=20;step++) {
                    float proficiency=step/20f;
                    data.setProficiency("charging",proficiency);
                    if(path==0) EnergyItemHelper.setEnergy(item,0);
                    if(path==1) nativeBlock.setEnergy(0);
                    fe.extractEnergy(Integer.MAX_VALUE,false);
                    effect.onChargingStart(player,data);
                    int old=(int)(15f+20f*proficiency)*(path>=2?4:1);
                    for(int tick=1;tick<=2;tick++) {
                        data.setProficiency("charging",proficiency);
                        if(!effect.onChargingTick(player,data,tick)) throw new AssertionError("tick rejected");
                        int stored=path==0?EnergyItemHelper.getEnergy(item):path==1?nativeBlock.getEnergyStored():fe.getEnergyStored();
                        if(stored!=old*5*tick/2) throw new AssertionError("path="+path+" level="+level+" step="+step+" tick="+tick+" stored="+stored+" expected="+(old*5*tick/2));
                    }
                    effect.onChargingRelease(player,data,2);
                    if(effect.onChargingTick(player,data,3)) throw new AssertionError("released session generated energy");
                }
            }
            System.out.println("CHARGING_OUTPUT_PASS path="+path+" levels=1..5 steps=0..20 ticks=210");
        } finally {effect.onChargingAbort(player,data);ITEMS.remove(item);BLOCKS.remove(pos);}
        h.succeed();
    }
    @GameTest(template="empty") public static void fullTargetPaymentFailureAndSessionReset(GameTestHelper h) {
        ServerPlayer player=h.makeMockServerPlayerInLevel();
        PlayerAbilityData data=player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.ELECTROMASTER);data.setPlayerLevel(5);data.learnSkill("charging");data.setAbilityActive(true);data.setDevMode(true);
        ItemStack item=new ItemStack(AcademyItems.ENERGY_UNIT.get());player.setItemInHand(InteractionHand.MAIN_HAND,item);
        ChargingEffect effect=new ChargingEffect();
        try {
            EnergyItemHelper.setEnergy(item,0);data.setProficiency("charging",0);effect.onChargingStart(player,data);
            effect.onChargingTick(player,data,1);if(EnergyItemHelper.getEnergy(item)!=37) throw new AssertionError("first tick");
            effect.onChargingAbort(player,data);data.setProficiency("charging",0);effect.onChargingStart(player,data);
            effect.onChargingTick(player,data,1);if(EnergyItemHelper.getEnergy(item)!=74) throw new AssertionError("abort leaked carry");
            EnergyItemHelper.setEnergy(item,Integer.MAX_VALUE);int full=EnergyItemHelper.getEnergy(item);
            effect.onChargingTick(player,data,2);if(EnergyItemHelper.getEnergy(item)!=full) throw new AssertionError("full target overflow");
            EnergyItemHelper.setEnergy(item,full-1);effect.onChargingTick(player,data,3);
            if(EnergyItemHelper.getEnergy(item)!=full) throw new AssertionError("capacity boundary");
            data.setDevMode(false);data.setCurrentCp(0);EnergyItemHelper.setEnergy(item,0);
            if(effect.onChargingTick(player,data,4)||EnergyItemHelper.getEnergy(item)!=0) throw new AssertionError("unpaid output");
        } finally {effect.onChargingAbort(player,data);}
        h.succeed();
    }
}
