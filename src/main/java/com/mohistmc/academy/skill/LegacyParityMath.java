package com.mohistmc.academy.skill;
/** Pure formula/timing contracts copied from the fixed 1.0.7 baseline. */
public final class LegacyParityMath {
 private LegacyParityMath(){}
 public static int scatterBallCount(int heldTicks){if(heldTicks<20)return 0;return Math.min(7,(heldTicks-20)/10+1);}
 public static int missileTimeLimit(float exp){return(int)(80+(200-80)*exp);}
 public static float deviationTickCp(float exp){return(13-8*exp)+(5-2.5f*exp);}
 public static float flashingDistance(float exp){return 12+6*exp;}
 public static int flashingGravityTicks(){return 40;}
 public static int jetTravelTicks(){return 8;}
}
