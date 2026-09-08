package com.mohistmc.academy.client.gate;

import com.mohistmc.academy.client.block.gui.MatrixGui;
import com.mohistmc.academy.client.block.gui.NodeBasicGui;
import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.energy.impl.*;
import com.mohistmc.academy.world.*;
import com.mohistmc.academy.world.block.*;
import com.mohistmc.academy.world.block.entity.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.*;
import java.util.List;

/** Default-off regression: real menus, input handlers and negotiated payloads, in a new isolated world. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class MatrixSsidClientGate {
    private static final BlockPos MATRIX=new BlockPos(0,80,0), NODE=MATRIX.east(4);
    private static int stage,age,total,checks;
    private static boolean finished,working;
    private static volatile boolean ready;
    private static volatile Throwable serverFailure;
    private static volatile String observedName,observedPassword;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.matrixSsidGate")||finished) return;
        var mc=Minecraft.getInstance();
        try {
            if (++total>4000) throw new IllegalStateException("matrix SSID gate timed out at stage "+stage);
            if (serverFailure!=null) throw new IllegalStateException("server fixture failed",serverFailure);
            if (stage==0) {
                if (!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null) return;
                next();
                mc.createWorldOpenFlows().createFreshLevel("MatrixSsidGate",
                        new LevelSettings("Matrix SSID isolated",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(20260908L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());
                return;
            }
            if (mc.level==null||mc.player==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null) return;
            age++;
            if (stage==1) {
                if (!working) {
                    working=true; server(mc,()->{
                        var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                        level.setBlock(MATRIX,AcademyBlocks.MATRIX.get().defaultBlockState(),3);
                        ((Matrix)AcademyBlocks.MATRIX.get()).createStructure(level,MATRIX,level.getBlockState(MATRIX));
                        level.setBlock(NODE,AcademyBlocks.NODE_BASIC.get().defaultBlockState(),3);
                        var matrix=(MatrixBlockEntity)level.getBlockEntity(MATRIX);
                        matrix.setOwnerUUID(player.getUUID());
                        ((BaseNodeBlockEntity)level.getBlockEntity(NODE)).setOwnerUUID(player.getUUID());
                        matrix.getItems().set(MatrixBlockEntity.CORE_SLOT,new ItemStack(AcademyItems.MAT_CORE_0.get()));
                        for(int i=0;i<3;i++)matrix.getItems().set(i,new ItemStack(AcademyItems.CONSTRAINT_PLATE.get()));
                        matrix.setSSID("Unnamed");matrix.applyCoreLevel(0);
                        require(WirelessSystem.createNetwork(level,matrix,"Unnamed",""),"fixture network failed");
                        matrix.setInitialized(true);matrix.setChanged();
                        ready=true;
                    });
                }
                if (ready) { next();open(mc,MATRIX); }
            } else if (stage==2||stage==7) {
                if (!(mc.screen instanceof MatrixGui gui)||!gui.getMenu().actionSessionReady()||age<20) return;
                if(stage==7)require(gui.getMenu().getInitialSsid().equals("123"),"reopened matrix has stale SSID");
                edit(gui,true,stage==2?"123":"中文矩阵");
                if(stage==2)edit(gui,false,"secret");
                gui.keyPressed(257,0,0);next();
            } else if (stage==3||stage==8) {
                if(age<40)return;
                if(!working){working=true;ready=false;server(mc,()->{
                    var m=(MatrixBlockEntity)mc.getSingleplayerServer().overworld().getBlockEntity(MATRIX);
                    var n=WiWorldData.get(mc.getSingleplayerServer().overworld()).getNetwork(m);
                    require(n!=null&&n.getSSID().equals(m.getSSID()),"network and BE names differ");
                    observedName=n.getSSID();observedPassword=n.getPassword();ready=true;
                });return;}
                if(!ready)return;
                require(observedName.equals(stage==3?"123":"中文矩阵"),"SSID was not submitted: "+observedName);
                require(observedPassword.equals("secret"),"password was lost");checks++;
                capture(mc,stage==3?"01-combined-save.png":"03-name-only-save.png");
                if(stage==3){next();open(mc,NODE);}else{next();open(mc,MATRIX);}
            } else if(stage==4) {
                if(!(mc.screen instanceof NodeBasicGui gui)||age<20)return;
                var left=number(gui,"leftPos");var top=number(gui,"topPos");
                gui.mouseClicked(left-11,top+29,0);next();
            } else if(stage==5) {
                if(age<40)return;
                require(mc.screen instanceof NodeBasicGui,"node menu closed");
                var rows=(List<?>)field(mc.screen,"serverMatrixNetworks");
                boolean found=false;
                for(var row:rows){var m=row.getClass().getDeclaredMethod("name");m.setAccessible(true);if(m.invoke(row).equals("123"))found=true;}
                require(found,"node network list still missing renamed SSID");checks++;
                capture(mc,"02-node-network-list.png");next();
            } else if(stage==6&&age>20) {next();open(mc,MATRIX);}
            else if(stage==9) {
                if(!(mc.screen instanceof MatrixGui gui)||age<30)return;
                require(gui.getMenu().getInitialSsid().equals("中文矩阵"),"second reopen lost SSID");checks++;
                Files.writeString(mc.gameDirectory.toPath().resolve("matrix-ssid-result.json"),"{\"passed\":true,\"checks\":"+checks+",\"screenshots\":3}");
                finished=true;mc.stop();
            }
        }catch(Throwable failure){
            finished=true;
            try{Files.writeString(mc.gameDirectory.toPath().resolve("matrix-ssid-failure.txt"),failure.toString());}catch(Exception ignored){}
            mc.stop();
        }
    }
    private static void next(){stage++;age=0;working=false;}
    private static void server(Minecraft mc,Runnable work){mc.getSingleplayerServer().execute(()->{try{work.run();}catch(Throwable failure){serverFailure=failure;}});}
    private static void open(Minecraft mc,BlockPos pos){server(mc,()->{
        var level=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        player.closeContainer();player.setNoGravity(true);player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(level,pos.getX()+.5,pos.getY()+.5,pos.getZ()+2.5,180,0);
        var state=level.getBlockState(pos);var hit=new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false);
        if(state.getBlock() instanceof Matrix m)m.useWithoutItem(state,level,pos,player,hit);
        else ((NodeBasic)state.getBlock()).useWithoutItem(state,level,pos,player,hit);
    });}
    private static void edit(MatrixGui gui,boolean ssid,String value)throws Exception{
        int x=number(gui,"leftPos")+190,y=number(gui,"topPos")+(ssid?28:42);
        require(gui.mouseClicked(x,y,0),"field click failed");
        if(ssid)for(int i=0;i<32;i++)gui.keyPressed(259,0,0);
        for(char c:value.toCharArray())gui.charTyped(c,0);
    }
    private static Object field(Object object,String name)throws Exception{
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(NoSuchFieldException absent){}
        throw new NoSuchFieldException(name);
    }
    private static int number(Object object,String name)throws Exception{return (int)field(object,name);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void capture(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{});}
}
