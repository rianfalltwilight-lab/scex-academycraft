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
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Two genuine network peers; inert unless the server explicitly enables the local fixture. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class ConcurrentMenuServerGate {
    public static final BlockPos NODE = new BlockPos(0, 81, 0);
    public static final BlockPos PHASE = new BlockPos(3, 81, 0);
    private static int stage, age, ticks;
    private static boolean finished;
    private ConcurrentMenuServerGate() {}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!ConcurrentGateState.enabled() || finished || !"server".equals(System.getProperty("academy.concurrentRole"))) return;
        MinecraftServer server = event.getServer();
        try {
            if (++ticks > 20 * 300) throw new IllegalStateException("gate overall timeout");
            if (++age > 20 * 150) throw new IllegalStateException("stage timeout " + stage);
            ServerLevel level = server.overworld();
            if (Boolean.getBoolean("academy.concurrentRestart")) {
                level.getChunkAt(NODE); level.getChunkAt(PHASE);
                BaseNodeBlockEntity node = node(level);
                if (!node.getNodeName().equals("OrderHigh") || !node.getPassword().equals("safe-pass"))
                    throw new IllegalStateException("restart lost node name/password");
                if (WirelessSystem.getUserConnection(level, (PhaseGenBlockEntity) level.getBlockEntity(PHASE)) == null)
                    throw new IllegalStateException("restart lost standalone PhaseGen connection");
                ConcurrentGateState.write("restart-result.txt", "status=PASS\nnode-name=OrderHigh\npassword=preserved\nphase-link=preserved\n");
                finished = true; server.halt(false); return;
            }
            ServerPlayer a = server.getPlayerList().getPlayerByName("AcademyGateA");
            ServerPlayer b = server.getPlayerList().getPlayerByName("AcademyGateB");
            if (stage == 0) {
                if (a == null || b == null) return;
                if (server.getPlayerList().getPlayers().stream().anyMatch(p -> !Set.of("AcademyGateA", "AcademyGateB").contains(p.getGameProfile().getName())))
                    throw new IllegalStateException("unexpected player in isolated fixture");
                if (a.getUUID().equals(b.getUUID())) throw new IllegalStateException("clients share identity");
                for (int x=-2; x<=5; x++) for (int z=-2; z<=3; z++) {
                    level.setBlock(new BlockPos(x,80,z), Blocks.STONE.defaultBlockState(),3);
                    for(int y=81;y<=84;y++) level.setBlock(new BlockPos(x,y,z), Blocks.AIR.defaultBlockState(),3);
                }
                level.setBlock(NODE, AcademyBlocks.NODE_BASIC.get().defaultBlockState(),3);
                level.setBlock(PHASE, AcademyBlocks.PHASE_GEN.get().defaultBlockState(),3);
                node(level).setOwnerUUID(a.getUUID()); node(level).setNodeName("Initial");
                a.setGameMode(GameType.SURVIVAL); b.setGameMode(GameType.SURVIVAL);
                server.getPlayerList().op(b.getGameProfile());
                a.teleportTo(level, .5,81,2.5,Set.of(),180,0);
                b.teleportTo(level,1.5,81,2.5,Set.of(),180,0);
                open(level,a,NODE); open(level,b,NODE);
                ConcurrentGateState.evidence("two distinct socket clients opened the same production node menu; B temporarily administrator");
                next(1); return;
            }
            if (a == null || b == null) {
                throw new IllegalStateException("peer disconnected during stage " + stage);
            }
            if (!ConcurrentGateState.read("a-ack.txt").equals(Integer.toString(stage))
                    || !ConcurrentGateState.read("b-ack.txt").equals(Integer.toString(stage)) || age < 30) return;
            switch(stage) {
                case 1 -> { require(node(level).getNodeName().equals("Concurrent-A"), "A GUI rename lost"); next(2); }
                case 2 -> {
                    require(node(level).getNodeName().equals("Concurrent-A") && node(level).getPassword().equals("safe-pass"),"B password edit overwrote A name");
                    ConcurrentGateState.evidence("concurrent GUI inputs: A name and B password both preserved");
                    a.closeContainer(); b.closeContainer(); node(level).setNodeName("Closed"); next(3);
                }
                case 3 -> { require(node(level).getNodeName().equals("Closed"),"closed menu accepted a delayed packet");
                    open(level,a,NODE); open(level,b,NODE); node(level).setNodeName("Reopened"); next(4); }
                case 4 -> { require(node(level).getNodeName().equals("Reopened"),"reopened menu accepted old nonce"); next(5); }
                case 5 -> { require(node(level).getNodeName().equals("OrderHigh"),"duplicate or reordered packet applied");
                    ConcurrentGateState.evidence("late closed/reopened packets and lower/repeated sequence numbers rejected over actual C2S");
                    server.getPlayerList().deop(b.getGameProfile()); next(6); }
                case 6 -> { require(node(level).getNodeName().equals("OrderHigh"),"non-owner mutation accepted");
                    ConcurrentGateState.evidence("B lost administrator access; stale editable menu could not change A-owned node");
                    open(level,a,PHASE); open(level,b,PHASE); next(7); }
                case 7 -> { require(WirelessSystem.getUserConnection(level,(PhaseGenBlockEntity)level.getBlockEntity(PHASE)) == null,"wrong password connected PhaseGen"); next(8); }
                case 8 -> { require(WirelessSystem.getUserConnection(level,(PhaseGenBlockEntity)level.getBlockEntity(PHASE)) != null,"correct password did not connect PhaseGen");
                    ConcurrentGateState.evidence("wrong password rejected then correct password bound PhaseGen directly without matrix");
                    a.closeContainer(); b.closeContainer(); a.setGameMode(GameType.CREATIVE); next(9); }
                case 9 -> finish(server);
                default -> throw new IllegalStateException("invalid stage");
            }
        } catch(Throwable failure) {
            ConcurrentGateState.write("server-result.txt","status=FAIL\nstage="+stage+"\nreason="+failure+"\n");
            finished=true; server.halt(false);
        }
    }
    private static void finish(MinecraftServer server) {
        ConcurrentGateState.evidence("both clients opened rendered inventory/creative screens with JEI and Jade installed");
        ConcurrentGateState.write("server-result.txt","status=PASS\nstages=9\n");
        finished=true; server.halt(false);
    }
    private static BaseNodeBlockEntity node(ServerLevel level) {
        if (!(level.getBlockEntity(NODE) instanceof BaseNodeBlockEntity node)) throw new IllegalStateException("node missing");
        return node;
    }
    private static void require(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); }
    private static void open(ServerLevel level,ServerPlayer player,BlockPos pos) {
        level.getBlockState(pos).useWithoutItem(level,player,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
    }
    private static void next(int value) { stage=value; age=0; ConcurrentGateState.write("stage.txt",Integer.toString(stage)); }
}
