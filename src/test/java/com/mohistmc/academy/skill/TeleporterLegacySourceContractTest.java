package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Differential tripwires for quirks verified against the fixed final 1.12.2 source. */
final class TeleporterLegacySourceContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy").resolve(relative));
    }

    @Test
    void threateningIgnoresWallsForLivingTargetsWhileFleshDoesNot() throws Exception {
        String threatening = source("skill/ability/teleporter/ThreateningTeleportEffect.java");
        String flesh = source("skill/ability/teleporter/FleshRippingEffect.java");
        assertTrue(threatening.contains("new AABB(start, intended).inflate(1)"));
        assertTrue(threatening.contains("closest == null ? new Target(end, null)"));
        assertTrue(flesh.contains("new AABB(start, end).inflate(1)"));
        assertFalse(flesh.contains("new AABB(start, intended).inflate(1)"));
        assertTrue(threatening.contains("instanceof EnderDragonPart"));
        assertTrue(flesh.contains("instanceof EnderDragonPart"));
    }

    @Test
    void penetrateAndMarkKeepTheirExactLegacyScanners() throws Exception {
        String penetrate = source("skill/ability/teleporter/PenetrateTeleportEffect.java");
        String input = source("client/KeyInputHandler.java");
        String renderer = source("client/render/LegacyAbilityPresentationRenderer.java");
        String mark = source("skill/ability/teleporter/MarkTeleportEffect.java");
        assertTrue(penetrate.contains("final double step = .8"));
        assertTrue(penetrate.contains("stage = 1") && penetrate.contains("stage = 2"));
        assertTrue(penetrate.contains("++counter > 4"));
        assertTrue(penetrate.contains("stage != 1"));
        assertTrue(penetrate.contains("Vec3 cursor = player.position()"),
                "final 1.12.2 penetration scan starts at the player's feet");
        assertTrue(input.contains("MouseScrollingEvent")
                        && input.contains("getPenetrateDistance")
                        && renderer.contains("KeyInputHandler.getPenetrateDistance(data)"),
                "final 1.12.2 wheel-adjusted penetration distance must reach preview and authority");
        assertTrue(mark.contains("Math.min((ticks+1)*2.0"));
        assertTrue(mark.contains("FlashingTargeting.destination"));
        assertTrue(mark.contains("distance<3"));
    }

    @Test
    void flashingUsesOnePitchSafeBasisEverywhereAndCleansStaticSessions() throws Exception {
        String target = source("skill/ability/teleporter/FlashingTargeting.java");
        String session = source("skill/ability/teleporter/FlashingSessionManager.java");
        String input = source("client/KeyInputHandler.java");
        String renderer = source("client/render/LegacyAbilityPresentationRenderer.java");
        assertTrue(target.contains("double yaw = Math.toRadians(player.getYRot())"));
        assertTrue(target.contains("public static Vec3 direction"));
        assertTrue(session.contains("FlashingTargeting.direction(player, direction)"));
        assertTrue(input.contains("FlashingTargeting.direction(mc.player, flashingHeldDirection)"));
        assertTrue(renderer.contains("FlashingTargeting.direction(mc.player,KeyInputHandler.getFlashingHeldDirection())"));
        assertTrue(session.contains("ServerStoppingEvent") && session.contains("ACTIVE.clear()"));
        assertTrue(input.contains("mc.options.keyLeft.matches(keyCode, scanCode)")
                        && input.contains("mc.options.keyRight.matches(keyCode, scanCode)")
                        && input.contains("mc.options.keyUp.matches(keyCode, scanCode)")
                        && input.contains("mc.options.keyDown.matches(keyCode, scanCode)"),
                "final 1.12.2 Flashing must honor remapped Minecraft movement keys");
        assertFalse(input.contains("event.getKey() == GLFW.GLFW_KEY_A"),
                "Flashing must not regress to hard-coded WASD");
    }

    @Test
    void shiftUsesGenericLegacyLivingSelectorAndCasterSoundOrigin() throws Exception {
        String shift = source("skill/ability/teleporter/ShiftTpEffect.java");
        assertTrue(shift.contains("for (Entity target"));
        assertTrue(shift.contains("instanceof EnderDragonPart"));
        assertTrue(shift.contains("level.playSound(null, origin.x, origin.y, origin.z"));
        assertTrue(shift.contains("(1 + attacked) * .002f"));
        assertTrue(shift.contains("new ItemEntity(level")
                        && shift.contains("remainingAfterOneUse(before"),
                "final 1.12.2 ShiftTP must drop an unplaceable remote block and consume exactly one");
    }

    @Test
    void locationScreenUsesOfficialAssetsAndLegacyPreflightInformation() throws Exception {
        String screen = source("client/gui/LocationTeleportGui.java");
        String action = source("network/LocationTeleportActionPacket.java");
        assertTrue(screen.contains("textures/guis/icons/icon_location_on.png"));
        assertTrue(screen.contains("textures/guis/icons/icon_clear.png"));
        assertTrue(screen.contains("textures/guis/check.png"));
        assertTrue(screen.contains("name.setMaxLength(16)"));
        assertTrue(screen.contains("200 - 50 * exp"));
        assertTrue(screen.contains("cross && exp <= .8f"));
        assertTrue(screen.contains("ac.gui.loctele.err_exp"));
        assertTrue(screen.contains("ac.gui.loctele.err_cp"));
        assertTrue(screen.contains("mc.player.playSound(AcademySounds.TP_TP.value(), .5f, 1f)"),
                "final 1.12.2 Location Teleport sound belongs to the local button click");
        assertTrue(action.contains("legacyProficiencyIncrement(distance)"));
        assertFalse(action.contains("target.playSound"),
                "final 1.12.2 must not broadcast the Location Teleport button sound from the server");
    }
}
