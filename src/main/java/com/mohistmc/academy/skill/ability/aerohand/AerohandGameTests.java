package com.mohistmc.academy.skill.ability.aerohand;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.PlayerAbilityData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class AerohandGameTests {
    private static final String EMPTY = "empty";

    private AerohandGameTests() {}

    private static PlayerAbilityData aerohand(ServerPlayer player, String... learned) {
        player.setGameMode(GameType.SURVIVAL);
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(AbilityCategory.AEROHAND);
        data.setAbilityActive(true);
        data.setDevMode(false);
        for (String id : learned) data.learnSkill(id);
        player.setData(AcademyAttachments.PLAYER_ABILITY, data);
        return data;
    }

    @GameTest(template = EMPTY)
    public static void airCoolingReducesOverloadWithoutHealing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = aerohand(player, "air_cooling");
        player.setHealth(10.0f);
        data.setCurrentOverload(200.0f);
        AirCoolingEffect effect = new AirCoolingEffect();
        if (!effect.canActivate(player, data)) {
            helper.fail("air cooling rejected a player with overload"); return;
        }
        effect.execute(player, data);
        if (data.getCurrentOverload() >= 200.0f) {
            helper.fail("air cooling did not reduce overload"); return;
        }
        if (Math.abs(player.getHealth() - 10.0f) > 0.001f) {
            helper.fail("air cooling incorrectly healed the player"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void separatorVacuumIncludesItsCaster(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = aerohand(player, "aero_separator");
        // Newly connected ServerPlayers have 60 ticks of spawn protection;
        // wait it out so this checks the real damage path rather than bypassing it.
        helper.runAfterDelay(65, () -> {
            float before = player.getHealth();
            int affected = AeroSeparatorEffect.detonate(player, data,
                    player.position().add(0, player.getBbHeight() * 0.5, 0));
            if (affected < 1 || player.getHealth() >= before) {
                helper.fail("separator vacuum did not suffocate its caster inside the volume"); return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void passiveFallCapAndMasteredBreathingAreServerOwned(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = aerohand(player, "ascending_air", "airflow");
        data.setProficiency("ascending_air", 1.0f);
        data.setProficiency("airflow", 1.0f);

        LivingFallEvent fall = new LivingFallEvent(player, 50.0f, 1.0f);
        AeroPassiveRuntime.falling(fall);
        if (Math.abs(fall.getDistance() - 5.0f) > 0.001f) {
            helper.fail("ascending air did not cap mastery fall distance"); return;
        }

        LivingBreatheEvent breathe = new LivingBreatheEvent(player, false, 1, 4);
        AeroPassiveRuntime.breathing(breathe);
        if (!breathe.canBreathe() || breathe.getRefillAirAmount() < 4) {
            helper.fail("mastered airflow did not permit water breathing"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void flyingRevokesOnlyTheFlightItGranted(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = aerohand(player, "flying");
        AeroPassiveRuntime.playerTick(new PlayerTickEvent.Post(player));
        if (!player.getAbilities().mayfly) {
            helper.fail("flying passive did not grant survival flight"); return;
        }
        data.setAbilityActive(false);
        AeroPassiveRuntime.playerTick(new PlayerTickEvent.Post(player));
        if (player.getAbilities().mayfly || player.getAbilities().flying) {
            helper.fail("flying passive leaked flight after ability deactivation"); return;
        }
        helper.succeed();
    }
}
