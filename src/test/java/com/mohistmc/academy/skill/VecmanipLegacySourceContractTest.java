package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class VecmanipLegacySourceContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy/" + relative));
    }

    @Test void directedBlastKeepsLegacyTargetSphereAndHalfOpenBlockCube() throws Exception {
        String source = source("skill/ability/vecmanip/DirBlastEffect.java");
        assertTrue(source.contains("instanceof LivingEntity || e instanceof EnderDragonPart"));
        assertTrue(source.contains("distanceToSqr(finalPos) <= AOE_RANGE * AOE_RANGE"));
        assertTrue(source.contains("for (int dx = -3; dx < 3; dx++)"));
        assertTrue(source.contains("e.setPos(e.getX(), e.getY() + .1"));
        assertTrue(source.contains("waveRings(level,player.getEyePosition().lerp(finalPos,.7)"));
        assertFalse(source.contains("EffectHelper.glowBurst"));
    }

    @Test void groundShockRetainsPlayedRadiansFootprintAndSoundPitch() throws Exception {
        String source = source("skill/ability/vecmanip/GroundShockEffect.java");
        assertTrue(source.contains("Math.cos(90.0)"));
        assertTrue(source.contains("Math.sin(90.0)"));
        assertTrue(source.contains("SoundSource.PLAYERS, 2.0f, 1.0f"));
    }

    @Test void stormWingDoesNotEnableASecondVanillaFlightController() throws Exception {
        String source = source("skill/ability/vecmanip/StormWingEffect.java");
        String visual = source("entity/StormWingVisualEntity.java");
        String renderer = source("client/renderer/StormWingVisualRenderer.java");
        assertFalse(source.contains("getAbilities().flying = true"));
        assertTrue(source.contains("new Vec3(forward.z, 0, -forward.x)"));
        assertFalse(source.contains("isShiftKeyDown"));
        assertTrue(source.contains("current.y + .078"));
        assertTrue(source.contains("exp == 1.0f"));
        assertTrue(source.contains("AcademyEntities.STORM_WING_VISUAL"));
        assertTrue(visual.contains("owner.getY() + 1.6"));
        assertTrue(renderer.contains("TRANSFORMS") && renderer.contains("tornado_ring.png"));
    }

    @Test void plasmaUsesTheLegacyLivingFamilyAndEntityEndpoint() throws Exception {
        String source = source("skill/ability/vecmanip/PlasmaCannonEffect.java");
        assertTrue(source.contains("entity instanceof LivingEntity || entity instanceof EnderDragonPart"));
        assertTrue(source.contains("entity.getEyeHeight() * .6"));
        assertTrue(source.contains("AcademySounds.playSound(player.serverLevel(), player.getX()"));
    }

    @Test void heldDefensesUseScreenRipplesWhileHitsUseOfficialWaveGeometry() throws Exception {
        String overlay = source("client/gui/LegacyVectorWaveOverlay.java");
        String deviation = source("skill/ability/vecmanip/VecDeviationEffect.java");
        String reflection = source("skill/ability/vecmanip/VecReflectionEffect.java");
        String renderer = source("client/renderer/LegacyFieldEffectRenderer.java");
        assertTrue(overlay.contains("glow_circle.png"));
        assertTrue(overlay.contains("averageSize = \"vec_reflection\".equals(skill) ? 110f : 100f"));
        assertFalse(deviation.contains("shockwaveRing"));
        assertFalse(reflection.contains("shockwaveRing"));
        assertTrue(renderer.contains("TEXTURED_WAVE"));
        assertTrue(renderer.contains("entityTranslucentEmissive(GLOW_CIRCLE)"));
    }

    @Test void vectorHandsAndGroundCameraUseTheLegacyTimelines() throws Exception {
        String controller = source("client/LegacyVecmanipClientController.java");
        assertTrue(controller.contains("pitchDelta * .2f"));
        assertTrue(controller.contains("groundSlamTicks = 4"));
        assertTrue(controller.contains("getXRot() + 3.4f"));
        assertTrue(controller.contains("vectorPunchStartedNanos"));
        assertTrue(controller.contains("Axis.XP.rotationDegrees(-20 * t)"));
    }
}
