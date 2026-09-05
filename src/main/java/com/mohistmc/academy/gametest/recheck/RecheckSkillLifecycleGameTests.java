package com.mohistmc.academy.gametest.recheck;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.SkillChargingManager;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime;
import com.mohistmc.academy.skill.ability.telekinesis.PaperDrillEffect;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Production player ticks, hurt/die, PlayerList.remove and teleportTo generate all observed events. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class RecheckSkillLifecycleGameTests {
    private enum Exit { DEATH, LOGOUT, DIMENSION, CANCELED_DEATH }
    private RecheckSkillLifecycleGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void paidPaperDrillReturnsExactlyOnceOnRealDeath(GameTestHelper h) { paper(h, Exit.DEATH); }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void paidPaperDrillReturnsExactlyOnceBeforeRealLogoutSave(GameTestHelper h) { paper(h, Exit.LOGOUT); }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void paidPaperDrillEndsOnActualDimensionTransfer(GameTestHelper h) { paper(h, Exit.DIMENSION); }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void canceledDeathDoesNotAbortAnAlivePlayersPaperDrill(GameTestHelper h) { paper(h, Exit.CANCELED_DEATH); }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aeroSessionsEndOnRealDeath(GameTestHelper h) { aero(h, Exit.DEATH, false); }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aeroSessionsEndBeforeRealLogoutSave(GameTestHelper h) { aero(h, Exit.LOGOUT, false); }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aeroSessionsEndOnActualDimensionTransfer(GameTestHelper h) { aero(h, Exit.DIMENSION, false); }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void canceledDeathDoesNotRevokeAlivePlayersAeroSessions(GameTestHelper h) { aero(h, Exit.CANCELED_DEATH, false); }


    @GameTest(template = "empty", timeoutTicks = 100)
    public static void flyingSessionEndsOnRealDeath(GameTestHelper h) { aero(h, Exit.DEATH, true); }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void flyingSessionEndsBeforeRealLogoutSave(GameTestHelper h) { aero(h, Exit.LOGOUT, true); }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void flyingSessionEndsOnActualDimensionTransfer(GameTestHelper h) { aero(h, Exit.DIMENSION, true); }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void canceledDeathDoesNotRevokeAlivePlayersFlying(GameTestHelper h) { aero(h, Exit.CANCELED_DEATH, true); }

    private static PlayerAbilityData ability(ServerPlayer player, AbilityCategory category, String... skills) {
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(category); data.setPlayerLevel(5); data.setAbilityActive(true); data.setDevMode(false);
        for (String skill : skills) { data.learnSkill(skill); data.setProficiency(skill, .5F); }
        data.setCurrentCp(8000); data.setCurrentOverload(0);
        return data;
    }

    private static void paper(GameTestHelper helper, Exit exit) {
        var session = RecheckPlayers.connect(helper);
        var player = session.player();
        var observer = new LifecycleObserver(player, exit == Exit.CANCELED_DEATH);
        NeoForge.EVENT_BUS.register(observer);
        helper.runAfterDelay(65, () -> {
            try {
                var data = ability(player, AbilityCategory.TELEKINESIS, "perfect_paper", "paper_drill");
                data.setProficiency("perfect_paper", 1);
                data.setSlot(0, 2, "paper_drill");
                player.getInventory().clearContent();
                player.getInventory().add(new ItemStack(Items.PAPER, 64));
                var effect = (PaperDrillEffect) SkillRegistry.getSkill("paper_drill").getEffect();
                check(effect.canStartCharging(player, data), "paper fixture preflight failed");
                effect.onChargingStart(player, data);
                var state = SkillChargingManager.startCharging(player.getUUID(), 2, "paper_drill", 1,
                        player.serverLevel().getGameTime());
                state.acknowledged = true;
                // Deliberately only initialize the already-acknowledged fixture here.
                // The ordinary server PlayerTick subscriber must perform material payment.
                helper.runAfterDelay(3, () -> {
                    try {
                        check(SkillChargingManager.isCharging(player.getUUID())
                                        && player.getInventory().countItem(Items.PAPER) == 0 && data.getCurrentCp() < 8000,
                                "natural real-player ticks did not start paid paper drill");
                        exit(helper, session, exit);
                        int paper = player.getInventory().countItem(Items.PAPER);
                        if (exit == Exit.DEATH) paper += helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                                player.getBoundingBox().inflate(5), item -> item.getItem().is(Items.PAPER))
                                .stream().mapToInt(item -> item.getItem().getCount()).sum();
                        LogUtils.getLogger().info("RECHECK_PAPER_LIFECYCLE {} deaths={} logouts={} dimensions={} alive={} charging={} paper={}",
                                exit, observer.deaths, observer.logouts, observer.dimensions, player.isAlive(),
                                SkillChargingManager.isCharging(player.getUUID()), paper);
                        observer.checkExit(exit);
                        if (exit == Exit.CANCELED_DEATH) {
                            check(player.isAlive() && SkillChargingManager.isCharging(player.getUUID()) && paper == 0,
                                    "canceled real death aborted an alive player's paid paper drill");
                        } else {
                            check(!SkillChargingManager.isCharging(player.getUUID()) && paper == 64,
                                    "real " + exit + " retained paper charging or returned incorrect paper: " + paper);
                            SkillChargingManager.cancel(player);
                            check(player.getInventory().countItem(Items.PAPER) + (exit == Exit.DEATH ? helper.getLevel()
                                    .getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(5),
                                            item -> item.getItem().is(Items.PAPER))
                                    .stream().mapToInt(item -> item.getItem().getCount()).sum() : 0) == 64,
                                    "second cancellation duplicated material");
                        }
                        helper.succeed();
                    } finally { NeoForge.EVENT_BUS.unregister(observer); session.close(); }
                });
            } catch (Throwable failure) { NeoForge.EVENT_BUS.unregister(observer); session.close(); throw failure; }
        });
    }

    private static void aero(GameTestHelper helper, Exit exit, boolean flight) {
        var session = RecheckPlayers.connect(helper);
        var player = session.player();
        var observer = new LifecycleObserver(player, exit == Exit.CANCELED_DEATH);
        NeoForge.EVENT_BUS.register(observer);
        helper.runAfterDelay(65, () -> {
            try {
                var data = ability(player, AbilityCategory.AEROHAND, "offense_armour", "flying");
                player.getAbilities().mayfly = false; player.getAbilities().flying = false;
                if (flight) check(AeroPassiveRuntime.toggleFlying(player, data), "flying setup failed");
                else check(AeroPassiveRuntime.toggleOffenseArmour(player, data), "armour setup failed");
                helper.runAfterDelay(3, () -> {
                    try {
                        check(flight ? AeroPassiveRuntime.isFlyingActive(player) && player.getAbilities().mayfly
                                        : AeroPassiveRuntime.isOffenseArmourEngaged(player)
                                        && player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= .89,
                                "natural real-player ticks did not engage " + (flight ? "flight" : "armour"));
                        exit(helper, session, exit);
                        boolean armour = AeroPassiveRuntime.isOffenseArmourEngaged(player);
                        boolean flying = AeroPassiveRuntime.isFlyingActive(player);
                        double resistance = player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                        LogUtils.getLogger().info("RECHECK_AERO_LIFECYCLE flight={} {} deaths={} logouts={} dimensions={} alive={} armour={} flying={} mayfly={} kbr={}",
                                flight, exit, observer.deaths, observer.logouts, observer.dimensions, player.isAlive(),
                                armour, flying, player.getAbilities().mayfly, resistance);
                        observer.checkExit(exit);
                        if (exit == Exit.CANCELED_DEATH) check(player.isAlive()
                                        && (flight ? flying && player.getAbilities().mayfly : armour && resistance >= .89),
                                "canceled real death revoked alive player's paid " + (flight ? "flight" : "armour"));
                        else check(!armour && !flying && !player.getAbilities().mayfly
                                        && !player.getAbilities().flying && resistance < .01,
                                "real " + exit + " retained armour/flight grant or modifier");
                        helper.succeed();
                    } finally { NeoForge.EVENT_BUS.unregister(observer); session.close(); }
                });
            } catch (Throwable failure) { NeoForge.EVENT_BUS.unregister(observer); session.close(); throw failure; }
        });
    }

    private static void exit(GameTestHelper helper, RecheckPlayers.Session session, Exit exit) {
        var player = session.player();
        switch (exit) {
            case DEATH, CANCELED_DEATH -> {
                player.invulnerableTime = 0;
                // A finite affordable hit isolates death handling from armour resource exhaustion.
                player.setHealth(.01F);
                check(player.hurt(player.damageSources().genericKill(), 10),
                        "real lethal hurt path rejected unexpectedly");
            }
            case LOGOUT -> session.close();
            case DIMENSION -> {
                var destination = helper.getLevel().getServer().getLevel(Level.NETHER);
                check(destination != null, "Nether fixture absent");
                player.teleportTo(destination, player.getX(), 200, player.getZ(), 0, 0);
                check(player.serverLevel() == destination, "actual teleport did not change ServerLevel");
            }
        }
    }

    public static final class LifecycleObserver {
        private final UUID player;
        private final boolean cancelDeath;
        private int deaths, logouts, dimensions;
        LifecycleObserver(ServerPlayer player, boolean cancelDeath) { this.player = player.getUUID(); this.cancelDeath = cancelDeath; }
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void death(LivingDeathEvent event) {
            if (!event.getEntity().getUUID().equals(player)) return;
            deaths++;
            if (cancelDeath) { event.getEntity().setHealth(20); event.setCanceled(true); }
        }
        @SubscribeEvent
        public void logout(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity().getUUID().equals(player)) logouts++; }
        @SubscribeEvent
        public void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if (event.getEntity().getUUID().equals(player)) dimensions++; }
        void checkExit(Exit exit) {
            check(switch (exit) { case DEATH, CANCELED_DEATH -> deaths == 1; case LOGOUT -> logouts == 1; case DIMENSION -> dimensions == 1; },
                    "production lifecycle event was not observed for " + exit);
        }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message); }
}

