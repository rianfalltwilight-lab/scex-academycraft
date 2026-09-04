package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MediaResourceContractTest {
    private static final Path MEDIA = Path.of("src/main/resources/assets/academy/sounds/media");

    @Test
    void allLegacyBuiltinTracksHaveIndependentStreamResources() throws Exception {
        List<String> ids = List.of("only_my_railgun", "level5_judgelight", "sisters_noise");
        String sounds = Files.readString(Path.of("src/main/resources/assets/academy/sounds.json"));
        Set<String> hashes = new HashSet<>();
        for (String id : ids) {
            Path ogg = MEDIA.resolve(id + ".ogg");
            assertTrue(Files.size(ogg) > 4_000_000, "missing/truncated legacy track " + id);
            assertTrue(sounds.contains("\"media." + id + "\": {\"category\": \"master\", \"sounds\": "
                    + "[{\"name\": \"academy:media/" + id + "\", \"stream\": true}]}"),
                    "sounds.json does not route " + id + " to its own streaming resource");
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(ogg)));
            hashes.add(hash);
        }
        assertEquals(ids.size(), hashes.size(), "two media ids share one audio file");
    }

    @Test
    void legacyTransportVolumeAndAuxHudAreImplementedWithoutGlobalAudioPause() throws Exception {
        String manager = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/client/MediaPlayerManager.java"));
        assertTrue(manager.contains("Channel::pause"));
        assertTrue(manager.contains("Channel::unpause"));
        assertTrue(manager.contains("MEDIA_PLAYER_VOLUME.set"));
        assertTrue(!manager.contains("getSoundManager().pause()"),
                "media pause must not pause every Minecraft sound");
        assertTrue(Files.isRegularFile(Path.of(
                "src/main/java/com/mohistmc/academy/client/gui/MediaPlayerHudOverlay.java")));
        String at = Files.readString(Path.of("src/main/resources/META-INF/accesstransformer.cfg"));
        assertTrue(at.contains("SoundManager soundEngine"));
        assertTrue(at.contains("SoundEngine instanceToChannel"));
    }

    @Test
    void legacyExternalMediaWorkspaceStreamingCoversAndEditableMetadataAreRestored() throws Exception {
        String external = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/client/media/ExternalMediaManager.java"));
        assertTrue(external.contains("resolve(\"acmedia\")"));
        assertTrue(external.contains("resolve(\"source\")"));
        assertTrue(external.contains("resolve(\"cover\")"));
        assertTrue(external.contains("metadata.json"));
        assertTrue(external.contains("DynamicTexture"));
        assertTrue(external.contains("MAX_TRACKS = 256"));

        String player = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/client/MediaPlayerManager.java"));
        assertTrue(player.contains("JOrbisAudioStream"));
        assertTrue(player.contains("getStream(SoundBufferLibrary"));
        assertTrue(player.contains("playerTick() % 5 == 0"));

        String screen = Files.readString(Path.of(
                "src/main/java/com/mohistmc/academy/client/gui/MediaPlayerAppGui.java"));
        assertTrue(screen.contains("refreshed.addAll(ExternalMediaManager.getTracks())"));
        assertTrue(screen.contains("ExternalMediaManager.updateMetadata"));
        assertTrue(Files.isRegularFile(Path.of(
                "src/main/resources/assets/academy/media/readme_template.txt")));
    }
}
