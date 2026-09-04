package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SoundResourceContractTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/academy");

    @Test void everyDeclaredAcademySoundHasAnOggResource() throws Exception {
        String sounds = Files.readString(ASSETS.resolve("sounds.json"));
        var matcher = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"academy:([^\\\"]+)\\\"").matcher(sounds);
        int declared = 0;
        while (matcher.find()) {
            declared++;
            Path ogg = ASSETS.resolve("sounds").resolve(matcher.group(1) + ".ogg");
            assertTrue(Files.isRegularFile(ogg), () -> "missing declared sound resource: " + ogg);
        }
        assertTrue(declared >= 49, "legacy sound inventory unexpectedly shrank");
    }

    @Test void dataTerminalUsesItsOriginalDedicatedSounds() throws Exception {
        String sounds = Files.readString(ASSETS.resolve("sounds.json"));
        assertTrue(sounds.contains("\"terminal.select\": {\"category\": \"master\", \"sounds\": "
                + "[{\"name\": \"academy:terminal/select\""));
        assertTrue(sounds.contains("\"terminal.confirm\": {\"category\": \"master\", \"sounds\": "
                + "[{\"name\": \"academy:terminal/confirm\""));
    }
}
