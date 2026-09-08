package com.mohistmc.academy.skill;
import com.mohistmc.academy.skill.ability.electromaster.ChargingOutput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChargingOutputTest {
    @Test void allOldIntegerStepsAndAdjacentFloatBoundariesScaleExactlyOverTwoTicks() {
        for (int i=0;i<=20;i++) {
            float boundary=i/20f;
            for(float p:new float[]{Math.max(0,Math.nextDown(boundary)),boundary,Math.min(1,Math.nextUp(boundary))}) {
                int old=(int)(15f+(35f-15f)*p);
                for(int units:new int[]{1,4}) {
                    ChargingOutput out=new ChargingOutput();
                    int first=out.preview(p,units);
                    assertEquals(first,out.preview(p,units),"simulation cannot advance carry");
                    out.commit(p,units);
                    int second=out.preview(p,units);
                    assertEquals(old*units*5,first+second,"two ticks must equal 2.5x old two ticks");
                    if(units==4) assertEquals(old*10,first,"FE exact every tick");
                }
            }
        }
    }
    @Test void longRunHasNoDriftOrRepeatedMultiplication() {
        ChargingOutput out=new ChargingOutput(); long sum=0;
        for(int tick=0;tick<100000;tick++){sum+=out.preview(1,1);out.commit(1,1);}
        assertEquals(8750000L,sum);
    }
    @Test void malformedProficiencyAndUnitChangesAreBounded() {
        for(float p:new float[]{Float.NaN,Float.NEGATIVE_INFINITY,Float.POSITIVE_INFINITY,-1,Float.MAX_VALUE}) {
            int value=new ChargingOutput().preview(p,4); assertTrue(value>=150&&value<=350);
        }
        ChargingOutput out=new ChargingOutput();out.commit(0,1);
        assertEquals(150,out.preview(0,4));out.commit(0,4);assertEquals(37,out.preview(0,1));
        assertThrows(IllegalArgumentException.class,()->out.preview(0,Integer.MAX_VALUE));
    }
}
