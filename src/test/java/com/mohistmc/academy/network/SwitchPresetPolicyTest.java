package com.mohistmc.academy.network;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SwitchPresetPolicyTest {
    @Test void rejectsMalformedUnauthorizedChargingAndSpam() {
        assertFalse(SwitchPresetPolicy.maySwitch(-1, 4, true, false, Long.MIN_VALUE, 10));
        assertFalse(SwitchPresetPolicy.maySwitch(4, 4, true, false, Long.MIN_VALUE, 10));
        assertFalse(SwitchPresetPolicy.maySwitch(1, 4, false, false, Long.MIN_VALUE, 10));
        assertFalse(SwitchPresetPolicy.maySwitch(1, 4, true, true, Long.MIN_VALUE, 10));
        assertFalse(SwitchPresetPolicy.maySwitch(1, 4, true, false, 9, 10));
        assertTrue(SwitchPresetPolicy.maySwitch(3, 4, true, false, 6, 10));
    }

    @Test void invalidFloodCannotAmplifyResponsesAndNormalRequestRecovers() {
        var state = SwitchPresetPolicy.State.empty();
        int responses = 0;
        for (long tick=0;tick<20;tick++) {
            var d=SwitchPresetPolicy.decide(Integer.MAX_VALUE,4,0,true,false,state,tick);
            state=d.state();
            if(d.action()==SwitchPresetPolicy.Action.REJECT_SYNC)responses++;
            assertNotEquals(SwitchPresetPolicy.Action.ACCEPT,d.action());
        }
        assertEquals(1,responses,"invalid flood must not produce one sync response per request");
        var normal=SwitchPresetPolicy.decide(1,4,0,true,false,state,21);
        assertEquals(SwitchPresetPolicy.Action.ACCEPT,normal.action(),"throttle must not permanently lock valid play");
    }

    @Test void duplicateAndChargingRejectionsShareTheBoundedSyncWindow() {
        var first=SwitchPresetPolicy.decide(0,4,0,true,false,SwitchPresetPolicy.State.empty(),0);
        assertEquals(SwitchPresetPolicy.Action.REJECT_SYNC,first.action());
        var dropped=SwitchPresetPolicy.decide(1,4,0,true,true,first.state(),1);
        assertEquals(SwitchPresetPolicy.Action.DROP,dropped.action());
        var silent=SwitchPresetPolicy.decide(1,4,0,true,true,first.state(),2);
        assertEquals(SwitchPresetPolicy.Action.REJECT_SILENT,silent.action());
    }
}
