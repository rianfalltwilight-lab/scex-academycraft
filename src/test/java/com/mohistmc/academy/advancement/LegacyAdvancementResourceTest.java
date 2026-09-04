package com.mohistmc.academy.advancement;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyAdvancementResourceTest {
 @Test void all56IdsAreUniqueValidReachableAndParentsResolve() throws Exception {
  Path root=Path.of("build/generated/legacyAdvancements/data/academy/advancement/legacy");
  assertTrue(Files.isDirectory(root)); List<Path> files;
  try(var s=Files.walk(root)){files=s.filter(p->p.toString().endsWith(".json")).toList();}
  assertEquals(56,files.size()); Set<String> ids=new HashSet<>();
  for(Path p:files){String id=root.relativize(p).toString().replace('\\','/').replace(".json","");assertTrue(ids.add(id));
   String json=Files.readString(p);assertTrue(json.contains("minecraft:impossible"));assertTrue(json.contains("\"earned\""));
   int at=json.indexOf("\"parent\"");if(at>=0){int marker=json.indexOf("academy:legacy/",at);int end=json.indexOf('"',marker);String parent=json.substring(marker+15,end);assertTrue(Files.exists(root.resolve(parent+".json")),id+" -> "+parent);}
  }
  String bridge=Files.readString(Path.of("src/main/java/com/mohistmc/academy/advancement/LegacyAdvancementBridge.java"));
  assertTrue(bridge.contains("learned(ServerPlayer")&&bridge.contains("used(ServerPlayer")&&bridge.contains("obtained(ServerPlayer"));
  assertTrue(bridge.contains("teleporterCritical")&&bridge.contains("teleported(ServerPlayer"));
 }

 @Test void everyLegacyNodeHasOfficialBilingualTitleAndDescription() throws Exception {
  String en=Files.readString(Path.of("src/main/resources/assets/academy/lang/en_us.json"));
  String zh=Files.readString(Path.of("src/main/resources/assets/academy/lang/zh_cn.json"));
  Path root=Path.of("build/generated/legacyAdvancements/data/academy/advancement/legacy");
  try(var paths=Files.walk(root)) {
   for(Path path:paths.filter(p->p.toString().endsWith(".json")).toList()) {
    String json=Files.readString(path); int title=json.indexOf("achievement.ac_");
    assertTrue(title>=0,path.toString()); int end=json.indexOf('"',title); String key=json.substring(title,end);
    assertTrue(en.contains("\""+key+"\""),"missing en "+key);
    assertTrue(zh.contains("\""+key+"\""),"missing zh "+key);
    assertTrue(en.contains("\""+key+".desc\""),"missing en desc "+key);
    assertTrue(zh.contains("\""+key+".desc\""),"missing zh desc "+key);
   }
  }
  assertEquals(112,count(en,"achievement.ac_")); assertEquals(112,count(zh,"achievement.ac_"));
 }
 private static int count(String text,String needle){int n=0,at=0;while((at=text.indexOf(needle,at))>=0){n++;at+=needle.length();}return n;}
}
