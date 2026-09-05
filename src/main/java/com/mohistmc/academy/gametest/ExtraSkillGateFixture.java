package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.skill.*;
import com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime;
import com.mohistmc.academy.skill.ability.telekinesis.CruiseBombEffect;
import com.mohistmc.academy.skill.ability.telekinesis.PsychoTransmissionEffect;
import com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler;
import com.mohistmc.academy.world.AcademyItems;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-thread fixtures/observations for the explicitly enabled real-input client gate. */
@EventBusSubscriber(modid = AcademyCraft.MODID)
public final class ExtraSkillGateFixture {
    public static final String PROPERTY = "academy.extraSkillVisualGate";
    public static final List<String> CASES = List.of("volcanic_ball", "ascending_air", "air_blade",
            "airflow", "air_cooling", "air_wall", "air_jet", "offense_armour", "bomber_lance",
            "flying", "storm_core", "aero_separator", "psycho_throwing", "psycho_throwing_plain",
            "psycho_transmission", "psycho_needling", "insulation", "cruise_bomb",
            "overload_thinking", "perfect_paper", "psycho_slam", "psycho_harden",
            "liquid_shadow", "paper_drill");
    private static final Set<String> PASSIVES = Set.of("ascending_air", "airflow", "insulation", "perfect_paper");
    private static final Set<String> TOGGLES = Set.of("offense_armour", "flying", "psycho_transmission",
            "cruise_bomb", "psycho_harden", "liquid_shadow");
    public record Observation(String testCase, String skill, int age, boolean armed, boolean activated,
                              boolean sustained, boolean charging, float cp, float overload, int paper,
                              Vec3 targetCenter, String metrics, String failure) {}
    private static volatile Observation observation;
    private static Fixture fixture;
    private ExtraSkillGateFixture() {}
    public static Observation observation() { return observation; }
    public static boolean passive(String id) { return PASSIVES.contains(id); }
    public static boolean toggle(String id) { return TOGGLES.contains(id); }
    public static String skill(String id) { return id.equals("psycho_throwing_plain") ? "psycho_throwing" : id; }
    public static AbilityCategory category(String id) {
        return CASES.indexOf(id) < 12 ? AbilityCategory.AEROHAND : AbilityCategory.TELEKINESIS;
    }
    public static int duration(String id) {
        return switch (id) {
            case "storm_core" -> 115;
            case "ascending_air" -> 100;
            case "air_wall" -> 65;
            case "psycho_slam", "liquid_shadow" -> 55;
            case "airflow", "perfect_paper", "cruise_bomb", "offense_armour", "flying", "psycho_harden" -> 40;
            case "paper_drill" -> 25;
            default -> 18;
        };
    }
    private static final class Fixture {
        final ServerPlayer player;
        final String id, skill;
        final Vec3 origin;
        final List<Entity> entities = new ArrayList<>();
        Villager target;
        Entity projectile;
        ItemEntity incoming;
        boolean armed, activated, everSustained, everCharging, projectileGone, markedVictim, hardenHit;
        boolean flightGranted, slowApplied;
        int age, cooldownMax, paperMin = Integer.MAX_VALUE, airMin = 300, airMax, maxShadows;
        float cpStart, cpMin, cpMax, olStart, olMin, olMax, hpStart, hpMin, profStart;
        float targetStart, targetMin, targetEarly = Float.NaN, targetPreBurst = Float.NaN;
        double targetMove, playerMove, targetMaxY, closestCore = Double.MAX_VALUE, knockbackMax;
        float hardenDamage = Float.NaN, environmentDamage = Float.NaN;
        long gameStart;
        Fixture(ServerPlayer player, String id, Vec3 origin) {
            this.player = player; this.id = id; this.skill = skill(id); this.origin = origin;
        }
        PlayerAbilityData data() { return player.getData(AcademyAttachments.PLAYER_ABILITY); }
        boolean sustained() {
            return switch (id) {
                case "offense_armour" -> AeroPassiveRuntime.isOffenseArmourEngaged(player);
                case "flying" -> AeroPassiveRuntime.isFlyingActive(player);
                case "psycho_harden" -> TelekinesisPassiveHandler.isHardened(player);
                case "liquid_shadow" -> TelekinesisPassiveHandler.hasLiquidShadow(player);
                default -> false; // Transmission/Cruise termination uses their actual cooldown, not private maps.
            };
        }
    }

