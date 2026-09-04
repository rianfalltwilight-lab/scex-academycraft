package com.mohistmc.academy.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Source/resource contracts for client paths that previously retained or crashed on stale state. */
class ClientFatalPathContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java").resolve(relative));
    }

    @Test
    void mediaClickUsesSnapshotBoundsIdentityAndAuthoritativeOwnership() throws Exception {
        String s = source("com/mohistmc/academy/client/gui/MediaPlayerAppGui.java");
        assertTrue(s.contains("List.copyOf(refreshed)"));
        assertTrue(s.contains("hoveredTrack < tracks.size()"));
        assertTrue(s.contains("trackId.equals(hoveredTrackId)"));
        assertTrue(s.contains("getLoadedMedia().contains(trackId)"));
    }

    @Test
    void sessionResetIsLocalCompleteAndHookedToBothBoundaries() throws Exception {
        String state = source("com/mohistmc/academy/client/ClientSessionState.java");
        String listener = source("com/mohistmc/academy/listener/ClientListener.java");
        assertTrue(state.contains("KeyInputHandler.resetClientSession()"));
        assertTrue(state.contains("ChargingHudOverlay.resetClientSession()"));
        assertTrue(state.contains("MediaPlayerManager.stop()"));
        assertTrue(state.contains("LocationTeleportGui.resetClientSession()"));
        assertTrue(state.contains("AcademyBaseUI.clearNodeCache()"));
        assertTrue(listener.contains("ClientPlayerNetworkEvent.LoggingOut"));
        assertTrue(listener.contains("LevelEvent.Unload"));
    }

    @Test
    void effectLifeAndReferencedTexturesAreSafe() throws Exception {
        String sprite = source("com/mohistmc/academy/client/effect/SpriteEffectEntity.java");
        assertTrue(sprite.contains("Math.max(2, ticks)"));
        assertTrue(sprite.contains("nextInt(Math.max(1, life / 2))"));
        Path effects = Path.of("src/main/resources/assets/academy/textures/effects");
        assertTrue(Files.isRegularFile(effects.resolve("railgun.png")));
        assertTrue(Files.isRegularFile(effects.resolve("mdshield.png")));
        assertTrue(Files.isRegularFile(effects.resolve("screen_mask.png")));
    }
}
