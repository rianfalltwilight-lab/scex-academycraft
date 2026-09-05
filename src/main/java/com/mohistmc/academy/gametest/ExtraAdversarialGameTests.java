package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AbilityInterferenceService;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.ability.aerohand.AeroPassiveRuntime;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.entity.AbilityInterfererBlockEntity;
import com.mohistmc.academy.skill.SkillEffect;
import com.mohistmc.academy.skill.ability.aerohand.AirBladeEffect;
import com.mohistmc.academy.skill.ability.aerohand.BomberLanceEffect;
import com.mohistmc.academy.skill.ability.aerohand.VolcanicBallEffect;
import com.mohistmc.academy.skill.ability.telekinesis.PaperDrillEffect;
import com.mohistmc.academy.skill.ability.telekinesis.PsychoNeedlingEffect;
import com.mohistmc.academy.skill.ability.telekinesis.PsychoThrowingEffect;
import com.mohistmc.academy.skill.ability.telekinesis.PsychoSlamEffect;
import com.mohistmc.academy.skill.ability.telekinesis.TelekinesisPassiveHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side counterexamples; these assertions do not certify client visuals. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ExtraAdversarialGameTests {
    private ExtraAdversarialGameTests() {}

    @GameTest(template = "empty")
    public static void interferenceRevokesArmourAndDoesNotResumeIt(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(), "[ArmourRedTeam]"));
        var pos = helper.absolutePos(new BlockPos(3, 2, 3));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(pos.getX() + 2.5, pos.getY() + .5, pos.getZ() + .5);
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.AEROHAND);
        data.setPlayerLevel(5);
        data.learnSkill("offense_armour");
        data.setAbilityActive(true);
        data.setDevMode(true);
        double resistance = player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        try {
            if (!AeroPassiveRuntime.toggleOffenseArmour(player, data)
                    || player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) <= resistance) {
                helper.fail("armour fixture did not engage"); return;
            }
            level.setBlock(pos, AcademyBlocks.ABILITY_INTERFERER.get().defaultBlockState(), 3);
            var machine = (AbilityInterfererBlockEntity) level.getBlockEntity(pos);
            machine.setEnergy(1000);
            machine.setEnabled(true);
            machine.serverTick();
            if (!AbilityInterferenceService.isInterfered(player)) {
                helper.fail("interferer fixture did not suppress the player"); return;
            }
            AeroPassiveRuntime.playerTick(new PlayerTickEvent.Post(player));
            if (Math.abs(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - resistance) > 1e-6) {
                helper.fail("interference retained the paid armour knockback modifier"); return;
            }
            machine.setEnabled(false);
            AbilityInterferenceService.remove(machine);
            AeroPassiveRuntime.playerTick(new PlayerTickEvent.Post(player));
            if (AeroPassiveRuntime.isOffenseArmourEngaged(player)) {
                helper.fail("armour resumed after interference without a new activation"); return;
            }
            helper.succeed();
        } finally {
            AeroPassiveRuntime.terminateSustained(player, data);
            if (level.getBlockEntity(pos) instanceof AbilityInterfererBlockEntity machine)
                AbilityInterferenceService.remove(machine);
        }
    }

    private static ServerPlayer caster(GameTestHelper helper, AbilityCategory category) {
        var player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "[ExtraRedTeam]"));
        player.setGameMode(GameType.SURVIVAL);
        var origin = helper.absolutePos(BlockPos.ZERO);
        player.setPos(origin.getX() + 2.5, origin.getY() + 2, origin.getZ() + 1.5);
        player.setYRot(0);
        player.setXRot(0);
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(category);
        data.setPlayerLevel(5);
        data.setAbilityActive(true);
        data.setDevMode(true);
        return player;
    }

    private static <T extends Mob> T mob(GameTestHelper helper, EntityType<T> type, double z) {
        T mob = type.create(helper.getLevel());
        var origin = helper.absolutePos(BlockPos.ZERO);
        mob.setPos(origin.getX() + 2.5, origin.getY() + 2, origin.getZ() + z);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        mob.setHealth(200);
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    @GameTest(template = "empty") public static void interferenceDoesNotResumeFlight(GameTestHelper helper) {
        var player = caster(helper, AbilityCategory.AEROHAND);
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.learnSkill("flying");
        var pos = helper.absolutePos(new BlockPos(3, 2, 3));
        try {
            if (!AeroPassiveRuntime.toggleFlying(player, data) || !player.getAbilities().mayfly) {
                helper.fail("flight fixture did not engage"); return;
            }
            helper.getLevel().setBlock(pos, AcademyBlocks.ABILITY_INTERFERER.get().defaultBlockState(), 3);
            var machine = (AbilityInterfererBlockEntity) helper.getLevel().getBlockEntity(pos);
            machine.setEnergy(1000);
            machine.setEnabled(true);
            machine.serverTick();
            if (!AbilityInterferenceService.isInterfered(player)) {
                helper.fail("interference fixture is inactive"); return;
            }
            AeroPassiveRuntime.playerTick(new PlayerTickEvent.Post(player));
            if (player.getAbilities().mayfly || player.getAbilities().flying) {
                helper.fail("interference retained skill-granted flight"); return;
            }
            machine.setEnabled(false);
            AbilityInterferenceService.remove(machine);
            AeroPassiveRuntime.playerTick(new PlayerTickEvent.Post(player));
            if (AeroPassiveRuntime.isFlyingActive(player) || player.getAbilities().mayfly) {
                helper.fail("flight resumed after interference without a fresh activation"); return;
            }
            helper.succeed();
        } finally {
            AeroPassiveRuntime.terminateSustained(player, data);
            if (helper.getLevel().getBlockEntity(pos) instanceof AbilityInterfererBlockEntity machine)
                AbilityInterferenceService.remove(machine);
        }
    }

    @GameTest(template = "empty") public static void unlearningArmourClearsModifierAndSession(GameTestHelper helper) {
        var player = caster(helper, AbilityCategory.AEROHAND);
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        // Dev mode intentionally grants every skill; exercise real learned-skill ownership.
        data.setDevMode(false);
        data.learnSkill("offense_armour");
        double original = player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        try {
            if (!AeroPassiveRuntime.toggleOffenseArmour(player, data)) {
                helper.fail("armour fixture did not engage"); return;
            }
            data.unlearnSkill("offense_armour");
            AeroPassiveRuntime.playerTick(new PlayerTickEvent.Post(player));
            if (Math.abs(original - player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) > 1e-6) {
                helper.fail("unlearned armour retained its attribute modifier"); return;
            }
            data.learnSkill("offense_armour");
            AeroPassiveRuntime.playerTick(new PlayerTickEvent.Post(player));
            if (AeroPassiveRuntime.isOffenseArmourEngaged(player)) {
                helper.fail("relearning armour resumed the stale session"); return;
            }
            helper.succeed();
        } finally { AeroPassiveRuntime.terminateSustained(player, data); }
    }

    @GameTest(template = "empty") public static void unlearningFlightClearsSession(GameTestHelper helper) {
        var player = caster(helper, AbilityCategory.AEROHAND);
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        // Dev mode intentionally grants every skill; exercise real learned-skill ownership.
        data.setDevMode(false);
        data.learnSkill("flying");
        try {
            if (!AeroPassiveRuntime.toggleFlying(player, data)) {
                helper.fail("flight fixture did not engage"); return;
            }
            data.unlearnSkill("flying");
            AeroPassiveRuntime.playerTick(new PlayerTickEvent.Post(player));
            data.learnSkill("flying");
            AeroPassiveRuntime.playerTick(new PlayerTickEvent.Post(player));
            if (AeroPassiveRuntime.isFlyingActive(player) || player.getAbilities().mayfly) {
                helper.fail("relearning flying resumed the stale session"); return;
            }
            helper.succeed();
        } finally { AeroPassiveRuntime.terminateSustained(player, data); }
    }

    private static void thinWall(GameTestHelper helper, SkillEffect effect) {
        var player = caster(helper, effect instanceof AirBladeEffect || effect instanceof BomberLanceEffect
                || effect instanceof VolcanicBallEffect ? AbilityCategory.AEROHAND : AbilityCategory.TELEKINESIS);
        var victim = mob(helper, EntityType.VILLAGER, 3.90);
        // Real front face is z=3.60; the isolated pane ends at z=3.5625.
        helper.setBlock(new BlockPos(2, 3, 3), Blocks.GLASS_PANE);
        float before = victim.getHealth();
        try {
            var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            if (effect instanceof PaperDrillEffect drill) {
                data.learnSkill("perfect_paper");
                player.getInventory().add(new ItemStack(Items.PAPER, 64));
                drill.onChargingStart(player, data);
                if (!drill.onChargingTick(player, data, 5)) {
                    helper.fail("drill fixture did not start"); return;
                }
            } else effect.execute(player, data);
            if (victim.getHealth() != before) {
                helper.fail(effect.getId() + " damaged a real hitbox entirely behind a glass pane"); return;
            }
            helper.succeed();
        } finally {
            if (effect instanceof PaperDrillEffect drill)
                drill.onChargingAbort(player, player.getData(AcademyAttachments.PLAYER_ABILITY));
            victim.discard();
        }
    }

    @GameTest(template = "empty") public static void airBladeStopsAtThinWall(GameTestHelper h) { thinWall(h, new AirBladeEffect()); }
    @GameTest(template = "empty") public static void bomberLanceStopsAtThinWall(GameTestHelper h) { thinWall(h, new BomberLanceEffect()); }
    @GameTest(template = "empty") public static void volcanicStopsAtThinWall(GameTestHelper h) { thinWall(h, new VolcanicBallEffect()); }
    @GameTest(template = "empty") public static void needlingStopsAtThinWall(GameTestHelper h) { thinWall(h, new PsychoNeedlingEffect()); }
    @GameTest(template = "empty") public static void throwingStopsAtThinWall(GameTestHelper h) { thinWall(h, new PsychoThrowingEffect()); }
    @GameTest(template = "empty") public static void slamStopsAtThinWall(GameTestHelper h) { thinWall(h, new PsychoSlamEffect()); }
    @GameTest(template = "empty") public static void paperDrillStopsAtThinWall(GameTestHelper h) { thinWall(h, new PaperDrillEffect()); }

    private static void firstIntersection(GameTestHelper helper, SkillEffect effect) {
        var player = caster(helper, effect instanceof AirBladeEffect || effect instanceof BomberLanceEffect
                || effect instanceof VolcanicBallEffect ? AbilityCategory.AEROHAND : AbilityCategory.TELEKINESIS);
        var small = mob(helper, EntityType.VILLAGER, 5.6);
        var large = mob(helper, EntityType.GHAST, 7);
        try {
            var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            data.setProficiency(effect.getId(), 1);
            effect.execute(player, data);
            if (small.getHealth() != 200 || large.getHealth() >= 200) {
                helper.fail(effect.getId() + " chose entity origin instead of the first ray/real-box intersection"); return;
            }
            helper.succeed();
        } finally { small.discard(); large.discard(); }
    }

    @GameTest(template = "empty") public static void airBladeHitsFirstSurface(GameTestHelper h) { firstIntersection(h, new AirBladeEffect()); }
    @GameTest(template = "empty") public static void bomberLanceHitsFirstSurface(GameTestHelper h) { firstIntersection(h, new BomberLanceEffect()); }
    @GameTest(template = "empty") public static void volcanicHitsFirstSurface(GameTestHelper h) { firstIntersection(h, new VolcanicBallEffect()); }
    @GameTest(template = "empty") public static void needlingHitsFirstSurface(GameTestHelper h) { firstIntersection(h, new PsychoNeedlingEffect()); }
    @GameTest(template = "empty") public static void throwingHitsFirstSurface(GameTestHelper h) { firstIntersection(h, new PsychoThrowingEffect()); }
    @GameTest(template = "empty") public static void slamHitsFirstSurface(GameTestHelper h) { firstIntersection(h, new PsychoSlamEffect()); }

    private static Drowned summonShadow(GameTestHelper helper, ServerPlayer player) {
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.learnSkill("liquid_shadow");
        player.getInventory().add(new ItemStack(Items.WATER_BUCKET));
        if (!TelekinesisPassiveHandler.toggleLiquidShadow(player))
            throw new IllegalStateException("shadow fixture did not summon");
        return helper.getLevel().getEntitiesOfClass(Drowned.class, player.getBoundingBox().inflate(8),
                e -> e.getTags().contains("academy_liquid_shadow_owner_" + player.getUUID())).getFirst();
    }

    @GameTest(template = "empty") public static void liquidShadowCannotAttackWithoutPaying(GameTestHelper helper) {
        var player = caster(helper, AbilityCategory.TELEKINESIS);
        var shadow = summonShadow(helper, player);
        var victim = mob(helper, EntityType.VILLAGER, 3.9);
        shadow.setPos(victim.position().add(0, 0, -1));
        player.setLastHurtMob(victim);
        player.tickCount = 20;
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setDevMode(false);
        data.setProficiency("liquid_shadow", 0);
        data.setCurrentCp(50);
        try {
            TelekinesisPassiveHandler.playerTick(new PlayerTickEvent.Post(player));
            if (victim.getHealth() != 200) {
                helper.fail("liquid shadow hurt before checking the 100 CP attack debit at only 50 CP"); return;
            }
            helper.succeed();
        } finally {
            TelekinesisPassiveHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(player)); victim.discard();
        }
    }

    @GameTest(template = "empty") public static void liquidShadowCannotMeleeThroughThinWall(GameTestHelper helper) {
        var player = caster(helper, AbilityCategory.TELEKINESIS);
        var shadow = summonShadow(helper, player);
        var victim = mob(helper, EntityType.VILLAGER, 3.9);
        shadow.setPos(victim.position().add(0, 0, -1));
        helper.setBlock(new BlockPos(2, 3, 3), Blocks.GLASS_PANE);
        player.setLastHurtMob(victim);
        player.tickCount = 20;
        try {
            TelekinesisPassiveHandler.playerTick(new PlayerTickEvent.Post(player));
            if (victim.getHealth() != 200) {
                helper.fail("liquid shadow performed melee through a glass pane"); return;
            }
            helper.succeed();
        } finally {
            TelekinesisPassiveHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(player)); victim.discard();
        }
    }

    @GameTest(template = "empty") public static void liquidShadowIgnoresOldDimensionVictim(GameTestHelper helper) {
        var player = caster(helper, AbilityCategory.TELEKINESIS);
        var shadow = summonShadow(helper, player);
        var other = helper.getLevel().getServer().getLevel(Level.NETHER);
        if (other == null) { helper.fail("nether fixture is unavailable"); return; }
        var victim = EntityType.VILLAGER.create(other);
        victim.setPos(player.position().add(0, 0, 2.4));
        victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        victim.setHealth(200);
        shadow.setPos(victim.position().add(0, 0, -1));
        player.setLastHurtMob(victim);
        player.tickCount = 20;
        try {
            TelekinesisPassiveHandler.playerTick(new PlayerTickEvent.Post(player));
            if (victim.getHealth() != 200) {
                helper.fail("liquid shadow attacked a cached target belonging to another dimension"); return;
            }
            helper.succeed();
        } finally {
            TelekinesisPassiveHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(player)); victim.discard();
        }
    }

    @GameTest(template = "empty") public static void liquidShadowSurvivesPeaceful(GameTestHelper helper) {
        var player = caster(helper, AbilityCategory.TELEKINESIS);
        var shadow = summonShadow(helper, player);
        var server = helper.getLevel().getServer();
        var previous = helper.getLevel().getDifficulty();
        try {
            server.setDifficulty(Difficulty.PEACEFUL, true);
            shadow.checkDespawn();
            if (!shadow.isAlive()) { helper.fail("peaceful difficulty deletes a paid liquid shadow"); return; }
            helper.succeed();
        } finally {
            server.setDifficulty(previous, true);
            TelekinesisPassiveHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
        }
    }

    @GameTest(template = "empty") public static void liquidShadowCannotLeaveSavedNoAiMob(GameTestHelper helper) {
        var player = caster(helper, AbilityCategory.TELEKINESIS);
        var shadow = summonShadow(helper, player);
        try {
            if (shadow.shouldBeSaved() || shadow.save(new CompoundTag()) || shadow.saveAsPassenger(new CompoundTag())) {
                helper.fail("temporary liquid shadow persists as a no-AI monster without its live owner session"); return;
            }
            helper.succeed();
        } finally { TelekinesisPassiveHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(player)); }
    }

    @GameTest(template = "empty") public static void legacyOrphanShadowCannotReload(GameTestHelper helper) {
        var orphan = EntityType.DROWNED.create(helper.getLevel());
        orphan.setNoAi(true);
        orphan.addTag("academy_liquid_shadow");
        orphan.addTag("academy_liquid_shadow_owner_" + UUID.randomUUID());
        var join = new EntityJoinLevelEvent(orphan, helper.getLevel(), true);
        NeoForge.EVENT_BUS.post(join);
        if (!join.isCanceled()) {
            helper.fail("an orphan saved legacy shadow was allowed back into the live level"); return;
        }
        // Normal Drowned entities must never be caught by the migration filter.
        var normal = EntityType.DROWNED.create(helper.getLevel());
        var normalJoin = new EntityJoinLevelEvent(normal, helper.getLevel(), true);
        NeoForge.EVENT_BUS.post(normalJoin);
        if (normalJoin.isCanceled()) { helper.fail("ordinary drowned was rejected as a shadow"); return; }
        helper.succeed();
    }

    @GameTest(template = "empty") public static void liquidShadowPaysForAVisibleAttack(GameTestHelper helper) {
        var player = caster(helper, AbilityCategory.TELEKINESIS);
        var shadow = summonShadow(helper, player);
        var victim = mob(helper, EntityType.VILLAGER, 3.9);
        shadow.setPos(victim.position().add(0, 0, -1));
        player.setLastHurtMob(victim);
        player.tickCount = 20;
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setDevMode(false);
        data.setProficiency("liquid_shadow", 0);
        data.setCurrentCp(101);
        try {
            TelekinesisPassiveHandler.playerTick(new PlayerTickEvent.Post(player));
            if (Math.abs(victim.getHealth() - 180) > 1e-4 || Math.abs(data.getCurrentCp()) > 1e-4) {
                helper.fail("visible shadow attack must deal 20 damage and debit 1 upkeep + 100 attack CP"); return;
            }
            helper.succeed();
        } finally {
            TelekinesisPassiveHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(player)); victim.discard();
        }
    }

    @GameTest(template = "empty") public static void liquidShadowCannotTeleportOntoItsVictim(GameTestHelper helper) {
        var player = caster(helper, AbilityCategory.TELEKINESIS);
        var shadow = summonShadow(helper, player);
        var victim = mob(helper, EntityType.VILLAGER, 3.9);
        shadow.setPos(player.position().add(0, 0, -10));
        player.setLastHurtMob(victim);
        player.tickCount = 20;
        try {
            TelekinesisPassiveHandler.playerTick(new PlayerTickEvent.Post(player));
            if (victim.getHealth() != 200 || shadow.distanceToSqr(player) > 16) {
                helper.fail("catch-up must return to the owner without teleporting into a melee hit"); return;
            }
            helper.succeed();
        } finally {
            TelekinesisPassiveHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(player)); victim.discard();
        }
    }

    @GameTest(template = "empty") public static void liquidShadowEndsOnEntityUnload(GameTestHelper helper) {
        var player = caster(helper, AbilityCategory.TELEKINESIS);
        var shadow = summonShadow(helper, player);
        try {
            shadow.setRemoved(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            if (TelekinesisPassiveHandler.hasLiquidShadow(player)) {
                helper.fail("unloaded shadow retained an active owner session"); return;
            }
            if (shadow.shouldBeSaved()) {
                helper.fail("unloaded shadow remained eligible for chunk serialization"); return;
            }
            helper.succeed();
        } finally { TelekinesisPassiveHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(player)); }
    }

    @GameTest(template = "empty") public static void ownerTagsCannotFabricateAnActiveShadow(GameTestHelper helper) {
        var player = caster(helper, AbilityCategory.TELEKINESIS);
        var orphan = mob(helper, EntityType.DROWNED, 3);
        orphan.addTag("academy_liquid_shadow");
        orphan.addTag("academy_liquid_shadow_owner_" + player.getUUID());
        try {
            // No session was ever created. Nearby tags alone must not be recovered as a cast.
            if (TelekinesisPassiveHandler.hasLiquidShadow(player)) {
                helper.fail("a radius scan promoted arbitrary tagged Drowned into a paid skill session"); return;
            }
            TelekinesisPassiveHandler.playerTick(new PlayerTickEvent.Post(player));
            if (!orphan.isAlive()) {
                helper.fail("a player without a session manipulated an unrelated tagged entity"); return;
            }
            helper.succeed();
        } finally {
            TelekinesisPassiveHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(player)); orphan.discard();
        }
    }

    @GameTest(template = "empty") public static void dimensionEventDiscardsTheOldShadow(GameTestHelper helper) {
        var player = caster(helper, AbilityCategory.TELEKINESIS);
        var shadow = summonShadow(helper, player);
        try {
            TelekinesisPassiveHandler.dimension(new PlayerEvent.PlayerChangedDimensionEvent(
                    player, helper.getLevel().dimension(), Level.NETHER));
            if (shadow.isAlive() || TelekinesisPassiveHandler.hasLiquidShadow(player)) {
                helper.fail("dimension event retained an old-world shadow or owner session"); return;
            }
            helper.succeed();
        } finally { TelekinesisPassiveHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(player)); }
    }

    @GameTest(template = "empty") public static void rejectedShadowDamageDoesNotRefundPaidGrowth(GameTestHelper helper) {
        var player = caster(helper, AbilityCategory.TELEKINESIS);
        var shadow = summonShadow(helper, player);
        var victim = mob(helper, EntityType.VILLAGER, 3.9);
        victim.setInvulnerable(true);
        shadow.setPos(victim.position().add(0, 0, -1));
        player.setLastHurtMob(victim);
        player.tickCount = 20;
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setDevMode(false);
        data.setProficiency("liquid_shadow", 0);
        data.setCurrentCp(101);
        try {
            TelekinesisPassiveHandler.playerTick(new PlayerTickEvent.Post(player));
            if (victim.getHealth() != 200 || Math.abs(data.getCurrentCp()) > 1e-4) {
                helper.fail("a rejected shadow attack must retain its paid debit, not refund CP while keeping usage growth"); return;
            }
            helper.succeed();
        } finally {
            TelekinesisPassiveHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(player)); victim.discard();
        }
    }

    @GameTest(template = "empty") public static void deniedShadowSpawnDoesNotDebitResourcesOrGrowUsage(GameTestHelper helper) {
        var player = caster(helper, AbilityCategory.TELEKINESIS);
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.learnSkill("liquid_shadow");
        data.setDevMode(false);
        player.getInventory().add(new ItemStack(Items.WATER_BUCKET));
        float cp = data.getCurrentCp(), overload = data.getCurrentOverload(), growth = data.getUsageMaxCp();
        java.util.function.Consumer<EntityJoinLevelEvent> deny = event -> {
            if (event.getEntity().getTags().contains("academy_liquid_shadow_owner_" + player.getUUID()))
                event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, deny);
        try {
            if (TelekinesisPassiveHandler.toggleLiquidShadow(player)
                    || TelekinesisPassiveHandler.hasLiquidShadow(player)
                    || data.getCurrentCp() != cp || data.getCurrentOverload() != overload
                    || data.getUsageMaxCp() != growth || player.getInventory().countItem(Items.WATER_BUCKET) != 1) {
                helper.fail("a rejected shadow insertion changed CP, overload, usage growth, bucket, or session"); return;
            }
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(deny);
            TelekinesisPassiveHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
        }
    }

    @GameTest(template = "empty") public static void shadowSpawnRechecksResourcesAfterJoinCallbacks(GameTestHelper helper) {
        var player = caster(helper, AbilityCategory.TELEKINESIS);
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.learnSkill("liquid_shadow");
        data.setDevMode(false);
        player.getInventory().add(new ItemStack(Items.WATER_BUCKET));
        java.util.function.Consumer<EntityJoinLevelEvent> drain = event -> {
            if (event.getEntity().getTags().contains("academy_liquid_shadow_owner_" + player.getUUID()))
                data.setCurrentCp(0);
        };
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, drain);
        try {
            if (TelekinesisPassiveHandler.toggleLiquidShadow(player)
                    || TelekinesisPassiveHandler.hasLiquidShadow(player)
                    || data.getCurrentCp() != 0 || player.getInventory().countItem(Items.WATER_BUCKET) != 1) {
                helper.fail("spawn succeeded after a join callback invalidated its resource preflight"); return;
            }
            helper.succeed();
        } finally {
            NeoForge.EVENT_BUS.unregister(drain);
            TelekinesisPassiveHandler.logout(new PlayerEvent.PlayerLoggedOutEvent(player));
        }
    }

    private static void transmissionItemVisibility(GameTestHelper helper, boolean behindPane) {
        var player = caster(helper, AbilityCategory.TELEKINESIS);
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.learnSkill("psycho_transmission");
        var eye = player.getEyePosition();
        var origin = helper.absolutePos(BlockPos.ZERO);
        // Item box front z=3.625 is behind the pane's z=3.5625 back face.
        var item = new net.minecraft.world.entity.item.ItemEntity(helper.getLevel(),
                eye.x, eye.y - 0.1, origin.getZ() + 3.75, new ItemStack(Items.DIAMOND));
        item.setNoGravity(true);
        helper.getLevel().addFreshEntity(item);
        if (behindPane) helper.setBlock(new BlockPos(2, 3, 3), Blocks.GLASS_PANE);
        try {
            var effect = new com.mohistmc.academy.skill.ability.telekinesis.PsychoTransmissionEffect();
            if (!effect.executeAndReport(player, data)) {
                helper.fail("transmission fixture did not start"); return;
            }
            com.mohistmc.academy.skill.ability.telekinesis.PsychoTransmissionEffect.tick(
                    new PlayerTickEvent.Post(player));
            int picked = player.getInventory().countItem(Items.DIAMOND);
            if (behindPane ? picked != 0 || !item.isAlive() : picked != 1 || item.isAlive()) {
                helper.fail(behindPane ? "transmission picked up an item entirely behind a glass pane"
                        : "transmission failed to pick up an unobstructed item on the real look ray"); return;
            }
            helper.succeed();
        } finally {
            com.mohistmc.academy.skill.ability.telekinesis.PsychoTransmissionEffect.logout(
                    new PlayerEvent.PlayerLoggedOutEvent(player));
            item.discard();
        }
    }

    @GameTest(template = "empty") public static void transmissionCannotPickThroughThinWall(GameTestHelper helper) {
        transmissionItemVisibility(helper, true);
    }
    @GameTest(template = "empty") public static void transmissionPicksUnobstructedItem(GameTestHelper helper) {
        transmissionItemVisibility(helper, false);
    }
}
