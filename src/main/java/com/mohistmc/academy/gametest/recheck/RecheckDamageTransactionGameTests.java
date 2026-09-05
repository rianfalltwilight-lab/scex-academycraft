package com.mohistmc.academy.gametest.recheck;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime;
import com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** New 0.0.18 recheck: actual hurt calls dispatch the events; no directly constructed hurt event. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class RecheckDamageTransactionGameTests {
    private RecheckDamageTransactionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void realSurvivalPlayerActuallyDispatchesDamage(GameTestHelper h) {
        var session = RecheckPlayers.connect(h);
        h.runAfterDelay(65, () -> {
            var player = session.player();
            var listener = new ObserveDamage(player, false);
            NeoForge.EVENT_BUS.register(listener);
            try {
                player.setHealth(20); player.invulnerableTime = 0;
                boolean accepted = player.hurt(player.damageSources().generic(), 10);
                check(accepted && listener.seen == 1 && near(player.getHealth(), 10),
                        "real survival damage control failed: accepted=" + accepted + " events=" + listener.seen + " hp=" + player.getHealth());
                LogUtils.getLogger().info("RECHECK_REAL_DAMAGE_CONTROL_PASS class={} fake={} events={} hp={}",
                        player.getClass().getName(), player.isFakePlayer(), listener.seen, player.getHealth());
                h.succeed();
            } finally { NeoForge.EVENT_BUS.unregister(listener); session.close(); }
        });
    }

    @GameTest(template = "empty")
    public static void fakePlayerRejectionNeverDispatchesDamage(GameTestHelper h) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "[FakeBoundary]"));
        player.setGameMode(GameType.SURVIVAL);
        var listener = new ObserveDamage(player, false);
        NeoForge.EVENT_BUS.register(listener);
        try {
            float hp = player.getHealth();
            boolean accepted = player.hurt(player.damageSources().generic(), 10);
            check(player.isFakePlayer() && !accepted && listener.seen == 0 && player.getHealth() == hp,
                    "FakePlayer boundary changed; do not assume it executes the player hurt pipeline");
            LogUtils.getLogger().info("RECHECK_FAKE_DAMAGE_CONTROL_PASS class={} fake={} events={}",
                    player.getClass().getName(), player.isFakePlayer(), listener.seen);
            h.succeed();
        } finally { NeoForge.EVENT_BUS.unregister(listener); }
    }

    @GameTest(template = "empty")
    public static void insulationDoesNotPayForSpawnProtection(GameTestHelper h) {
        var defender = RecheckPlayers.connect(h);
        var attacker = RecheckPlayers.connect(h);
        try {
            PlayerAbilityData data = ability(defender.player(), AbilityCategory.TELEKINESIS, "insulation");
            ability(attacker.player(), AbilityCategory.ELECTROMASTER);
            expectRejectedAbilityUnchanged(h, attacker.player(), defender.player(), data, "spawn-protection");
            h.succeed();
        } finally { attacker.close(); defender.close(); }
    }

    @GameTest(template = "empty")
    public static void insulationDoesNotPayForFakePlayerInvulnerability(GameTestHelper h) {
        var attacker = RecheckPlayers.connect(h);
        var defender = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "[FakeInsulation]"));
        defender.setGameMode(GameType.SURVIVAL);
        try {
            var data = ability(defender, AbilityCategory.TELEKINESIS, "insulation");
            ability(attacker.player(), AbilityCategory.ELECTROMASTER);
            expectRejectedAbilityUnchanged(h, attacker.player(), defender, data, "fake-player-invulnerability");
            h.succeed();
        } finally { attacker.close(); }
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void insulationDoesNotPayForVanillaPvpRejection(GameTestHelper h) {
        var defender = RecheckPlayers.connect(h);
        var attacker = RecheckPlayers.connect(h);
        h.runAfterDelay(65, () -> {
            boolean oldPvp = h.getLevel().getServer().isPvpAllowed();
            boolean oldAcademy = ACConfig.Server.PVP_ENABLED.get();
            try {
                h.getLevel().getServer().setPvpAllowed(false); ACConfig.Server.PVP_ENABLED.set(true);
                var data = ability(defender.player(), AbilityCategory.TELEKINESIS, "insulation");
                ability(attacker.player(), AbilityCategory.ELECTROMASTER);
                CompoundTag before = data.toSyncTag().copy();
                boolean accepted = AcademyDamageHelper.hurt(attacker.player(), defender.player(),
                        attacker.player().damageSources().playerAttack(attacker.player()), 10);
                unchanged(data, before, !accepted, "vanilla-pvp");
                h.succeed();
            } finally {
                h.getLevel().getServer().setPvpAllowed(oldPvp); ACConfig.Server.PVP_ENABLED.set(oldAcademy);
                attacker.close(); defender.close();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void abilityInsulationDoesNotPayForCanceledIncomingDamage(GameTestHelper h) {
        canceled(h, "insulation", AbilityCategory.TELEKINESIS, true);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void lightningInsulationDoesNotPayForCanceledIncomingDamage(GameTestHelper h) {
        canceled(h, "insulation", AbilityCategory.TELEKINESIS, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void hardenDoesNotPayForCanceledIncomingDamage(GameTestHelper h) {
        canceled(h, "psycho_harden", AbilityCategory.TELEKINESIS, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ascendingAirDoesNotPayForCanceledIncomingDamage(GameTestHelper h) {
        canceled(h, "ascending_air", AbilityCategory.AEROHAND, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void armourDoesNotPayForCanceledIncomingDamage(GameTestHelper h) {
        canceled(h, "offense_armour", AbilityCategory.AEROHAND, false);
    }


    @GameTest(template = "empty", timeoutTicks = 110)
    public static void abilityInsulationDoesNotPayForInvulnerabilityFrameRejection(GameTestHelper h) {
        iframe(h, true);
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void lightningInsulationDoesNotPayForInvulnerabilityFrameRejection(GameTestHelper h) {
        iframe(h, false);
    }

    private static void iframe(GameTestHelper h, boolean academyDamage) {
        var defender = RecheckPlayers.connect(h);
        var attacker = RecheckPlayers.connect(h);
        h.runAfterDelay(65, () -> {
            var player = defender.player();
            var listener = new ObserveDamage(player, false);
            boolean oldAcademy = ACConfig.Server.PVP_ENABLED.get();
            NeoForge.EVENT_BUS.register(listener);
            try {
                ACConfig.Server.PVP_ENABLED.set(true);
                var data = ability(player, AbilityCategory.TELEKINESIS, "insulation");
                ability(attacker.player(), AbilityCategory.ELECTROMASTER);
                player.setHealth(20); player.invulnerableTime = 0;
                DamageSource source = academyDamage ? player.damageSources().generic() : player.damageSources().lightningBolt();
                boolean first = academyDamage ? AcademyDamageHelper.hurt(attacker.player(), player, source, 10)
                        : player.hurt(source, 10);
                check(first && player.getHealth() < 20 && player.invulnerableTime > 10 && data.getCurrentCp() < 8000,
                        "first real mitigated damage was not accepted/paid: hp=" + player.getHealth() + " cp=" + data.getCurrentCp());
                float hp = player.getHealth();
                CompoundTag before = data.toSyncTag().copy();
                boolean second = academyDamage ? AcademyDamageHelper.hurt(attacker.player(), player, source, 10)
                        : player.hurt(source, 10);
                check(!second && player.getHealth() == hp && listener.seen == 2,
                        "equal second hit was not rejected by the actual hurt iframe path: accepted=" + second + " events=" + listener.seen);
                unchanged(data, before, true, (academyDamage ? "ability" : "lightning") + "-insulation-iframe");
                h.succeed();
            } finally {
                NeoForge.EVENT_BUS.unregister(listener); ACConfig.Server.PVP_ENABLED.set(oldAcademy);
                attacker.close(); defender.close();
            }
        });
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void abilityInsulationDoesNotPayForActualShieldFullBlock(GameTestHelper h) {
        shield(h, true);
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void armourDoesNotPayForActualShieldFullBlock(GameTestHelper h) {
        shield(h, false);
    }

    private static void shield(GameTestHelper h, boolean academyDamage) {
        var defender = RecheckPlayers.connect(h);
        var attacker = RecheckPlayers.connect(h);
        var player = defender.player();
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD));
        player.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
        h.runAfterDelay(65, () -> {
            var listener = new ObserveDamage(player, false);
            boolean oldAcademy = ACConfig.Server.PVP_ENABLED.get();
            boolean oldPvp = h.getLevel().getServer().isPvpAllowed();
            NeoForge.EVENT_BUS.register(listener);
            try {
                ACConfig.Server.PVP_ENABLED.set(true); h.getLevel().getServer().setPvpAllowed(true);
                var data = ability(player, academyDamage ? AbilityCategory.TELEKINESIS : AbilityCategory.AEROHAND,
                        academyDamage ? "insulation" : "offense_armour");
                ability(attacker.player(), AbilityCategory.ELECTROMASTER);
                if (!academyDamage) check(AeroPassiveRuntime.toggleOffenseArmour(player, data), "armour setup failed");
                player.setHealth(20); player.invulnerableTime = 0;
                player.setYRot(0); player.setYHeadRot(0); player.setXRot(0);
                attacker.player().setPos(player.getX(), player.getY(), player.getZ() + 3);
                DamageSource source = player.damageSources().playerAttack(attacker.player());
                check(player.isBlocking() && player.isDamageSourceBlocked(source),
                        "real shield did not reach its natural five-tick blocking state/facing");
                CompoundTag before = data.toSyncTag().copy();
                int shieldBefore = player.getOffhandItem().getDamageValue();
                float rawDamage = academyDamage ? 10 : 100; // Armour reduces 100 to 7.5: enough to wear the shield.
                boolean accepted = academyDamage ? AcademyDamageHelper.hurt(attacker.player(), player, source, rawDamage)
                        : player.hurt(source, rawDamage);
                int shieldAfter = player.getOffhandItem().getDamageValue();
                check(!accepted && near(player.getHealth(), 20) && listener.seen == 1 && shieldAfter > shieldBefore,
                        "actual shield full-block control failed: accepted=" + accepted + " hp=" + player.getHealth()
                                + " events=" + listener.seen + " shield=" + shieldBefore + "=>" + shieldAfter);
                LogUtils.getLogger().info("RECHECK_ACTUAL_SHIELD_BLOCK ability={} damage={}=>{} hp={}",
                        academyDamage, shieldBefore, shieldAfter, player.getHealth());
                unchanged(data, before, true, (academyDamage ? "insulation" : "armour") + "-shield-full-block");
                h.succeed();
            } finally {
                NeoForge.EVENT_BUS.unregister(listener); ACConfig.Server.PVP_ENABLED.set(oldAcademy);
                h.getLevel().getServer().setPvpAllowed(oldPvp);
                attacker.close(); defender.close();
            }
        });
    }


    @GameTest(template = "empty", timeoutTicks = 100)
    public static void vectorDeviationDoesNotPayForCanceledIncomingDamage(GameTestHelper h) {
        canceled(h, "vec_deviation", AbilityCategory.VECMANIP, false);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void vectorReflectionDoesNotPayForCanceledIncomingDamage(GameTestHelper h) {
        canceled(h, "vec_reflection", AbilityCategory.VECMANIP, false);
    }


    @GameTest(template = "empty", timeoutTicks = 100)
    public static void lightShieldDoesNotPayForCanceledIncomingDamage(GameTestHelper h) {
        canceled(h, "light_shield", AbilityCategory.MELTDOWNER, false);
    }


    @GameTest(template = "empty", timeoutTicks = 110)
    public static void fullReflectionKeepsRealPlayersHealthMotionAndHurtCooldown(GameTestHelper h) {
        reflectionBoundary(h, 0);
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void fullReflectionPreservesSameTargetNestedIncomingHit(GameTestHelper h) {
        reflectionBoundary(h, 1);
    }

    @GameTest(template = "empty", timeoutTicks = 110)
    public static void fullReflectionPreservesSameTargetHitNestedInReflectedDamage(GameTestHelper h) {
        reflectionBoundary(h, 2);
    }

    private static void reflectionBoundary(GameTestHelper h, int nestedMode) {
        var session = RecheckPlayers.connect(h);
        var player = session.player();
        h.runAfterDelay(65, () -> {
            var attacker = net.minecraft.world.entity.EntityType.VILLAGER.create(h.getLevel());
            check(attacker != null, "reflection attacker fixture absent");
            attacker.setNoAi(true); attacker.setNoGravity(true);
            attacker.setPos(player.getX(), player.getY(), player.getZ() + 2);
            h.getLevel().addFreshEntity(attacker);
            DamageSource source = player.damageSources().mobAttack(attacker);
            var observer = new NestedReflectionDamage(player, attacker, source, nestedMode);
            NeoForge.EVENT_BUS.register(observer);
            try {
                var data = ability(player, AbilityCategory.VECMANIP, "vec_reflection");
                data.setProficiency("vec_reflection", 1);
                com.mohistmc.academy.skill.passive.VecDefenseRuntime.start(player.getUUID(),
                        com.mohistmc.academy.skill.passive.VecDefenseRuntime.Mode.REFLECTION);
                player.setHealth(20); player.invulnerableTime = 0;
                player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                boolean accepted = player.hurt(source, nestedMode == 1 ? 8 : 10);
                check(!accepted && attacker.getHealth() < attacker.getMaxHealth(),
                        "full reflection did not cancel the real outer hurt and damage its real source");
                if (nestedMode == 0) {
                    check(near(player.getHealth(), 20) && player.invulnerableTime == 0
                                    && player.getDeltaMovement().lengthSqr() < 1e-9 && near(data.getCurrentCp(), 7850),
                            "full reflected hurt retained downstream health/motion/iframe effects or incorrect payment: cp="
                                    + data.getCurrentCp() + " iframe=" + player.invulnerableTime + " motion=" + player.getDeltaMovement());
                } else {
                    check(observer.triggered && observer.innerAccepted && observer.innerHealth > 0 && observer.innerHealth < 20
                                    && near(player.getHealth(), observer.innerHealth)
                                    && player.invulnerableTime == observer.innerIframes && observer.innerIframes > 10,
                            "outer cancellation erased a nested real hit: nested=" + observer.innerAccepted
                                    + " hp=" + player.getHealth() + " iframe=" + player.invulnerableTime + "/" + observer.innerIframes);
                    com.mohistmc.academy.skill.passive.VecDefenseRuntime.stop(player.getUUID());
                    check(!player.hurt(player.damageSources().generic(), observer.innerDamage)
                                    && near(player.getHealth(), observer.innerHealth),
                            "outer full reflection erased the nested hit's lastHurt threshold");
                }
                LogUtils.getLogger().info("RECHECK_FULL_REFLECTION_PASS mode={} hp={} iframe={} nestedAccepted={} cp={}",
                        nestedMode, player.getHealth(), player.invulnerableTime, observer.innerAccepted, data.getCurrentCp());
                h.succeed();
            } finally {
                NeoForge.EVENT_BUS.unregister(observer);
                attacker.discard(); session.close();
            }
        });
    }

    public static final class NestedReflectionDamage {
        private final ServerPlayer player;
        private final net.minecraft.world.entity.LivingEntity attacker;
        private final DamageSource outer;
        private final int mode;
        private boolean triggered, innerAccepted;
        private int innerIframes;
        private float innerHealth, innerDamage;
        NestedReflectionDamage(ServerPlayer player, net.minecraft.world.entity.LivingEntity attacker,
                               DamageSource outer, int mode) {
            this.player = player; this.attacker = attacker; this.outer = outer; this.mode = mode;
        }
        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void incoming(LivingIncomingDamageEvent event) {
            if (mode == 0 || triggered) return;
            if (mode == 1 && (event.getEntity() != player || event.getSource() != outer)) return;
            if (mode == 2 && event.getEntity() != attacker) return;
            triggered = true;
            // Only the nested damage is unprotected. Restoring the paid context lets the outer hit reflect.
            com.mohistmc.academy.skill.passive.VecDefenseRuntime.stop(player.getUUID());
            try {
                // Exceed the outer 10-point hit so a nested hit is accepted even after its iframe write.
                innerDamage = mode == 2 ? 14 : 4;
                innerAccepted = player.hurt(player.damageSources().generic(), innerDamage);
                innerIframes = player.invulnerableTime;
                innerHealth = player.getHealth();
            } finally {
                com.mohistmc.academy.skill.passive.VecDefenseRuntime.start(player.getUUID(),
                        com.mohistmc.academy.skill.passive.VecDefenseRuntime.Mode.REFLECTION);
            }
        }
    }

    private static void canceled(GameTestHelper h, String skill, AbilityCategory category, boolean academyDamage) {
        var defender = RecheckPlayers.connect(h);
        var attacker = RecheckPlayers.connect(h);
        h.runAfterDelay(65, () -> {
            var player = defender.player();
            var listener = new ObserveDamage(player, true);
            boolean oldAcademy = ACConfig.Server.PVP_ENABLED.get();
            boolean oldPvp = h.getLevel().getServer().isPvpAllowed();
            NeoForge.EVENT_BUS.register(listener);
            try {
                ACConfig.Server.PVP_ENABLED.set(true); h.getLevel().getServer().setPvpAllowed(true);
                var data = ability(player, category, skill);
                ability(attacker.player(), AbilityCategory.ELECTROMASTER);
                if (skill.equals("light_shield")) new com.mohistmc.academy.skill.ability.meltdowner.LightShieldEffect().onChargingStart(player, data);
                if (skill.equals("psycho_harden")) check(TelekinesisPassiveHandler.togglePsychoHarden(player, data), "harden setup failed");
                if (skill.equals("offense_armour")) check(AeroPassiveRuntime.toggleOffenseArmour(player, data), "armour setup failed");

                if (category == AbilityCategory.VECMANIP)
                    com.mohistmc.academy.skill.passive.VecDefenseRuntime.start(player.getUUID(),
                            skill.equals("vec_deviation") ? com.mohistmc.academy.skill.passive.VecDefenseRuntime.Mode.DEVIATION
                                    : com.mohistmc.academy.skill.passive.VecDefenseRuntime.Mode.REFLECTION);

                player.setHealth(20); player.invulnerableTime = 0;
                CompoundTag before = data.toSyncTag().copy();
                DamageSource source = skill.equals("ascending_air") ? player.damageSources().fall()
                        : skill.equals("insulation") && !academyDamage ? player.damageSources().lightningBolt()
                        : skill.equals("offense_armour") ? player.damageSources().playerAttack(attacker.player())
                        : player.damageSources().generic();
                float amount = skill.equals("light_shield") ? 100 : skill.equals("ascending_air") ? 20 : 10;
                boolean accepted = academyDamage
                        ? AcademyDamageHelper.hurt(attacker.player(), player, source, amount) : player.hurt(source, amount);
                check(listener.seen == 1 && listener.canceled && !accepted && near(player.getHealth(), 20),
                        "real LOWEST-priority cancel control failed for " + skill + ": accepted=" + accepted + " events=" + listener.seen);
                unchanged(data, before, true, skill + (academyDamage ? "-ability" : "-ordinary") + "-canceled");

                if (skill.equals("psycho_harden")) check(TelekinesisPassiveHandler.isHardened(player), "canceled hit stopped hardening");
                if (skill.equals("offense_armour")) check(AeroPassiveRuntime.isOffenseArmourEngaged(player), "canceled hit stopped armour");
                h.succeed();
            } finally {
                NeoForge.EVENT_BUS.unregister(listener); ACConfig.Server.PVP_ENABLED.set(oldAcademy);
                h.getLevel().getServer().setPvpAllowed(oldPvp);
                if (skill.equals("light_shield")) new com.mohistmc.academy.skill.ability.meltdowner.LightShieldEffect().onChargingAbort(player, player.getData(AcademyAttachments.PLAYER_ABILITY));
                attacker.close(); defender.close();
            }
        });
    }

    private static void expectRejectedAbilityUnchanged(GameTestHelper h, ServerPlayer attacker,
            ServerPlayer defender, PlayerAbilityData data, String label) {
        boolean old = ACConfig.Server.PVP_ENABLED.get();
        var listener = new ObserveDamage(defender, false);
        NeoForge.EVENT_BUS.register(listener);
        try {
            ACConfig.Server.PVP_ENABLED.set(true);
            CompoundTag before = data.toSyncTag().copy();
            float health = defender.getHealth();
            boolean accepted = AcademyDamageHelper.hurt(attacker, defender, defender.damageSources().generic(), 10);
            check(!accepted && listener.seen == 0 && defender.getHealth() == health,
                    label + " fixture did not reject before damage events");
            unchanged(data, before, true, label);
        } finally { ACConfig.Server.PVP_ENABLED.set(old); NeoForge.EVENT_BUS.unregister(listener); }
    }

    private static PlayerAbilityData ability(ServerPlayer p, AbilityCategory category, String... skills) {
        var d = p.getData(AcademyAttachments.PLAYER_ABILITY);
        d.setCurrentAbility(category); d.setPlayerLevel(5); d.setAbilityActive(true); d.setDevMode(false);
        for (String skill : skills) { d.learnSkill(skill); d.setProficiency(skill, .5F); }
        d.setCurrentCp(8000); d.setCurrentOverload(0);
        return d;
    }

    private static void unchanged(PlayerAbilityData data, CompoundTag before, boolean rejected, String label) {
        CompoundTag after = data.toSyncTag();
        LogUtils.getLogger().info("RECHECK_DAMAGE_TRANSACTION {} rejected={} cp={}=>{} growth={}=>{} proficiencyBefore={} proficiencyAfter={}",
                label, rejected, before.getFloat("cp"), after.getFloat("cp"), before.getFloat("usage_max_cp"),
                after.getFloat("usage_max_cp"), before.getCompound("proficiency"), after.getCompound("proficiency"));
        check(rejected && before.equals(after), label + " changed ability resources/growth/proficiency despite rejected hurt: cp="
                + before.getFloat("cp") + "=>" + after.getFloat("cp") + ", growth="
                + before.getFloat("usage_max_cp") + "=>" + after.getFloat("usage_max_cp"));
    }

    public static final class ObserveDamage {
        private final UUID target;
        private final boolean deny;
        private int seen;
        private boolean canceled;
        ObserveDamage(ServerPlayer player, boolean deny) { target = player.getUUID(); this.deny = deny; }
        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void incoming(LivingIncomingDamageEvent event) {
            if (!event.getEntity().getUUID().equals(target)) return;
            seen++;
            if (deny) { event.setCanceled(true); canceled = true; }
        }
    }
    private static boolean near(float a, float b) { return Math.abs(a - b) < .01F; }
    private static void check(boolean condition, String message) { if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message); }
}

