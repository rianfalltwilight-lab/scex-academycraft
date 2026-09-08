package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import com.mohistmc.academy.world.block.entity.PhaseGenBlockEntity;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Real server persistence/topology observer; clients exercise production GUI input. */
@EventBusSubscriber(modid=AcademyCraft.MODID)
public final class NodeConfigServerGate {
    public static final BlockPos NODE=new BlockPos(0,81,0), PHASE=new BlockPos(3,81,0);
    public static final String NAME_FIRST="123", NAME_SECOND="节点-乙20";
    public static final String PASSWORD_FIRST="口令甲20", PASSWORD_SECOND="口令乙20", PASSWORD_FINAL="口令终20";
    private static int stage,age,total,checks,failures;
    private static boolean finished;
    private NodeConfigServerGate() {}
    private static boolean serverRole() { return NodeConfigGateState.enabled() && System.getProperty("academy.nodeConfigRole","").equals("server"); }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!serverRole() || finished) return;
        MinecraftServer server=event.getServer();
        try {
            if (++total>20*360 || ++age>20*80) throw new IllegalStateException("node config gate timeout at "+stage);
            ServerLevel level=server.overworld();
            ServerPlayer a=server.getPlayerList().getPlayerByName("AcademyGateA");
            ServerPlayer b=server.getPlayerList().getPlayerByName("AcademyGateB");
            if (Boolean.getBoolean("academy.nodeConfigRestart")) {
                if (a==null) return;
                level.getChunkAt(NODE); level.getChunkAt(PHASE);
                if(stage==0) {
                    a.setGameMode(GameType.SURVIVAL);
                    a.teleportTo(level,.5,81,2.5,Set.of(),180,0);
                    open(level,a,NODE); next(14); return;
                }
                if(!acked("a") || age<35) return;
                check(NAME_SECOND.equals(node(level).getNodeName()) && PASSWORD_FINAL.equals(node(level).getPassword())
                        && a.getUUID().equals(node(level).getOwnerUUID()) && connected(level),
                        "restart-name-password-owner-and-link", details(level));
                finish(server,true); return;
            }
            if(stage==0) {
                if(a==null || b==null) return;
                if(server.getPlayerList().getPlayers().stream().anyMatch(p -> !Set.of("AcademyGateA","AcademyGateB").contains(p.getGameProfile().getName())))
                    throw new IllegalStateException("unexpected player in isolated gate");
                if(a.getUUID().equals(b.getUUID())) throw new IllegalStateException("acceptance clients share UUID");
                for(int x=-2;x<=5;x++) for(int z=-2;z<=3;z++) {
                    level.setBlock(new BlockPos(x,80,z),Blocks.STONE.defaultBlockState(),3);
                    for(int y=81;y<=85;y++) level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
                }
                level.setBlock(NODE,AcademyBlocks.NODE_BASIC.get().defaultBlockState(),3);
                level.setBlock(PHASE,AcademyBlocks.PHASE_GEN.get().defaultBlockState(),3);
                node(level).setOwnerUUID(a.getUUID());
                // Nearby same-name and default-name nodes exercise target identity in the selector.
                for (BlockPos extra : new BlockPos[]{new BlockPos(4,81,2),new BlockPos(5,81,-1)}) {
                    level.setBlock(extra,AcademyBlocks.NODE_BASIC.get().defaultBlockState(),3);
                    var neighbour=(BaseNodeBlockEntity)level.getBlockEntity(extra);
                    neighbour.setOwnerUUID(a.getUUID());
                    if(extra.getX()==4)neighbour.setNodeName(NAME_FIRST);
                }
                server.getPlayerList().deop(a.getGameProfile());server.getPlayerList().deop(b.getGameProfile());
                a.setGameMode(GameType.SURVIVAL);b.setGameMode(GameType.SURVIVAL);
                a.teleportTo(level,.5,81,2.5,Set.of(),180,0);
                b.teleportTo(level,2.5,81,2.5,Set.of(),180,0);
                open(level,a,NODE);open(level,b,PHASE);
                NodeConfigGateState.append("server-evidence.txt","SETUP distinct real clients; A owns node; B accesses standalone Phase generator; both non-operator");
                next(1);return;
            }
            if(a==null || b==null) throw new IllegalStateException("peer disconnected during first run stage "+stage);
            if(!acked("a") || !acked("b") || age<40) return;
            switch(stage) {
                case 1 -> check(NAME_FIRST.equals(node(level).getNodeName()),"enter-chinese-name",details(level));
                case 2 -> check(NAME_FIRST.equals(node(level).getNodeName()) && PASSWORD_FIRST.equals(node(level).getPassword()),"enter-password-preserves-name",details(level));
                case 3 -> check(!connected(level),"wrong-password-rejected",details(level));
                case 4 -> check(connected(level),"correct-password-visitor-connects",details(level));
                case 5 -> check(NAME_SECOND.equals(node(level).getNodeName()) && connected(level),"rename-preserves-link",details(level));
                case 6 -> check(PASSWORD_SECOND.equals(node(level).getPassword()) && connected(level),"password-change-preserves-established-link",details(level));
                case 7 -> check(!connected(level),"real-gui-disconnect",details(level));
                case 8 -> check(!connected(level),"old-password-rejected-after-change",details(level));
                case 9 -> check(connected(level),"new-password-connects",details(level));
                case 10 -> check(node(level).getPassword().isEmpty() && connected(level),"clear-password-public",details(level));
                case 11 -> {
                    check(connected(level) && node(level).getPassword().isEmpty(),"public-reconnect-without-password",details(level));
                    a.closeContainer();open(level,a,NODE);
                }
                case 12 -> check(NAME_SECOND.equals(node(level).getNodeName()),"server-name-unchanged-by-client-stale-mirror-fixture",details(level));
                case 13 -> {
                    check(NAME_SECOND.equals(node(level).getNodeName()) && PASSWORD_FINAL.equals(node(level).getPassword())
                        && a.getUUID().equals(node(level).getOwnerUUID()) && connected(level),"final-private-config-ready-for-save",details(level));
                    finish(server,false);return;
                }
                default -> throw new IllegalStateException("unknown node config stage "+stage);
            }
            next(stage+1);
        } catch(Throwable failure) {
            failures++;
            NodeConfigGateState.append("server-evidence.txt","FATAL stage="+stage+" "+failure);
            finish(server,Boolean.getBoolean("academy.nodeConfigRestart"));
        }
    }
    private static boolean acked(String role) { return NodeConfigGateState.read(role+"-ack.txt").equals(Integer.toString(stage)); }
    private static BaseNodeBlockEntity node(ServerLevel level) {
        if(!(level.getBlockEntity(NODE) instanceof BaseNodeBlockEntity node)) throw new IllegalStateException("node missing");
        return node;
    }
    private static boolean connected(ServerLevel level) {
        if(!(level.getBlockEntity(PHASE) instanceof PhaseGenBlockEntity phase)) return false;
        var connection=WirelessSystem.getUserConnection(level,phase);
        return connection!=null && connection.getNode() instanceof net.minecraft.world.level.block.entity.BlockEntity be && NODE.equals(be.getBlockPos());
    }
    private static String details(ServerLevel level) {
        return "name="+node(level).getNodeName()+" passwordSet="+!node(level).getPassword().isEmpty()+" connected="+connected(level)+" owner="+node(level).getOwnerUUID();
    }
    private static void check(boolean condition,String id,String detail) {
        checks++;if(!condition)failures++;
        NodeConfigGateState.append("server-checks.txt",(condition?"PASS ":"FAIL ")+id+" stage="+stage+" "+detail);
    }
    private static void open(ServerLevel level,ServerPlayer player,BlockPos pos) {
        level.getBlockState(pos).useWithoutItem(level,player,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
    }
    private static void next(int value) {stage=value;age=0;NodeConfigGateState.write("stage.txt",Integer.toString(stage));}
    private static void finish(MinecraftServer server,boolean restart) {
        if(finished)return;
        boolean clientFailed=!NodeConfigGateState.read(restart?"a-restart-failures.txt":"a-failures.txt").isBlank()
                || !restart && !NodeConfigGateState.read("b-failures.txt").isBlank();
        NodeConfigGateState.write(restart?"restart-result.txt":"server-result.txt","status="+(failures==0&&!clientFailed?"PASS":"FAIL")
                +"\nserverChecks="+checks+"\nserverFailures="+failures+"\nclientFailure="+clientFailed+"\nstage="+stage+"\n");
        finished=true;server.halt(false);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void stopped(ServerStoppedEvent event) {
        if(serverRole()) NodeConfigGateState.write(Boolean.getBoolean("academy.nodeConfigRestart")?"restart-stopped.txt":"server-stopped.txt","saved and ServerStoppedEvent observed");
    }
}