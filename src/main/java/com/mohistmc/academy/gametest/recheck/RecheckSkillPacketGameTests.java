package com.mohistmc.academy.gametest.recheck;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.network.*;
import com.mohistmc.academy.skill.*;
import java.lang.reflect.Proxy;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** New recheck of real handlers. Context only dispatches enqueueWork; it does not emulate policy. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class RecheckSkillPacketGameTests {
    private RecheckSkillPacketGameTests() {}

    @GameTest(template = "empty")
    public static void repeatedKeyDownCannotRestartCompletedGeneration(GameTestHelper h) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); preparePaper(p);
            var packet = down(p, 0, 8);
            SkillKeyDownPacket.handle(packet, context(p));
            var original = requireState(h, p);
            SkillKeyUpPacket.handle(new SkillKeyUpPacket(0, "paper_drill", original.epoch), context(p));
            check(h, !SkillChargingManager.isCharging(p.getUUID()), "valid key-up must end first intent");
            SkillKeyDownPacket.handle(packet, context(p));
            check(h, !SkillChargingManager.isCharging(p.getUUID()), "duplicate generation 8 restarted completed intent");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reorderedKeyDownCannotRestartOlderGeneration(GameTestHelper h) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); preparePaper(p);
            SkillKeyDownPacket.handle(down(p, 0, 9), context(p));
            var state = requireState(h, p);
            ChargingCancelPacket.handle(new ChargingCancelPacket(0, "paper_drill", state.epoch), context(p));
            SkillKeyDownPacket.handle(down(p, 0, 8), context(p));
            check(h, !SkillChargingManager.isCharging(p.getUUID()), "older generation 8 restarted after generation 9 was cancelled");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void zeroKeyDownGenerationIsRejected(GameTestHelper h) { invalidGeneration(h, 0); }
    @GameTest(template = "empty")
    public static void negativeKeyDownGenerationIsRejected(GameTestHelper h) { invalidGeneration(h, -1); }
    private static void invalidGeneration(GameTestHelper h, long generation) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); preparePaper(p);
            SkillKeyDownPacket.handle(down(p, 0, generation), context(p));
            check(h, !SkillChargingManager.isCharging(p.getUUID()), "illegal generation " + generation + " started a charging intent");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void deadPlayerCannotStartCharging(GameTestHelper h) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); preparePaper(p); p.setHealth(0);
            SkillKeyDownPacket.handle(down(p, 0, 1), context(p));
            check(h, !SkillChargingManager.isCharging(p.getUUID()), "dead player created a new charging state");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void deadPlayerCannotSpendCpOnOneShot(GameTestHelper h) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); var d = p.getData(AcademyAttachments.PLAYER_ABILITY);
            d.reset(); d.setCurrentAbility(AbilityCategory.AEROHAND); d.setPlayerLevel(5);
            d.learnSkill("air_cooling"); d.setProficiency("air_cooling", .5F);
            d.setSlot(0, 0, "air_cooling"); d.setAbilityActive(true); d.recalculateMaxResources(true);
            d.setCurrentOverload(100); float cp = d.getCurrentCp(); p.setHealth(0);
            UseSkillPacket.handle(new UseSkillPacket(0, token(p, 1)), context(p));
            check(h, d.getCurrentCp() == cp && d.getCurrentOverload() == 100,
                    "dead one-shot mutated CP/overload: " + cp + " -> " + d.getCurrentCp() + "/" + d.getCurrentOverload());
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void queuedKeyDownCannotResurrectLoggedOutPlayer(GameTestHelper h) {
        var session = RecheckPlayers.connect(h);
        try {
            var p = session.player(); preparePaper(p);
            var queued = context(p); var packet = down(p, 0, 1); session.close();
            SkillKeyDownPacket.handle(packet, queued);
            check(h, !SkillChargingManager.isCharging(p.getUUID()), "queued handler resurrected state after PlayerList.remove/logout");
        } finally { SkillChargingManager.cancel(session.player()); session.close(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void oldDimensionKeyDownCannotRestartAfterActualTeleport(GameTestHelper h) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); preparePaper(p);
            var packet = down(p, 0, 8);
            SkillKeyDownPacket.handle(packet, context(p)); requireState(h, p);
            var destination = p.server.getLevel(Level.NETHER);
            check(h, destination != null, "nether fixture missing");
            p.teleportTo(destination, .5, 100, .5, Set.of(), 0, 0);
            check(h, p.level().dimension() == Level.NETHER, "actual dimension transition failed");
            check(h, !SkillChargingManager.isCharging(p.getUUID()), "dimension lifecycle failed to cancel original state");
            SkillKeyDownPacket.handle(packet, context(p));
            check(h, !SkillChargingManager.isCharging(p.getUUID()), "old overworld key-down created nether charging state");
            SkillKeyDownPacket.handle(down(p, 0, 1), context(p)); requireState(h, p);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void exactKeyUpEndsOnlyMatchingPlayerAndEpoch(GameTestHelper h) {
        try (var a = RecheckPlayers.connect(h); var b = RecheckPlayers.connect(h)) {
            preparePaper(a.player()); preparePaper(b.player());
            SkillKeyDownPacket.handle(down(a.player(), 0, 1), context(a.player()));
            SkillKeyDownPacket.handle(down(b.player(), 0, 1), context(b.player()));
            var stateA = requireState(h, a.player()); var stateB = requireState(h, b.player());
            SkillKeyUpPacket.handle(new SkillKeyUpPacket(0, "paper_drill", stateA.epoch), context(b.player()));
            check(h, SkillChargingManager.getState(b.player().getUUID()) == stateB, "A release altered B charging state");
            SkillKeyUpPacket.handle(new SkillKeyUpPacket(0, "paper_drill", stateA.epoch + 1), context(a.player()));
            check(h, SkillChargingManager.getState(a.player().getUUID()) == stateA, "wrong epoch altered A charging state");
            SkillKeyUpPacket.handle(new SkillKeyUpPacket(0, "paper_drill", stateA.epoch), context(a.player()));
            check(h, !SkillChargingManager.isCharging(a.player().getUUID()), "matching key-up did not clear A");
            check(h, SkillChargingManager.getState(b.player().getUUID()) == stateB, "matching A key-up touched B");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reconnectRejectsOldNonceAndOldPlayerButAcceptsFreshInput(GameTestHelper h) {
        var old = RecheckPlayers.connect(h);
        try {
            var p = old.player(); preparePaper(p); var packet = down(p, 0, 1); var oldContext = context(p);
            var profile = p.getGameProfile(); old.close();
            try (var fresh = RecheckPlayers.connect(h, profile)) {
                preparePaper(fresh.player());
                check(h, !packet.actionToken().session().equals(SkillInputSessionManager.sessionId(fresh.player())), "reconnect reused nonce");
                SkillKeyDownPacket.handle(packet, oldContext);
                SkillKeyDownPacket.handle(packet, context(fresh.player()));
                check(h, !SkillChargingManager.isCharging(p.getUUID()), "old connection input recreated state on rejoin");
                SkillKeyDownPacket.handle(down(fresh.player(), 0, 1), context(fresh.player()));
                requireState(h, fresh.player());
            }
        } finally { old.close(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void forgedNonceAndInvalidSlotCannotPoisonFreshInput(GameTestHelper h) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); preparePaper(p);
            SkillKeyDownPacket.handle(new SkillKeyDownPacket(0, new SkillInputToken(java.util.UUID.randomUUID(), Long.MAX_VALUE)), context(p));
            SkillKeyDownPacket.handle(down(p, -1, Long.MAX_VALUE), context(p));
            SkillKeyDownPacket.handle(down(p, 0, 1), context(p)); requireState(h, p);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void inputTokenCodecRoundTripRetainsFullSignedValues(GameTestHelper h) {
        var buffer = io.netty.buffer.Unpooled.buffer();
        try {
            var input = new SkillInputToken(new java.util.UUID(Long.MIN_VALUE, Long.MAX_VALUE), Long.MAX_VALUE);
            var packet = new SkillKeyDownPacket(3, input);
            SkillKeyDownPacket.STREAM_CODEC.encode(buffer, packet);
            check(h, packet.equals(SkillKeyDownPacket.STREAM_CODEC.decode(buffer)) && !buffer.isReadable(), "new input codec round trip changed token");
        } finally { buffer.release(); }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void inRangeEmptyAndWrongKindSlotsCannotPoisonInputLedger(GameTestHelper h) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); preparePaper(p); var d = p.getData(AcademyAttachments.PLAYER_ABILITY);
            SkillKeyDownPacket.handle(down(p, 1, Long.MAX_VALUE), context(p));
            d.learnSkill("air_cooling"); d.setSlot(0, 1, "air_cooling");
            SkillKeyDownPacket.handle(down(p, 1, Long.MAX_VALUE), context(p));
            UseSkillPacket.handle(new UseSkillPacket(0, token(p, Long.MAX_VALUE)), context(p));
            SkillKeyDownPacket.handle(down(p, 0, 1), context(p)); requireState(h, p);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectedCooldownIntentCannotReviveAfterCooldownClears(GameTestHelper h) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); preparePaper(p); var d = p.getData(AcademyAttachments.PLAYER_ABILITY);
            d.setCooldown("paper_drill", 100); var blocked = down(p, 0, 1);
            SkillKeyDownPacket.handle(blocked, context(p));
            check(h, !SkillChargingManager.isCharging(p.getUUID()), "cooldown did not reject initial intent");
            d.clearCooldowns(); SkillKeyDownPacket.handle(blocked, context(p));
            check(h, !SkillChargingManager.isCharging(p.getUUID()), "rejected intent revived after cooldown cleared");
            SkillKeyDownPacket.handle(down(p, 0, 2), context(p)); requireState(h, p);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rejectedResourceIntentCannotReviveAfterRefill(GameTestHelper h) {
        try (var session = RecheckPlayers.connect(h)) {
            var p = session.player(); preparePaper(p); var d = p.getData(AcademyAttachments.PLAYER_ABILITY);
            d.setCurrentCp(0); var blocked = down(p, 0, 1);
            SkillKeyDownPacket.handle(blocked, context(p));
            check(h, !SkillChargingManager.isCharging(p.getUUID()), "zero CP started initial intent");
            d.recalculateMaxResources(true); SkillKeyDownPacket.handle(blocked, context(p));
            check(h, !SkillChargingManager.isCharging(p.getUUID()), "rejected intent revived after refill");
            SkillKeyDownPacket.handle(down(p, 0, 2), context(p)); requireState(h, p);
        }
        h.succeed();
    }
    private static SkillInputToken token(ServerPlayer p, long sequence) {
        var nonce = SkillInputSessionManager.sessionId(p);
        if (nonce.equals(SkillInputToken.ABSENT)) throw new IllegalStateException("registered player fixture has no login input session");
        return new SkillInputToken(nonce, sequence);
    }
    private static SkillKeyDownPacket down(ServerPlayer p, int slot, long sequence) { return new SkillKeyDownPacket(slot, token(p, sequence)); }
    private static void preparePaper(ServerPlayer p) {
        var d = p.getData(AcademyAttachments.PLAYER_ABILITY);
        d.reset(); d.clearCooldowns(); d.setDevMode(false); d.setCurrentAbility(AbilityCategory.TELEKINESIS); d.setPlayerLevel(5);
        d.learnSkill("paper_drill"); d.learnSkill("perfect_paper");
        d.setProficiency("paper_drill", .5F); d.setProficiency("perfect_paper", 1);
        d.setCurrentPreset(0); d.setSlot(0, 0, "paper_drill"); d.setAbilityActive(true);
        d.recalculateMaxResources(true); p.getInventory().setItem(1, new ItemStack(Items.PAPER, 64));
    }
    private static SkillChargingManager.ChargingState requireState(GameTestHelper h, ServerPlayer p) {
        var state = SkillChargingManager.getState(p.getUUID());
        check(h, state != null, "fixture valid key-down was not accepted"); return state;
    }
    private static void check(GameTestHelper h, boolean condition, String message) { if (!condition) h.fail(message); }
    private static IPayloadContext context(ServerPlayer p) {
        return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
                new Class<?>[]{IPayloadContext.class}, (proxy, method, args) -> {
                    if (method.getName().equals("player")) return p;
                    if (method.getName().equals("enqueueWork")) {
                        if (args[0] instanceof Runnable work) { work.run(); return CompletableFuture.completedFuture(null); }
                        if (args[0] instanceof java.util.function.Supplier<?> work) return CompletableFuture.completedFuture(work.get());
                    }
                    if (method.getName().equals("toString")) return "RecheckSkillPacketContext";
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}