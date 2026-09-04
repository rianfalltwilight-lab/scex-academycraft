package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.ability.vecmanip.VecDeviationEffect;
import com.mohistmc.academy.skill.ability.vecmanip.VecReflectionEffect;
import com.mohistmc.academy.skill.passive.PassiveSkillEventHandler;
import com.mohistmc.academy.skill.passive.VecDefenseRuntime;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class VecDefenseGameTests {
    private static final String EMPTY = "empty";
    private static final String VECTOR_MARK = "academy:vec_deviated";

    private VecDefenseGameTests() {}

    private static PlayerAbilityData vector(ServerPlayer player, String skill, float proficiency, float cp) {
        player.setGameMode(GameType.SURVIVAL);
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.VECMANIP);
        data.setPlayerLevel(5);
        data.setAbilityActive(true);
        data.learnSkill(skill);
        data.setProficiency(skill, proficiency);
        data.setCurrentCp(cp);
        player.setData(AcademyAttachments.PLAYER_ABILITY, data);
        return data;
    }

    @GameTest(template = EMPTY)
    public static void deviationForceStopsOwnProjectileOnItsFinalUnpaidTick(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = vector(player, "vec_deviation", 0, 1);
        Snowball snowball = new Snowball(helper.getLevel(), player);
        snowball.setPos(player.getX() + 1, player.getEyeY(), player.getZ());
        snowball.setDeltaMovement(new Vec3(.3, 0, 0));
        helper.getLevel().addFreshEntity(snowball);

        new VecDeviationEffect().onChargingStart(player, data);
        PassiveSkillEventHandler.tick(new PlayerTickEvent.Post(player));

        if (!snowball.getPersistentData().getBoolean(VECTOR_MARK)) {
            helper.fail("1.0.7 force-stop did not mark the defender's own projectile"); return;
        }
        if (data.getCurrentCp() != 0) {
            helper.fail("forced deviation debit did not clamp CP to zero"); return;
        }
        if (VecDefenseRuntime.active(player.getUUID(), VecDefenseRuntime.Mode.DEVIATION)) {
            helper.fail("unpaid deviation upkeep did not terminate after its final action"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void reflectionRedirectsWithoutStealingProjectileOwnership(GameTestHelper helper) {
        ServerPlayer reflector = helper.makeMockServerPlayerInLevel();
        ServerPlayer shooter = helper.makeMockServerPlayerInLevel();
        shooter.setPos(reflector.getX() + 10, reflector.getY(), reflector.getZ());
        PlayerAbilityData data = vector(reflector, "vec_reflection", 1, 1_000);
        Snowball snowball = new Snowball(helper.getLevel(), shooter);
        snowball.setPos(reflector.getX() + 1, reflector.getEyeY(), reflector.getZ());
        snowball.setDeltaMovement(new Vec3(.4, 0, 0));
        helper.getLevel().addFreshEntity(snowball);

        new VecReflectionEffect().onChargingStart(reflector, data);
        PassiveSkillEventHandler.tick(new PlayerTickEvent.Post(reflector));

        if (!snowball.getPersistentData().getBoolean(VECTOR_MARK)) {
            helper.fail("reflected projectile was not globally marked like EntityAffection.mark"); return;
        }
        if (snowball.getOwner() != shooter) {
            helper.fail("reflection stole the original shooter's damage attribution"); return;
        }
        if (!VecDefenseRuntime.active(reflector.getUUID(), VecDefenseRuntime.Mode.REFLECTION)) {
            helper.fail("paid reflection upkeep terminated unexpectedly"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void zeroCpStillReflectsRaysAndIncomingDamageWithForcedDebit(GameTestHelper helper) {
        ServerPlayer reflector = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = vector(reflector, "vec_reflection", 1, 0);
        Drowned attacker = EntityType.DROWNED.create(helper.getLevel());
        if (attacker == null) {
            helper.fail("could not create reflection attacker"); return;
        }
        attacker.setPos(reflector.getX() + 2, reflector.getY(), reflector.getZ());
        helper.getLevel().addFreshEntity(attacker);
        new VecReflectionEffect().onChargingStart(reflector, data);

        if (!PassiveSkillEventHandler.reflectSpecialRay(reflector, attacker, 10)) {
            helper.fail("live 1.0.7 reflection context rejected a ray solely because CP was zero"); return;
        }
        float attackerHealth = attacker.getHealth();
        DamageContainer container = new DamageContainer(
                helper.getLevel().damageSources().mobAttack(attacker), 10);
        LivingIncomingDamageEvent event = new LivingIncomingDamageEvent(reflector, container);
        PassiveSkillEventHandler.damage(event);

        if (event.getAmount() != 0 || !event.isCanceled()) {
            helper.fail("full-proficiency reflection left damage/knockback event active: "
                    + event.getAmount()); return;
        }
        if (attacker.getHealth() >= attackerHealth) {
            helper.fail("incoming damage was not reflected to its direct source"); return;
        }
        if (data.getCurrentCp() != 0) {
            helper.fail("forced reflection debit escaped the zero-CP clamp"); return;
        }
        helper.succeed();
    }
}
