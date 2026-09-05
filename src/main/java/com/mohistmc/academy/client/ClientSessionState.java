package com.mohistmc.academy.client;

import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.client.gui.LocationTeleportGui;
import com.mohistmc.academy.client.gui.NotifyOverlay;
import com.mohistmc.academy.client.gui.ScreenMaskOverlay;
import com.mohistmc.academy.client.gui.tutorial.RecipeViews;

/** Owns teardown of state that must never cross a connection or client-level boundary. */
public final class ClientSessionState {
    private ClientSessionState() {}

    /** Idempotent and strictly local: safe after the connection has already closed. */
    public static void reset() {
        SkillInputClientState.clear();
        KeyInputHandler.resetClientSession();
        ChargingHudOverlay.resetClientSession();
        com.mohistmc.academy.client.sound.AbilityLoopSoundManager.resetClientSession();
        com.mohistmc.academy.client.sound.MachineLoopSoundManager.resetClientSession();
        ClientPacketBridge.resetClientSession();
        MediaPlayerManager.stop();
        TerminalInstallProgress.resetClientSession();
        LocationTeleportGui.resetClientSession();
        AcademyBaseUI.clearNodeCache();
        NotifyOverlay.resetClientSession();
        ScreenMaskOverlay.resetClientSession();
        RecipeViews.resetClientSession();
    }
}
