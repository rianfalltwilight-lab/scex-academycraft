package com.mohistmc.academy.config;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyAbilityRulesTest {
    @Test void defaultsMatch107Config() {
        var d=LegacyAbilityRules.DEFAULTS;
        assertEquals(15,d.cpDelay()); assertEquals(1,d.cpSpeed()); assertEquals(32,d.overloadDelay());
        assertEquals(1,d.overloadSpeed()); assertEquals(.0025f,d.maxCpGrowth());
        assertEquals(.0058f,d.maxOverloadGrowth()); assertEquals(1,d.proficiencyGrowth());
    }
    @Test void skillDisableAndMultipliersAreParsed() {
        var t=LegacyAbilityRules.parseSkill("railgun",List.of("railgun.enabled=false","railgun.damage=2",
                "railgun.cp=.5","railgun.overload=1.25","railgun.exp=3","railgun.destroy_blocks=false"));
        assertFalse(t.enabled()); assertEquals(2,t.damage()); assertEquals(.5f,t.cp());
        assertEquals(1.25f,t.overload()); assertEquals(3,t.exp()); assertFalse(t.destroyBlocks());
    }
    @Test void malformedOrOtherSkillEntriesCannotPoisonDefaults() {
        var t=LegacyAbilityRules.parseSkill("railgun",List.of("other.enabled=false","railgun.cp=NaN","railgun.damage=-4"));
        assertTrue(t.enabled()); assertEquals(1,t.cp()); assertEquals(1,t.damage());
    }
    @Test void recoveryCurvesMatch107CpDataFormula() {
        var d=LegacyAbilityRules.DEFAULTS;
        assertEquals(.6f,LegacyAbilityRules.cpRecovery(0,2000,1,d),1e-6);
        assertEquals(1.2f,LegacyAbilityRules.cpRecovery(2000,2000,1,d),1e-6);
        assertEquals(3.5f,LegacyAbilityRules.overloadRecovery(0,500,d),1e-6);
        assertEquals(2.625f,LegacyAbilityRules.overloadRecovery(500,500,d),1e-6);
    }
}
