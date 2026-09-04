package com.mohistmc.academy.config;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DynamicSkillRulesTest {
 @Test void identityAndEndpointsAreStable(){assertEquals(12,DynamicSkillRules.scale(12,1));assertEquals(6,DynamicSkillRules.scale(12,.5f));assertEquals(24,DynamicSkillRules.scale(12,2));}
 @Test void hostileNumbersFailClosed(){assertEquals(0,DynamicSkillRules.scale(Float.NaN,2));assertEquals(0,DynamicSkillRules.scale(-1,2));assertEquals(0,DynamicSkillRules.scale(Float.MAX_VALUE,Float.MAX_VALUE));}
 @Test void legacyAndNamespacedDimensionAllowlistEntriesResolveExactly(){
  assertTrue(LegacyDimensionAllowlist.contains("minecraft:overworld",List.of("0")));
  assertTrue(LegacyDimensionAllowlist.contains("minecraft:the_nether",List.of("-1")));
  assertTrue(LegacyDimensionAllowlist.contains("minecraft:the_end",List.of("1")));
  assertTrue(LegacyDimensionAllowlist.contains("example:ability_arena",List.of("example:ability_arena")));
  assertFalse(LegacyDimensionAllowlist.contains("minecraft:overworld",List.of("example:ability_arena")));
  assertTrue(LegacyDimensionAllowlist.validEntry("example:ability_arena"));
  assertFalse(LegacyDimensionAllowlist.validEntry("Example:BAD"));
 }
}
