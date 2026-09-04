package com.mohistmc.academy.skill.ability.aerohand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AeroBehaviorMathTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void airCoolingRemovesOverloadAndNeverUnderflows() {
        assertEquals(120.0f, AeroBehaviorMath.cooledOverload(200.0f, 0.0f), EPSILON);
        assertEquals(20.0f, AeroBehaviorMath.cooledOverload(200.0f, 1.0f), EPSILON);
        assertEquals(0.0f, AeroBehaviorMath.cooledOverload(30.0f, 1.0f), EPSILON);
        assertEquals(0.0f, AeroBehaviorMath.cooledOverload(Float.NaN, 0.5f), EPSILON);
    }

    @Test
    void airJetAndSeparatorProgressionStayBounded() {
        assertEquals(1.35, AeroBehaviorMath.airJetSpeed(-1.0f), 0.0001);
        assertEquals(2.25, AeroBehaviorMath.airJetSpeed(2.0f), 0.0001);
        assertEquals(30, AeroBehaviorMath.separatorChargeTicks(0.0f));
        assertEquals(18, AeroBehaviorMath.separatorChargeTicks(1.0f));
        assertEquals(3.0f, AeroBehaviorMath.separatorRadius(0.0f), EPSILON);
        assertEquals(5.0f, AeroBehaviorMath.separatorRadius(1.0f), EPSILON);
        assertEquals(12.0f, AeroBehaviorMath.separatorDamage(0.0f), EPSILON);
        assertEquals(24.0f, AeroBehaviorMath.separatorDamage(1.0f), EPSILON);
    }

    @Test
    void volcanicBallDamageFallsWithTravelButRetainsMinimum() {
        assertEquals(10.0f, AeroBehaviorMath.volcanicDamage(10.0f, 0, 20), EPSILON);
        assertEquals(6.75f, AeroBehaviorMath.volcanicDamage(10.0f, 10, 20), EPSILON);
        assertEquals(3.5f, AeroBehaviorMath.volcanicDamage(10.0f, 20, 20), EPSILON);
        assertEquals(3.5f, AeroBehaviorMath.volcanicDamage(10.0f, 200, 20), EPSILON);
        assertEquals(0.0f, AeroBehaviorMath.volcanicDamage(10.0f, 1, 0), EPSILON);
    }

    @Test
    void ascendingAirCapsRawFallDamageMoreStronglyAtMastery() {
        float noviceDistance = AeroBehaviorMath.cappedFallDistance(50, 1, 0);
        float masterDistance = AeroBehaviorMath.cappedFallDistance(50, 1, 1);
        assertEquals(11.0f, noviceDistance, EPSILON);
        assertEquals(5.0f, masterDistance, EPSILON);
        assertTrue(masterDistance < noviceDistance);
        assertEquals(2.0f, AeroBehaviorMath.cappedFallDistance(2, 1, 1), EPSILON);
    }

    @Test
    void defensiveAndCruisePoliciesHaveAuditableEndpoints() {
        assertEquals(0.80f, AeroBehaviorMath.offenseArmourDamageMultiplier(0), EPSILON);
        assertEquals(0.55f, AeroBehaviorMath.offenseArmourDamageMultiplier(1), EPSILON);
        assertEquals(1, AeroBehaviorMath.airflowConsumptionInterval(0));
        assertEquals(5, AeroBehaviorMath.airflowConsumptionInterval(1));
        assertEquals(3, AeroBehaviorMath.cruiseBombOrbCount(0));
        assertEquals(6, AeroBehaviorMath.cruiseBombOrbCount(1));
        assertEquals(200, AeroBehaviorMath.cruiseBombDurationTicks(0));
        assertEquals(400, AeroBehaviorMath.cruiseBombDurationTicks(1));
        assertEquals(4.0f, AeroBehaviorMath.cruiseBombDamage(0), EPSILON);
        assertEquals(8.0f, AeroBehaviorMath.cruiseBombDamage(1), EPSILON);
    }
}
