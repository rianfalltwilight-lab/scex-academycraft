package com.mohistmc.academy.skill;
import static org.junit.jupiter.api.Assertions.*;import org.junit.jupiter.api.Test;
class LegacyParityMathTest{
 @Test void scatterCadenceMatchesTwentyThroughEighty(){assertEquals(0,LegacyParityMath.scatterBallCount(19));assertEquals(1,LegacyParityMath.scatterBallCount(20));assertEquals(7,LegacyParityMath.scatterBallCount(80));}
 @Test void legacyEndpointsRemainExact(){assertEquals(80,LegacyParityMath.missileTimeLimit(0));assertEquals(200,LegacyParityMath.missileTimeLimit(1));assertEquals(18,LegacyParityMath.deviationTickCp(0),1e-5);assertEquals(7.5,LegacyParityMath.deviationTickCp(1),1e-5);assertEquals(12,LegacyParityMath.flashingDistance(0),1e-5);assertEquals(18,LegacyParityMath.flashingDistance(1),1e-5);assertEquals(40,LegacyParityMath.flashingGravityTicks());assertEquals(8,LegacyParityMath.jetTravelTicks());}
}
