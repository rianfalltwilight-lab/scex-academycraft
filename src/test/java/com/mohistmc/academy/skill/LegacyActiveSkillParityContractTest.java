package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Narrow tripwires for old context semantics which previously regressed into one-shot approximations. */
class LegacyActiveSkillParityContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy/").resolve(relative));
    }

    @Test void heldTeleporterContextsCannotRegressToOneShotEffects() throws Exception {
        for (String file : List.of("ShiftTpEffect.java", "PenetrateTeleportEffect.java",
                "FleshRippingEffect.java", "ThreateningTeleportEffect.java")) {
            String code = source("skill/ability/teleporter/" + file);
            assertTrue(code.contains("implements ChargingSkillEffect"), file);
            assertTrue(code.contains("tryRelease("), file);
        }
    }

    @Test void dynamicOneShotsOnlySettleAfterAReportedCommit() throws Exception {
        String effect = source("skill/SkillEffect.java");
        String packet = source("network/UseSkillPacket.java");
        String data = source("skill/PlayerAbilityData.java");
        assertTrue(effect.contains("executeAndReport"));
        assertTrue(packet.contains("effect.executeAndReport"));
        assertTrue(data.contains("effect != null && !effect.appliesBaseResourceCost()) return true"));
        assertTrue(packet.indexOf("effect.executeAndReport") < packet.indexOf("data.setCooldown"));
    }

    @Test void bothReleasePathsHonorSkillOwnedCooldownSettlement() throws Exception {
        String tick = source("skill/SkillEventHandler.java");
        String keyUp = source("network/SkillKeyUpPacket.java");
        assertTrue(tick.contains("shouldApplyCooldownAfterRelease"));
        assertTrue(keyUp.contains("shouldApplyCooldownAfterRelease"));
    }

    @Test void correctedHeldAndVectorRulesRemainExplicit() throws Exception {
        String shield = source("skill/ability/meltdowner/LightShieldEffect.java");
        String scatter = source("skill/ability/meltdowner/ScatterBombEffect.java");
        String mag = source("skill/ability/MagManipEffect.java");
        String accel = source("skill/ability/vecmanip/VecAccelEffect.java");
        String ground = source("skill/ability/vecmanip/GroundShockEffect.java");
        assertTrue(shield.contains("lerpf(5, 3, state.exp), lerpf(50, 30, state.exp)"));
        assertTrue(scatter.contains("onChargingAbort") && scatter.contains("fire(player, data, state)"));
        assertTrue(mag.contains("getSessionTimeoutTicks(PlayerAbilityData data){return Integer.MAX_VALUE;"));
        assertTrue(accel.contains("directionFromRotation(player.getXRot() - 10.0F"));
        assertTrue(ground.contains("LegacyPlotter") && ground.contains("{1.0, 0.7, 0.7, 0.3, 0.3}"));
    }

    @Test void flashingKeepsTheLegacyAerialDestinationInsteadOfRequiringGround() throws Exception {
        String session = source("skill/ability/teleporter/FlashingSessionManager.java");
        String targeting = source("skill/ability/teleporter/FlashingTargeting.java");
        String input = source("client/KeyInputHandler.java");
        String renderer = source("client/render/LegacyAbilityPresentationRenderer.java");
        assertTrue(session.contains("FlashingTargeting.destination"));
        assertTrue(targeting.contains("result = end"), "unobstructed flashing must retain the eye-height air endpoint");
        assertTrue(targeting.contains("case UP -> y += 1.8") && targeting.contains("y -= 1.25"));
        assertTrue(!targeting.contains("below()).isAir"), "a ground-only check breaks the old aerial mobility");
        assertTrue(input.contains("FlashingTargeting.destination") && renderer.contains("FlashingTargeting.destination"));
        assertTrue(input.contains("FlashingTargeting.direction") && renderer.contains("FlashingTargeting.direction"));
    }
}
