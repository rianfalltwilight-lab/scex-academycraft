package com.mohistmc.academy.api.event;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SkillExpMutationCoverageTest {
    @Test void onlyOwnerAwareServiceMayIncrementProficiency() throws Exception {
        Path root=Path.of("src/main/java/com/mohistmc/academy");
        List<String> offenders=new ArrayList<>();
        try(var paths=Files.walk(root)){
            for(Path p:paths.filter(x->x.toString().endsWith(".java")).toList()){
                String relative=root.relativize(p).toString().replace('\\','/');
                String source=Files.readString(p);
                if(source.contains(".addProficiency(")&&!relative.equals("skill/AbilityMutationService.java"))offenders.add(relative);
            }
        }
        assertEquals(List.of(),offenders,"authoritative increments must retain their ServerPlayer owner");
    }

    @Test void codecAndDataRemainEntityFree() throws Exception {
        String data=Files.readString(Path.of("src/main/java/com/mohistmc/academy/skill/PlayerAbilityData.java"));
        String codec=Files.readString(Path.of("src/main/java/com/mohistmc/academy/skill/PlayerAbilityDataCodec.java"));
        assertFalse(data.matches("(?s).*private\\s+(?:final\\s+)?ServerPlayer\\s+\\w+.*"),
                "persistent data must not retain an owner entity");
        assertFalse(codec.contains("ServerPlayer"));
        String service=Files.readString(Path.of("src/main/java/com/mohistmc/academy/skill/AbilityMutationService.java"));
        assertTrue(service.contains("player.getData(AcademyAttachments.PLAYER_ABILITY) != data"));
        assertTrue(service.indexOf("new AbilityEvents.SkillExpChanged")<service.indexOf("new AbilityEvents.SkillExpAdded"));
    }
}
