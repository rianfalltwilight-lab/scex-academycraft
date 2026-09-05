package com.mohistmc.academy.gametest.recheck;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.*;
import com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real two-socket lifecycle audit; failed behavioral assertions are recorded before continuing cleanup. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class RecheckSessionServerGate {
    public static final BlockPos NODE = new BlockPos(0, 81, 0);
    private static int stage, age, ticks, failures, checks;
    private static boolean finished;
    private static ServerPlayer originalA;
    private static UUID identityA;
    private static long epochA, epochB;
    private RecheckSessionServerGate() {}

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!RecheckSessionState.enabled() || !RecheckSessionState.role().equals("server") || finished) return;
        var server = event.getServer();
        try {
            if (++ticks > 20 * 480 || ++age > 20 * 120) throw new IllegalStateException("timeout at stage " + stage);
            var a = server.getPlayerList().getPlayerByName("AcademyGateA");
            var b = server.getPlayerList().getPlayerByName("AcademyGateB");
            if (Boolean.getBoolean("academy.recheckSessionRestart")) {
                if (a == null || !RecheckSessionState.read("a-restart-ready.txt").equals("ready")) return;
                RecheckPersistenceProbe.verifyRestart(a);
                check("restart-real-playerdata", true, "same UUID=" + a.getUUID());
                finish(server, "restart-result.txt"); return;
            }
            if (stage == 0) {
                if (a == null || b == null || a.tickCount < 120 || b.tickCount < 120) return;
                require(server.getPlayerList().getPlayerCount() == 2 && !a.getUUID().equals(b.getUUID()), "distinct isolated peers required");
                originalA = a; identityA = a.getUUID();
                platform(server.overworld());
                server.overworld().setBlock(NODE, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
                node(server).setOwnerUUID(a.getUUID()); node(server).setNodeName("Recheck-initial");
                a.setGameMode(GameType.SURVIVAL); b.setGameMode(GameType.SURVIVAL);
                a.teleportTo(server.overworld(), .5, 81, 2.5, Set.of(), 180, 0);
                b.teleportTo(server.overworld(), 1.5, 81, 2.5, Set.of(), 180, 0);
                open(a); open(b); next(1); return;
            }
            if (stage == 10) {
                if (a != null || !RecheckSessionState.ack("a", stage)) return;
                check("normal-client-disconnect", server.getPlayerList().getPlayer(identityA) == null,
                        "B remains=" + (b != null) + "; original A charging=" + SkillChargingManager.isCharging(identityA));
                next(11); return;
            }
            if (stage == 11 && a == null) return;
            require(a != null && b != null, "unexpected peer loss at stage " + stage);
            if (!RecheckSessionState.ack("a", stage) || !RecheckSessionState.ack("b", stage) || age < 15) return;
            switch (stage) {
                case 1 -> { a.closeContainer(); b.closeContainer(); preparePaper(a); preparePaper(b); next(2); }
                case 2 -> {
                    requireCharging(a, "A"); requireCharging(b, "B");
                    snapshot(a, "first-a"); snapshot(b, "first-b");
                    check("both-real-key-down-and-ack", state(a).acknowledged && state(b).acknowledged,
                            "A CP=" + data(a).getCurrentCp() + " B CP=" + data(b).getCurrentCp());
                    check("both-paper-payment", paper(a) == 0 && paper(b) == 0 && data(a).getCurrentCp() < 6000 && data(b).getCurrentCp() < 6000,
                            "paper A/B=" + paper(a) + "/" + paper(b)); next(3);
                }
                case 3 -> {
                    check("both-real-key-up", state(a) == null && state(b) == null && paper(a) == 64 && paper(b) == 64,
                            "A/B charging=" + (state(a) != null) + "/" + (state(b) != null) + " paper=" + paper(a) + "/" + paper(b));
                    data(a).clearCooldowns(); data(b).clearCooldowns(); next(4);
                }
                case 4 -> {
                    check("replayed-illegal-key-down", state(a) == null && state(b) == null,
                            "A=" + describe(a) + " B=" + describe(b));
                    SkillChargingManager.cancel(a); SkillChargingManager.cancel(b);
                    preparePaper(a); preparePaper(b); next(5);
                }
                case 5 -> {
                    requireCharging(a, "A"); requireCharging(b, "B");
                    epochA = state(a).epoch; epochB = state(b).epoch;
                    snapshot(a, "second-a"); snapshot(b, "second-b"); next(6);
                }
                case 6 -> {
                    check("foreign-and-old-key-up", state(a) != null && state(a).epoch == epochA && state(b) != null && state(b).epoch == epochB,
                            "A=" + describe(a) + " B=" + describe(b));
                    var nether = server.getLevel(Level.NETHER); require(nether != null, "Nether missing"); platform(nether);
                    a.teleportTo(nether, .5, 81, 2.5, Set.of(), 180, 0); next(7);
                }
                case 7 -> {
                    check("actual-dimension-clears-charging", a.level().dimension() == Level.NETHER && state(a) == null && state(b) == null,
                            "dimension=" + a.level().dimension().location() + " A=" + describe(a) + " B=" + describe(b)); next(8);
                }
                case 8 -> {
                    check("old-dimension-input-rejected", state(a) == null, describe(a));
                    SkillChargingManager.cancel(a);
                    a.teleportTo(server.overworld(), .5, 81, 2.5, Set.of(), 180, 0);
                    RecheckPersistenceProbe.prepare(a); next(9);
                }
                case 9 -> {
                    check("real-flying-before-disconnect", AeroPassiveRuntime.isFlyingActive(a) && a.getAbilities().mayfly,
                            "active=" + AeroPassiveRuntime.isFlyingActive(a) + " mayfly=" + a.getAbilities().mayfly + " flying=" + a.getAbilities().flying);
                    next(10);
                }
                case 11 -> {
                    check("same-uuid-new-server-player", a.getUUID().equals(identityA) && a != originalA, "UUID=" + a.getUUID());
                    RecheckPersistenceProbe.verifyReconnect(a);
                    check("reconnect-early-persistence-probe", true, "LOWEST login snapshot verified");
                    open(a); open(b); next(12);
                }
                case 12 -> {
                    check("reconnect-menu-session-replay-owner-order", node(server).getNodeName().equals("Recheck-high"), "actual=" + node(server).getNodeName());
                    a.closeContainer(); b.closeContainer(); data(a).setCurrentCp(5000); data(a).clearCooldowns(); data(a).syncTo(a); next(13);
                }
                case 13 -> {
                    require(AeroPassiveRuntime.isFlyingActive(a) && a.getAbilities().mayfly, "real flying must be active before normal server stop");
                    check("real-flying-before-stop", true, "active=true mayfly=" + a.getAbilities().mayfly + " flying=" + a.getAbilities().flying + " cp=" + data(a).getCurrentCp());
                    RecheckShutdownTransientProbe.armJet(b);
                    finish(server, "server-result.txt");
                }
                default -> throw new IllegalStateException("unknown stage " + stage);
            }
        } catch (Throwable error) {
            RecheckSessionState.append("server-checks.txt", "FIXTURE_OR_FATAL stage=" + stage + " " + error);
            failures++; finish(server, Boolean.getBoolean("academy.recheckSessionRestart") ? "restart-result.txt" : "server-result.txt");
        }
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        if (RecheckSessionState.enabled() && RecheckSessionState.role().equals("server"))
            RecheckSessionState.write(Boolean.getBoolean("academy.recheckSessionRestart") ? "restart-stopped.txt" : "server-stopped.txt",
                    "ServerStoppedEvent after server save; tick=" + event.getServer().getTickCount());
    }    private static void next(int next) { stage = next; age = 0; RecheckSessionState.write("stage.txt", Integer.toString(stage)); }
    private static void check(String id, boolean pass, String detail) {
        checks++; if (!pass) failures++;
        RecheckSessionState.append("server-checks.txt", (pass ? "PASS " : "FAIL ") + id + " stage=" + stage + " " + detail);
    }
    private static void finish(MinecraftServer server, String file) {
        RecheckSessionState.write(file, "status=" + (failures == 0 ? "PASS" : "FAIL") + "\nstage=" + stage + "\nchecks=" + checks + "\nfailures=" + failures + "\n");
        finished = true; server.halt(false);
    }
    private static void require(boolean yes, String message) { if (!yes) throw new IllegalStateException(message); }
    private static PlayerAbilityData data(ServerPlayer p) { return p.getData(AcademyAttachments.PLAYER_ABILITY); }
    private static SkillChargingManager.ChargingState state(ServerPlayer p) { return SkillChargingManager.getState(p.getUUID()); }
    private static String describe(ServerPlayer p) { var s = state(p); return s == null ? "none" : "epoch=" + s.epoch + ",generation=" + s.generation + ",ack=" + s.acknowledged; }
    private static void requireCharging(ServerPlayer p, String peer) { require(state(p) != null && state(p).acknowledged, "valid press missing for " + peer + " " + describe(p)); }
    private static int paper(ServerPlayer p) { return p.getInventory().countItem(Items.PAPER); }
    private static void snapshot(ServerPlayer p, String name) { var s = state(p); RecheckSessionState.write(name + ".txt", s.epoch + "," + s.generation + "," + com.mohistmc.academy.network.SkillInputSessionManager.sessionId(p)); }
    private static void preparePaper(ServerPlayer p) {
        SkillChargingManager.cancel(p); p.getInventory().clearContent();
        var d = data(p); d.reset(); d.setDevMode(false); d.setCurrentAbility(AbilityCategory.TELEKINESIS); d.setPlayerLevel(5);
        d.learnSkill("paper_drill"); d.learnSkill("perfect_paper"); d.setProficiency("paper_drill", .5F); d.setProficiency("perfect_paper", 1);
        d.clearCooldowns(); d.setCurrentPreset(0); d.setSlot(0, 2, "paper_drill"); d.setAbilityActive(true); d.recalculateMaxResources(true);
        require(!d.isOnCooldown("paper_drill"), "fixture cooldown must be cleared before press");
        p.getInventory().setItem(1, new ItemStack(Items.PAPER, 64)); d.syncTo(p); p.inventoryMenu.broadcastChanges();
    }
    private static BaseNodeBlockEntity node(MinecraftServer server) { return (BaseNodeBlockEntity) server.overworld().getBlockEntity(NODE); }
    private static void open(ServerPlayer p) { p.serverLevel().getBlockState(NODE).useWithoutItem(p.serverLevel(), p, new BlockHitResult(Vec3.atCenterOf(NODE), Direction.NORTH, NODE, false)); }
    private static void platform(ServerLevel level) {
        for (int x = -3; x <= 4; x++) for (int z = -3; z <= 4; z++) {
            level.setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 81; y <= 85; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
    }
}