    public static void prepare(ServerPlayer player, String id) {
        require(Boolean.getBoolean(PROPERTY), "gate was not explicitly enabled");
        require(CASES.contains(id), "unknown case " + id);
        cleanup(player);
        var level = player.serverLevel();
        // Each case is separated by 64 blocks: old finite wall/storm/slam contexts cannot hit the next fixture.
        Vec3 origin = new Vec3(64 * CASES.indexOf(id) + .5, 81, .5);
        Fixture f = new Fixture(player, id, origin);
        fixture = f;
        BlockPos base = BlockPos.containing(origin);
        for (int x = -9; x <= 9; x++) for (int z = -5; z <= 22; z++) {
            level.setBlock(base.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 0; y < 8; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        player.closeContainer();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.getInventory().selected = 0;
        player.removeAllEffects();
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        player.setHealth(100);
        player.setAirSupply(300);
        player.getFoodData().setFoodLevel(20);
        player.invulnerableTime = 0;
        player.resetFallDistance();
        teleport(player, origin, id.equals("air_jet") ? -20 : 0);
        player.setDeltaMovement(Vec3.ZERO);
        var data = f.data();
        data.reset(); data.setDevMode(false); data.setCurrentAbility(category(id)); data.setPlayerLevel(5);
        data.learnSkill(f.skill); data.setProficiency(f.skill, id.equals("insulation") ? 1 : .5F);
        if (id.equals("paper_drill")) {
            data.learnSkill("perfect_paper"); data.setProficiency("perfect_paper", 1);
        }
        data.setCurrentPreset(0);
        if (!passive(id)) data.setSlot(0, id.equals("paper_drill") ? 0 : 2, f.skill);
        data.setAbilityActive(true); data.recalculateMaxResources(true);
        switch (id) {
            case "psycho_throwing" -> player.getInventory().setItem(1, new ItemStack(AcademyItems.ETCHED_COBBLESTONE.get(), 4));
            case "psycho_throwing_plain" -> player.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 4));
            case "psycho_needling" -> player.getInventory().setItem(1, new ItemStack(AcademyItems.NEEDLE.get(), 4));
            case "cruise_bomb", "liquid_shadow" -> player.getInventory().setItem(1, new ItemStack(Items.WATER_BUCKET));
            case "perfect_paper" -> player.getInventory().setItem(0, new ItemStack(Items.PAPER, 64));
            case "paper_drill" -> player.getInventory().setItem(1, new ItemStack(Items.PAPER, 64));
        }
        if (Set.of("volcanic_ball", "air_blade", "air_wall", "offense_armour", "bomber_lance", "storm_core",
                "aero_separator", "psycho_throwing", "psycho_throwing_plain", "psycho_needling", "cruise_bomb",
                "psycho_slam", "liquid_shadow", "paper_drill").contains(id)) {
            double distance = Set.of("air_wall", "offense_armour", "aero_separator", "cruise_bomb", "liquid_shadow").contains(id) ? 2.3
                    : id.equals("storm_core") ? 8 : 5;
            f.target = EntityType.VILLAGER.create(level);
            require(f.target != null, "could not create target");
            // NoAI skips ordinary mob travel and would freeze real knockback/lift.
            // Zero walking speed keeps the target still while normal travel applies external impulses.
            f.target.setNoAi(false);
            f.target.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            // Suppress vanilla hurt knockback so movement assertions require the skill's explicit impulse.
            f.target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            f.target.setSilent(true); f.target.setPersistenceRequired();
            f.target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500); f.target.setHealth(500);
            f.target.moveTo(origin.x, origin.y, origin.z + distance, 180, 0);
            require(level.addFreshEntity(f.target), "target spawn vetoed"); f.entities.add(f.target);
        }
        if (id.equals("air_wall") || id.equals("offense_armour")) {
            f.projectile = EntityType.ARROW.create(level);
            require(f.projectile != null, "could not create projectile");
            f.projectile.setPos(origin.x + 1, origin.y + 1.2, origin.z + 1);
            f.projectile.setNoGravity(true); f.projectile.setDeltaMovement(Vec3.ZERO);
            require(level.addFreshEntity(f.projectile), "projectile spawn vetoed"); f.entities.add(f.projectile);
        }
        if (id.equals("psycho_transmission")) {
            Vec3 at = player.getEyePosition().add(0, -.12, 5);
            f.incoming = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.DIAMOND, 3));
            f.incoming.setNoPickUpDelay(); f.incoming.setNoGravity(true); f.incoming.setDeltaMovement(Vec3.ZERO);
            require(level.addFreshEntity(f.incoming), "item spawn vetoed"); f.entities.add(f.incoming);
        }
        data.syncTo(player); player.inventoryMenu.broadcastChanges();
        publish(f, null);
    }

    /** Arms measurements; only the four labeled passive cases trigger scripted environments here. */
    public static void arm(ServerPlayer player) {
        Fixture f = current(player); var data = f.data();
        data.setCurrentCp(f.id.equals("overload_thinking") ? 1000 : data.getMaxCp());
        data.setCurrentOverload(f.id.equals("air_cooling") ? 300 : 0);
        f.cpStart = f.cpMin = f.cpMax = data.getCurrentCp();
        f.olStart = f.olMin = f.olMax = data.getCurrentOverload();
        f.hpStart = f.hpMin = player.getHealth(); f.profStart = data.getProficiency(f.skill);
        f.targetStart = f.targetMin = f.target == null ? 0 : f.target.getHealth();
        f.targetMaxY = f.target == null ? 0 : f.target.getY();
        f.gameStart = player.serverLevel().getGameTime(); f.armed = true;
        data.syncTo(player);
        switch (f.id) {
            case "ascending_air" -> {
                // Actual server teleport to a height, followed by vanilla falling/movement and landing.
                teleport(player, f.origin.add(0, 22, 0), 35); player.resetFallDistance();
            }
            case "airflow" -> {
                BlockPos base = BlockPos.containing(f.origin);
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                    for (int y = 0; y < 3; y++) player.serverLevel().setBlock(base.offset(x,y,z), Blocks.WATER.defaultBlockState(), 3);
                player.setAirSupply(20);
            }
            case "insulation" -> {
                boolean acPvp = ACConfig.Server.PVP_ENABLED.get();
                boolean vanillaPvp = player.serverLevel().getServer().isPvpAllowed();
                try {
                    ACConfig.Server.PVP_ENABLED.set(true); player.serverLevel().getServer().setPvpAllowed(true);
                    var attacker = FakePlayerFactory.get(player.serverLevel(), new GameProfile(
                            UUID.randomUUID(), "ExtraGateAttacker"));
                    attacker.getData(AcademyAttachments.PLAYER_ABILITY).setCurrentAbility(AbilityCategory.ELECTROMASTER);
                    player.invulnerableTime = 0;
                    require(AcademyDamageHelper.hurt(attacker, player, attacker.damageSources().playerAttack(attacker), 10),
                            "ability-tagged fixture damage did not reach the real player");
                    f.environmentDamage = f.hpStart - player.getHealth();
                } finally { player.serverLevel().getServer().setPvpAllowed(vanillaPvp); ACConfig.Server.PVP_ENABLED.set(acPvp); }
            }
            case "perfect_paper" -> { /* Real held paper and normal PlayerTickEvent train the passive. */ }
        }
        sample(f); publish(f, null);
    }

    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if (!Boolean.getBoolean(PROPERTY) || fixture == null || event.getEntity() != fixture.player || !fixture.armed) return;
        Fixture f = fixture;
        try {
            f.age = (int) (f.player.serverLevel().getGameTime() - f.gameStart);
            if (f.id.equals("psycho_harden") && f.sustained() && !f.hardenHit && f.age >= 8) {
                f.hardenHit = true; float hp = f.player.getHealth(); f.player.invulnerableTime = 0;
                // Explicit scripted self-damage follows the same Academy damage boundary as production skills.
                AcademyDamageHelper.hurtSelf(f.player, f.player, f.player.damageSources().generic(), 10);
                f.hardenDamage = hp - f.player.getHealth();
            }
            if (f.id.equals("liquid_shadow") && f.sustained() && !f.markedVictim) {
                // This marks the target for the real follower AI; it is not a direct shadow effect call.
                f.player.setLastHurtMob(f.target); f.markedVictim = true;
            }
            sample(f); publish(f, null);
        } catch (Throwable failure) { publish(f, failure.toString()); f.armed = false; }
    }
    private static void sample(Fixture f) {
        var data = f.data(); var player = f.player;
        f.cpMin = Math.min(f.cpMin, data.getCurrentCp()); f.cpMax = Math.max(f.cpMax, data.getCurrentCp());
        f.olMin = Math.min(f.olMin, data.getCurrentOverload()); f.olMax = Math.max(f.olMax, data.getCurrentOverload());
        f.hpMin = Math.min(f.hpMin, player.getHealth()); f.airMin = Math.min(f.airMin, player.getAirSupply());
        f.airMax = Math.max(f.airMax, player.getAirSupply());
        f.paperMin = Math.min(f.paperMin, count(player, Items.PAPER));
        f.cooldownMax = Math.max(f.cooldownMax, data.getCooldownTicks(f.skill));
        f.everSustained |= f.sustained(); f.everCharging |= SkillChargingManager.isCharging(player.getUUID());
        f.flightGranted |= player.getAbilities().mayfly && player.getAbilities().flying;
        f.slowApplied |= player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
        f.knockbackMax = Math.max(f.knockbackMax, player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        f.playerMove = Math.max(f.playerMove, player.position().distanceTo(f.origin));
        if (f.projectile != null) f.projectileGone |= !f.projectile.isAlive();
        if (f.target != null) {
            f.targetMin = Math.min(f.targetMin, f.target.getHealth());
            f.targetMove = Math.max(f.targetMove, f.target.position().distanceTo(new Vec3(f.origin.x, f.origin.y,
                    f.origin.z + (Set.of("air_wall", "offense_armour", "aero_separator", "cruise_bomb", "liquid_shadow").contains(f.id) ? 2.3 : f.id.equals("storm_core") ? 8 : 5))));
            f.targetMaxY = Math.max(f.targetMaxY, f.target.getY());
            f.closestCore = Math.min(f.closestCore, f.target.position().distanceTo(f.origin.add(0, 1.62, 6)));
            if (f.age >= 10 && Float.isNaN(f.targetEarly)) f.targetEarly = f.target.getHealth();
            if (f.age >= 65 && Float.isNaN(f.targetPreBurst)) f.targetPreBurst = f.target.getHealth();
        }
        if (f.id.equals("liquid_shadow")) {
            var shadows = player.serverLevel().getEntitiesOfClass(Drowned.class, player.getBoundingBox().inflate(16),
                    e -> e.getTags().contains("academy_liquid_shadow_owner_" + player.getUUID()));
            f.maxShadows = Math.max(f.maxShadows, shadows.size());
            for (Drowned shadow : shadows) require(Math.abs(shadow.getMaxHealth() - 100) < .01, "shadow max health differed");
        }
        f.activated |= switch (f.id) {
            case "perfect_paper" -> data.getProficiency(f.skill) > f.profStart;
            case "overload_thinking" -> f.cpMax > f.cpStart + 1000 && f.olMax > 40;
            case "airflow" -> f.airMax > 250 && f.cpMin < f.cpStart - 20;
            case "ascending_air", "insulation" -> f.hpMin < f.hpStart && f.cpMin < f.cpStart - 20;
            case "paper_drill" -> f.everCharging && f.paperMin == 0 && f.cpMin < f.cpStart - 2500;
            default -> f.cpMin < f.cpStart - 10 || f.olMax > f.olStart + 10;
        };
    }

    /** Called after the real key release/second toggle, before fixture cleanup. */
    public static String finish(ServerPlayer player) {
        Fixture f = current(player); sample(f); var data = f.data();
        require(player.isAlive() && !data.isDevMode() && data.isAbilityActive(), "invalid/dead fixture or dev-mode bypass");
        require(f.activated, "production input/environment never activated: " + metrics(f));
        if (!passive(f.id) && !toggle(f.id) && !f.id.equals("paper_drill")) require(f.cooldownMax > 0, "no authoritative cooldown");
        switch (f.id) {
            case "volcanic_ball", "air_blade", "bomber_lance" -> require(f.targetMin < f.targetStart, "ray did not hurt aligned target");
            case "ascending_air" -> require(player.onGround() && f.hpStart-f.hpMin > 0 && f.hpStart-f.hpMin <= 7.55, "real landing was not capped at p=.5");
            case "airflow" -> require(f.airMin <= 20 && f.airMax >= 290 && f.cpMin < f.cpStart-20, "underwater low air was not replenished with CP");
            case "air_cooling" -> require(f.olMin < 160 && player.getHealth() <= f.hpStart, "cooling did not lower overload without healing");
            case "air_wall" -> require(f.targetMin < f.targetStart && f.targetMove > .3 && f.projectileGone, "expanding wall damage/push/projectile clearing missing");
            case "air_jet" -> require(f.playerMove > .5, "jet did not move the authoritative player");
            case "offense_armour" -> require(f.everSustained && !f.sustained() && f.knockbackMax >= .89
                    && f.targetMin < f.targetStart && f.projectileGone, "armour defense/contact/toggle missing");
            case "flying" -> require(f.everSustained && f.flightGranted && f.playerMove > .3 && !f.sustained()
                    && !player.getAbilities().mayfly && !player.getAbilities().flying, "survival flight or key-off revocation missing");
            case "storm_core" -> require(f.age >= 105 && f.closestCore < 2 && f.targetMin < f.targetPreBurst - 10,
                    "storm attraction/late burst missing");
            case "aero_separator" -> require(f.hpStart-f.hpMin >= 49 && f.airMin <= 4 && f.targetMin < f.targetStart,
                    "vacuum did not damage self/target and remove air");
            case "psycho_throwing", "psycho_throwing_plain" -> {
                Item ammo = f.id.equals("psycho_throwing") ? AcademyItems.ETCHED_COBBLESTONE.get() : Items.COBBLESTONE;
                require(count(player, ammo) == 3 && f.targetMin < f.targetStart && f.targetMove > .3,
                        "throwing ammo/damage/push missing");
                require(hasDropped(f, ammo), "thrown stone did not return as an item");
            }
            case "psycho_transmission" -> require(count(player, Items.DIAMOND) == 3 && !f.incoming.isAlive()
                    && data.getCooldownTicks(f.skill) > 0, "sighted item was not transmitted or key-off cooldown missing");
            case "psycho_needling" -> require(count(player, AcademyItems.NEEDLE.get()) == 3
                    && f.targetMin < f.targetStart && hasDropped(f, AcademyItems.NEEDLE.get()), "needle cost/hit/drop missing");
            case "insulation" -> require(Math.abs(f.environmentDamage-6) < .05 && f.cpMin < f.cpStart-900,
                    "ability-tagged Electromaster insulation expected 6 health/1000 CP");
            case "cruise_bomb" -> require(count(player, Items.WATER_BUCKET) == 0 && count(player, Items.BUCKET) == 1
                    && f.targetMin < f.targetStart && data.getCooldownTicks(f.skill) > 0, "cruise material/acquisition/hit/toggle missing");
            case "overload_thinking" -> require(f.cpMax > f.cpStart+1000 && f.olMax >= 80, "thinking conversion missing");
            case "perfect_paper" -> require(player.getMainHandItem().is(Items.PAPER)
                    && data.getProficiency(f.skill) > f.profStart, "held-paper training missing");
            case "psycho_slam" -> require(f.targetMaxY > f.origin.y+.25 && f.targetMin < f.targetEarly-10,
                    "slam lift/second damage missing");
            case "psycho_harden" -> require(f.everSustained && f.slowApplied && f.hardenHit && f.hardenDamage == 0
                    && f.olMax >= 75 && !f.sustained(), "harden maintenance/blocked damage/second toggle missing");
            case "liquid_shadow" -> require(f.everSustained && f.maxShadows == 1 && f.targetMin < f.targetStart
                    && count(player, Items.WATER_BUCKET) == 0 && count(player, Items.BUCKET) == 1 && !f.sustained(),
                    "single shadow/material/attack/toggle missing");
            case "paper_drill" -> require(f.everCharging && f.paperMin == 0 && count(player, Items.PAPER) == 64
                    && f.targetMin < f.targetStart && !SkillChargingManager.isCharging(player.getUUID()),
                    "charging ack/64-paper consume/target pulse/keyup return missing");
        }
        f.armed = false; publish(f, null);
        return "PASS " + f.id + " path=" + (passive(f.id) ? "environment-fixture" : f.id.equals("paper_drill")
                ? "client-keydown/charging-ack/keyup" : "client-key-edge") + " " + metrics(f);
    }
    private static boolean hasDropped(Fixture f, Item item) {
        return !f.player.serverLevel().getEntitiesOfClass(ItemEntity.class,
                f.player.getBoundingBox().inflate(45), e -> e.getItem().is(item)).isEmpty();
    }
    public static void cleanup(ServerPlayer player) {
        SkillChargingManager.cancel(player);
        AeroPassiveRuntime.terminateSustained(player, player.getData(AcademyAttachments.PLAYER_ABILITY));
        var logout = new PlayerEvent.PlayerLoggedOutEvent(player);
        TelekinesisPassiveHandler.logout(logout); CruiseBombEffect.logout(logout); PsychoTransmissionEffect.logout(logout);
        if (fixture != null) for (Entity entity : fixture.entities) if (!entity.isRemoved()) entity.discard();
        fixture = null;
    }
    private static Fixture current(ServerPlayer player) {
        require(fixture != null && fixture.player == player, "fixture/player changed"); return fixture;
    }
    private static int count(ServerPlayer player, Item item) {
        int result = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot); if (stack.is(item)) result += stack.getCount();
        }
        return result;
    }
    private static void teleport(ServerPlayer player, Vec3 at, float pitch) {
        player.teleportTo(player.serverLevel(), at.x, at.y, at.z, Set.of(), 0, pitch);
    }
    private static void publish(Fixture f, String failure) {
        observation = new Observation(f.id, f.skill, f.age, f.armed, f.activated, f.sustained(),
                SkillChargingManager.isCharging(f.player.getUUID()), f.data().getCurrentCp(),
                f.data().getCurrentOverload(), count(f.player, Items.PAPER),
                f.target == null ? null : f.target.position().add(0, f.target.getBbHeight() * .5, 0), metrics(f), failure);
    }
    private static String metrics(Fixture f) {
        return String.format(Locale.ROOT,
                "age=%d category=%s level=5 proficiency=%.5f->%.5f cp=%.2f[min=%.2f,max=%.2f,current=%.2f] ol=%.2f[min=%.2f,max=%.2f,current=%.2f] cooldownMax=%d hp=%.2f->%.2f target=%.2f->%.2f targetMove=%.3f targetMaxY=%.3f playerMove=%.3f air=%d..%d paperMin=%d paperNow=%d sustained=%s charging=%s projectilesCleared=%s shadowsMax=%d",
                f.age, category(f.id), f.profStart, f.data().getProficiency(f.skill), f.cpStart, f.cpMin, f.cpMax,
                f.data().getCurrentCp(), f.olStart, f.olMin, f.olMax, f.data().getCurrentOverload(), f.cooldownMax,
                f.hpStart, f.hpMin, f.targetStart, f.targetMin, f.targetMove, f.targetMaxY, f.playerMove,
                f.airMin, f.airMax, f.paperMin, count(f.player, Items.PAPER), f.sustained(),
                SkillChargingManager.isCharging(f.player.getUUID()), f.projectileGone, f.maxShadows);
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
}