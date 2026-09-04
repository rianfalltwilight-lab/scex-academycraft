package com.mohistmc.academy.config;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynamicSettlementSourceContractTest {
 @Test void abilityEffectsCannotBypassTheAtomicSettlementBoundary() throws Exception {
  Path root=Path.of("src/main/java/com/mohistmc/academy/skill/ability"); List<String> violations=new ArrayList<>();
  try(var paths=Files.walk(root)) {
   for(Path p:paths.filter(x->x.toString().endsWith(".java")).toList()) {
    // GameTest fixtures intentionally seed CP/OL to exercise production
    // effects; they are not player-facing settlement implementations.
    if(p.getFileName().toString().endsWith("GameTests.java")) continue;
    if(p.getFileName().toString().equals("LightShieldResourceLedger.java")) continue;
    String source=Files.readString(p);
    for(String forbidden:List.of(".setCurrentCp(",".addOverload(",".addProficiency("))
     if(source.contains(forbidden)) violations.add(root.relativize(p)+" uses "+forbidden);
    if(source.contains("DynamicSkillRules.tryPay") && !source.contains("appliesBaseResourceCost")
        && !p.getFileName().toString().equals("TeleportSkillHelper.java"))
     violations.add(root.relativize(p)+" dynamically pays but still applies the registry base cost");
   }
  }
  assertEquals(List.of(),violations);
 }

 @Test void everyExplicitDynamicEffectConsumesTheSharedRuleMatrix() throws Exception {
  Path root=Path.of("src/main/java/com/mohistmc/academy/skill/ability"); List<String> violations=new ArrayList<>();
  try(var paths=Files.walk(root)) {
   for(Path p:paths.filter(x->x.toString().endsWith(".java")).toList()) {
    if(p.getFileName().toString().endsWith("GameTests.java")) continue;
    String source=Files.readString(p);
    if(source.contains("appliesBaseResourceCost")&&source.contains("return false")
        && !source.contains("DynamicSkillRules")) violations.add(root.relativize(p).toString());
   }
  }
  assertEquals(List.of(),violations);
 }
}
