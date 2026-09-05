package com.mohistmc.academy.skill;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AcademyDamageBoundaryContractTest {
    @Test void academyDamageCannotBypassThePvpBoundary() throws IOException {
        Path root=Path.of("src/main/java/com/mohistmc/academy");
        List<String> violations=new ArrayList<>();
        try(var files=Files.walk(root)) {
            files.filter(p->p.toString().endsWith(".java"))
                    // Runtime fixtures must call vanilla hurt to audit the real event boundary.
                    .filter(p->!p.startsWith(root.resolve("gametest")))
                    .filter(p->!p.endsWith("AcademyDamageHelper.java"))
                    .forEach(p->{try {
                        String source=Files.readString(p).replace("AcademyDamageHelper.hurt", "");
                        if(source.matches("(?s).*\\b[A-Za-z_$][A-Za-z0-9_$]*\\.hurt\\s*\\(.*")) violations.add(root.relativize(p).toString());
                    }catch(IOException e){throw new RuntimeException(e);}});
        }
        assertEquals(List.of(),violations,"direct Entity.hurt bypasses AcademyDamageHelper");
    }

    @Test void legacyPvpOptionIsPublicDefaultTrueAndConsumed() throws IOException {
        String config=Files.readString(Path.of("src/main/java/com/mohistmc/academy/config/ACConfig.java"));
        String boundary=Files.readString(Path.of("src/main/java/com/mohistmc/academy/skill/AcademyDamageHelper.java"));
        assertTrue(config.contains("define(\"pvpEnabled\", true)"));
        assertTrue(boundary.contains("ACConfig.Server.pvpEnabled()"));
    }
}
