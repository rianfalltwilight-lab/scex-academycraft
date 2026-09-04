package com.mohistmc.academy.skill.ability.aerohand;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Adversarial contracts for behaviors that previously contradicted their descriptions. */
class AerohandBehaviorSourceContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy").resolve(relative));
    }

    @Test
    void coolingMutatesOverloadRatherThanHealthOrFireResistance() throws Exception {
        String cooling = source("skill/ability/aerohand/AirCoolingEffect.java");
        String registry = source("skill/SkillRegistry.java");
        assertTrue(cooling.contains("setCurrentOverload"));
        assertFalse(cooling.contains(".heal("));
        assertFalse(cooling.contains("FIRE_RESISTANCE"));
        int skill = registry.indexOf("new Skill.Builder(\"air_cooling\"");
        assertTrue(skill >= 0);
        assertTrue(registry.substring(skill, Math.min(skill + 420, registry.length()))
                .contains(".cpCost(20).overload(0)"));
    }

    @Test
    void jetMovesCasterAndVolcanicBallIsDistanceAttenuatedAir() throws Exception {
        String jet = source("skill/ability/aerohand/AirJetEffect.java");
        String ball = source("skill/ability/aerohand/VolcanicBallEffect.java");
        assertTrue(jet.contains("player.setDeltaMovement"));
        assertTrue(jet.contains("isOffenseArmourEngaged"));
        assertFalse(jet.contains("AcademyDamageHelper"));
        assertTrue(ball.contains("AeroBehaviorMath.volcanicDamage"));
        assertTrue(ball.contains("target.setDeltaMovement"));
        assertFalse(ball.contains(".explode("));
        assertFalse(ball.contains("setSecondsOnFire"));
    }

    @Test
    void separatorChargesAndExplicitlyIncludesSelfSuffocation() throws Exception {
        String separator = source("skill/ability/aerohand/AeroSeparatorEffect.java");
        String boundary = source("skill/AcademyDamageHelper.java");
        assertTrue(separator.contains("implements ChargingSkillEffect"));
        assertTrue(separator.contains("player.position().add"));
        assertTrue(separator.contains("living == player"));
        assertTrue(separator.contains("damageSources().inWall()"));
        assertTrue(separator.contains("living.setAirSupply"));
        assertTrue(separator.contains("AcademyDamageHelper.hurtSelf"));
        assertTrue(boundary.contains("target == attacker"));
        assertTrue(boundary.contains("Float.isFinite(amount)"));
    }

    @Test
    void cruiseBombConsumesWaterAndRunsBoundedServerSessions() throws Exception {
        String cruise = source("skill/ability/telekinesis/CruiseBombEffect.java");
        assertTrue(cruise.contains("Items.WATER_BUCKET"));
        assertTrue(cruise.contains("new ItemStack(Items.BUCKET)"));
        assertTrue(cruise.contains("Map<UUID, Session> SESSIONS"));
        assertTrue(cruise.contains("PlayerTickEvent.Post"));
        assertTrue(cruise.contains("projectile.discard()"));
        assertFalse(cruise.contains("GENERIC_EXPLODE"));
        assertFalse(cruise.contains("level.explode"));
    }

    @Test
    void allFourPassivesHaveDedicatedServerEventBoundaries() throws Exception {
        String runtime = source("skill/ability/aerohand/AeroPassiveRuntime.java");
        assertTrue(runtime.contains("LivingFallEvent"));
        assertTrue(runtime.contains("LivingBreatheEvent"));
        assertTrue(runtime.contains("LivingDrownEvent"));
        assertTrue(runtime.contains("LivingIncomingDamageEvent"));
        assertTrue(runtime.contains("GRANTED_FLIGHT"));
        assertTrue(runtime.contains("projectile.discard()"));
        assertTrue(runtime.contains("ServerStoppedEvent"));
    }
}
