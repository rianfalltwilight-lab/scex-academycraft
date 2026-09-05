package com.mohistmc.academy.skill.ability.meltdowner;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.entity.MdBallEntity;
import com.mohistmc.academy.entity.MeltdownBeamEntity;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.world.AcademyEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class MeltdownerParityGameTests {
    private static final String EMPTY = "empty";

    private MeltdownerParityGameTests() {}

    private static PlayerAbilityData meltdowner(ServerPlayer player, String skill) {
        player.setGameMode(GameType.SURVIVAL);
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.MELTDOWNER);
        data.setPlayerLevel(5);
        data.setAbilityActive(true);
        data.learnSkill(skill);
        data.setProficiency(skill, 1);
        return data;
    }

    @GameTest(template = EMPTY)
    public static void jetEngineKeepsLegacyEightTickVectorThroughTermination(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 start = player.position();
        Vec3 target = start.add(4, 0, 0);
        float priorWalkSpeed = player.getAbilities().getWalkingSpeed();
        JetEngineRuntime.start(player, target, 0);

        for (int i = 0; i < 8; i++) JetEngineRuntime.tick(new PlayerTickEvent.Post(player));
        if (player.position().distanceToSqr(target) > 1.0e-6) {
            helper.fail("Jet Engine did not reach its marker on legacy tick 8");
            return;
        }
        for (int i = 8; i < 16; i++) JetEngineRuntime.tick(new PlayerTickEvent.Post(player));
        Vec3 legacyEnd = start.add(target.subtract(start).scale(2));
        if (player.position().distanceToSqr(legacyEnd) > 1.0e-6) {
            helper.fail("Jet Engine lost the 1.0.7 unclamped post-marker travel");
            return;
        }
        if (Math.abs(player.getAbilities().getWalkingSpeed() - priorWalkSpeed) > 1.0e-6) {
            helper.fail("Jet Engine did not restore the pre-cast walking speed");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void jetEngineReleaseUsesFinal112UnraisedRayEndpoint(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = meltdowner(player, "jet_engine");
        data.setDevMode(true);
        player.setYRot(0);
        player.setYHeadRot(0);
        player.setXRot(0);
        Vec3 expected = JetEngineEffect.legacyDestination(player);

        JetEngineEffect effect = new JetEngineEffect();
        if (!effect.tryRelease(player, data, 1)) {
            helper.fail("Jet Engine release was rejected in dev-mode parity fixture");
            return;
        }
        for (int i = 0; i < 8; i++) JetEngineRuntime.tick(new PlayerTickEvent.Post(player));
        if (player.position().distanceToSqr(expected) > 1.0e-6) {
            helper.fail("Jet Engine reintroduced the pre-769f45c1 +1.65 Y target offset");
            return;
        }
        // Finish the deliberately unclamped final 1.12.2 lifetime so no
        // static runtime state escapes this fixture.
        for (int i = 8; i < 16; i++) JetEngineRuntime.tick(new PlayerTickEvent.Post(player));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void mdBallKeepsItsSpawnOffsetInsteadOfOrbiting(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MdBallEntity ball = new MdBallEntity(AcademyEntities.MD_BALL.get(), helper.getLevel())
                .bind(player.getUUID(), 4, false);
        if (!helper.getLevel().addFreshEntity(ball)) {
            helper.fail("could not spawn MD ball");
            return;
        }
        Vec3 initialOffset = ball.position().subtract(player.position());
        for (int i = 0; i < 12; i++) ball.tick();
        Vec3 finalOffset = ball.position().subtract(player.position());
        if (initialOffset.distanceToSqr(finalOffset) > 1.0e-8) {
            helper.fail("MD ball still orbits instead of retaining EntityMdBall's fixed offset");
            return;
        }
        double horizontal = Math.sqrt(initialOffset.x * initialOffset.x + initialOffset.z * initialOffset.z);
        if (horizontal < .8 || horizontal > 1.3 || initialOffset.y < .4 || initialOffset.y > 1.8) {
            helper.fail("MD ball display offset left the final 1.12.2 spawn ranges");
            return;
        }
        helper.succeed();
    }

    // This assertion needs a singleton five-block Mob population because the
    // final 1.12.2 implementation deliberately chooses a random nearby Mob.
    // Default GameTests run concurrently in neighbouring fixtures, so a mob
    // created by another test can legitimately become the chosen target and
    // make this test fail even though auto-aim worked.  A separate batch keeps
    // the production random-selection rule intact while making the assertion
    // deterministic.
    @GameTest(template = EMPTY, batch = "scatter_bomb_isolated")
    public static void scatterBombMasteryAutoTargetsNearbyMob(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = meltdowner(player, "scatter_bomb");
        data.setDevMode(true);
        player.setYRot(0);
        player.setYHeadRot(0);
        player.setXRot(0);

        // Batch isolation prevents neighbouring tests from contributing mobs,
        // but it does not disable natural spawning in the shared GameTest
        // level.  The legacy implementation deliberately picks a random Mob,
        // so enforce the singleton population promised by this test instead
        // of assuming the world starts empty.
        helper.getLevel().getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(5)).forEach(Mob::discard);

        Zombie target = EntityType.ZOMBIE.create(helper.getLevel());
        if (target == null) {
            helper.fail("could not create Scatter Bomb auto target");
            return;
        }
        // Ninety degrees away from the look vector: the final 1.12.2
        // high-proficiency auto-target branch is the only way this ray hits.
        target.setPos(player.getX() + 3, player.getY(), player.getZ());
        helper.getLevel().addFreshEntity(target);
        float health = target.getHealth();

        ScatterBombEffect effect = new ScatterBombEffect();
        effect.onChargingStart(player, data);
        if (!effect.onChargingTick(player, data, 20)) {
            helper.fail("Scatter Bomb could not reach its first ball cadence");
            return;
        }
        var balls = helper.getLevel().getEntitiesOfClass(MdBallEntity.class,
                player.getBoundingBox().inflate(4));
        if (balls.size() != 1) {
            helper.fail("Scatter Bomb created " + balls.size() + " balls at its first cadence, expected 1");
            return;
        }
        var traced = ScatterBombEffect.nearestVisibleTarget(player, balls.getFirst().position(),
                target.position().add(0, target.getEyeHeight(), 0));
        if (traced != target) {
            helper.fail("Scatter Bomb auto-target ray selected "
                    + (traced == null ? "nothing" : traced.getType().toString())
                    + " instead of the nearby zombie");
            return;
        }
        if (!effect.tryRelease(player, data, 20)) {
            helper.fail("Scatter Bomb did not release its accumulated ball");
            return;
        }
        if (target.getHealth() >= health) {
            helper.fail("Scatter Bomb lost final 1.12.2 mastery auto-targeting");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void lightShieldAbsorbsFallAndOnlyFrontalDirectDamage(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = meltdowner(player, "light_shield");
        data.setDevMode(true);
        player.setYRot(0);
        player.setYHeadRot(0);

        LightShieldEffect effect = new LightShieldEffect();
        effect.onChargingStart(player, data);
        effect.onChargingTick(player, data, 1);
        com.mohistmc.academy.skill.AcceptedAbilityDamage fall = new com.mohistmc.academy.skill.AcceptedAbilityDamage(player, helper.getLevel().damageSources().fall(), 10);
        LightShieldEffect.damage(fall);
        if (fall.getAmount() != 0 || !fall.isCanceled()) {
            helper.fail("Light Shield lost the final 1.12.2 fall-damage fix");
            return;
        }

        effect.onChargingTick(player, data, 20);
        Zombie attacker = EntityType.ZOMBIE.create(helper.getLevel());
        if (attacker == null) {
            helper.fail("could not create Light Shield directional attacker");
            return;
        }
        attacker.setPos(player.getX(), player.getY(), player.getZ() - 2);
        helper.getLevel().addFreshEntity(attacker);
        com.mohistmc.academy.skill.AcceptedAbilityDamage rear = new com.mohistmc.academy.skill.AcceptedAbilityDamage(player, helper.getLevel().damageSources().mobAttack(attacker), 10);
        LightShieldEffect.damage(rear);
        if (rear.getAmount() != 10 || rear.isCanceled()) {
            helper.fail("Light Shield incorrectly absorbed a rear direct attack");
            return;
        }

        attacker.setPos(player.getX(), player.getY(), player.getZ() + 2);
        com.mohistmc.academy.skill.AcceptedAbilityDamage front = new com.mohistmc.academy.skill.AcceptedAbilityDamage(player, helper.getLevel().damageSources().mobAttack(attacker), 10);
        LightShieldEffect.damage(front);
        effect.onChargingRelease(player, data, 20);
        if (front.getAmount() != 0 || !front.isCanceled()) {
            helper.fail("Light Shield failed to absorb a frontal direct attack");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void mineRayCompletesItsFinalProgressTickAfterCpFailure(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = meltdowner(player, "mine_ray_expert");
        player.setYRot(0); player.setYHeadRot(0); player.setXRot(0);
        BlockPos target = BlockPos.containing(player.getEyePosition().add(0, 0, 3));
        helper.getLevel().setBlock(target, Blocks.GLASS.defaultBlockState(), 3);
        MineRayExpertEffect effect = new MineRayExpertEffect();
        effect.onChargingStart(player, data);
        if (!effect.onChargingTick(player, data, 1)) {
            helper.fail("Mine Ray failed before acquiring its first target");
            return;
        }
        data.setCurrentCp(0);
        if (effect.onChargingTick(player, data, 2)) {
            helper.fail("Mine Ray upkeep unexpectedly succeeded at zero CP");
            return;
        }
        effect.onChargingAbort(player, data);
        if (!helper.getLevel().getBlockState(target).isAir()) {
            helper.fail("Mine Ray skipped the final 1.0.7 progress tick after CP failure");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void basicMineRayUsesItsOwnIronHarvestTier(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = meltdowner(player, "mine_ray_basic");
        data.setDevMode(true);
        player.setYRot(0); player.setYHeadRot(0); player.setXRot(0);
        BlockPos target = BlockPos.containing(player.getEyePosition().add(0, 0, 3));
        helper.getLevel().setBlock(target, Blocks.DIAMOND_ORE.defaultBlockState(), 3);
        MineRayBasicEffect effect = new MineRayBasicEffect();
        effect.onChargingStart(player, data);
        for (int tick = 1; tick <= 10 && !helper.getLevel().getBlockState(target).isAir(); tick++) {
            effect.onChargingTick(player, data, tick);
        }
        effect.onChargingRelease(player, data, 10);
        if (!helper.getLevel().getBlockState(target).isAir()) {
            helper.fail("Basic Mine Ray could not harvest diamond ore at legacy tier 2");
            return;
        }
        boolean diamond = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new net.minecraft.world.phys.AABB(target).inflate(2), ItemEntity::isAlive).stream()
                .anyMatch(item -> item.getItem().is(Items.DIAMOND));
        if (!diamond) {
            helper.fail("Basic Mine Ray used the player's empty hand and lost the legacy ore drop");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void mineRayUsesOnePersistentFollowingBeam(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = meltdowner(player, "mine_ray_basic");
        data.setDevMode(true);
        MineRayBasicEffect effect = new MineRayBasicEffect();
        effect.onChargingStart(player, data);
        var box = player.getBoundingBox().inflate(20);
        var beams = helper.getLevel().getEntitiesOfClass(MeltdownBeamEntity.class, box,
                MeltdownBeamEntity::followsPlayer);
        if (beams.size() != 1) {
            helper.fail("Mine Ray did not create exactly one persistent owner-following beam");
            return;
        }
        MeltdownBeamEntity beam = beams.getFirst();
        for (int tick = 1; tick <= 5; tick++) effect.onChargingTick(player, data, tick);
        if (helper.getLevel().getEntitiesOfClass(MeltdownBeamEntity.class, box,
                MeltdownBeamEntity::followsPlayer).size() != 1) {
            helper.fail("Mine Ray recreated/stacked its visual beam during upkeep");
            return;
        }
        effect.onChargingRelease(player, data, 5);
        if (!beam.isRemoved()) {
            helper.fail("Mine Ray left its persistent beam behind after release");
            return;
        }
        helper.succeed();
    }
}
