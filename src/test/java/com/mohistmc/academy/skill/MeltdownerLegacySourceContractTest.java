package com.mohistmc.academy.skill;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class MeltdownerLegacySourceContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy/", relative));
    }

    @Test
    void radiationIsADeferredMarkAndOnlyWrapsLegacyMdDamageHelperSkills() throws Exception {
        String helper = source("skill/passive/PassiveDamageHelper.java");
        String runtime = source("skill/passive/RadiationIntensifyRuntime.java");
        assertTrue(helper.indexOf("AcademyDamageHelper.hurt")
                < helper.indexOf("RadiationIntensifyRuntime.mark"));
        assertTrue(runtime.contains("Math.max(60, casterData.getInt(MARK_TICKS))"));
        assertTrue(runtime.contains("event.setAmount(event.getAmount() * rate)"));

        for (String file : new String[]{"ElectronBombEffect.java", "ScatterBombEffect.java",
                "LightShieldEffect.java", "RayBarrageEffect.java", "JetEngineRuntime.java",
                "ElectronMissileEffect.java"}) {
            assertTrue(source("skill/ability/meltdowner/" + file)
                    .contains("PassiveDamageHelper.meltdownerAttack"), file);
        }
        // 1.0.7 Meltdowner.scala used RangedRayDamage/ctx.attack directly,
        // not MDDamageHelper, for both the main and reflected rays.
        assertFalse(source("skill/ability/meltdowner/MeltdownerEffect.java")
                .contains("PassiveDamageHelper.meltdownerAttack"));
    }

    @Test
    void electronBombRetainsCallbackTimingAndTwoStageRay() throws Exception {
        String bomb = source("skill/ability/meltdowner/ElectronBombEffect.java");
        String scatter = source("skill/ability/meltdowner/ScatterBombEffect.java");
        assertTrue(bomb.contains("ball.remaining == 2"));
        assertTrue(bomb.contains("lookingDestination(player, RANGE)"));
        assertTrue(bomb.contains("firstEntity(player, from, destination)"));
        assertTrue(bomb.contains("!(candidate instanceof MdBallEntity)"));
        assertFalse(bomb.contains("MD_BALLSHOOT"));
        assertTrue(scatter.contains("int autoCount = exp > .5F"));
        assertTrue(scatter.contains("getEntitiesOfClass(Mob.class"));
        assertTrue(scatter.contains("look.xRot(pitch).yRot(yaw).scale(RAY_RANGE)"));
    }

    @Test
    void shieldBarrageAndJetRetainTheirLegacyTargetAndTickOrdering() throws Exception {
        String shield = source("skill/ability/meltdowner/LightShieldEffect.java");
        assertTrue(shield.contains("boolean maintained = pay"));
        assertTrue(shield.indexOf("boolean maintained = pay") < shield.indexOf("DynamicSkillRules.addExp"));
        assertFalse(shield.contains("instanceof net.minecraft.world.entity.projectile.Projectile"));
        assertTrue(shield.contains("direct == null || frontal(player, direct)"));
        assertTrue(shield.contains("if (left == 0) event.setCanceled(true)"));

        String barrage = source("skill/ability/meltdowner/RayBarrageEffect.java");
        assertTrue(barrage.contains("player.serverLevel().getEntities(player, legacyFanBounds(player)"));
        assertTrue(barrage.contains("LookingHit plain = lookingHit(player)"));

        String jet = source("skill/ability/meltdowner/JetEngineRuntime.java");
        String jetEffect = source("skill/ability/meltdowner/JetEngineEffect.java");
        assertTrue(jet.contains("LIFETIME_TICKS = 16"));
        assertTrue(jet.contains("scale(1d / TRAVEL_DIVISOR)"));
        assertFalse(jet.contains("invulnerableTime=0"));
        assertTrue(jetEffect.contains("Vec3 destination = legacyDestination(player)"));
        assertFalse(jetEffect.contains("add(0, 1.65"));
    }

    @Test
    void mineRaySeparatesContinuousFifteenBlockPresentationFromMiningRange() throws Exception {
        String mine = source("skill/ability/meltdowner/AbstractMineRayEffect.java");
        String effects = source("world/effect/EffectHelper.java");
        String beam = source("entity/MeltdownBeamEntity.java");
        assertTrue(mine.contains("EffectHelper.startFollowingMineRay"));
        assertFalse(mine.contains("EffectHelper.mineRay(level,from,beamEnd"));
        assertTrue(mine.contains("boolean maintained=DynamicSkillRules.tryPay"));
        assertTrue(mine.contains("Items.IRON_PICKAXE:Items.NETHERITE_PICKAXE"));
        assertTrue(effects.contains("MeltdownBeamEntity.SMALL"));
        assertTrue(beam.contains("entityData.set(LEN, 15f)"));
        assertTrue(beam.contains("setFollowingPlayer"));
    }

    @Test
    void coreRayKeepsFixedRadiusGenericTargetsAndLegacyReflectionOwnership() throws Exception {
        String core = source("skill/ability/meltdowner/MeltdownerEffect.java");
        assertTrue(core.contains("double radius=lerpf(2,3,exp);"));
        assertFalse(core.contains("lerpf(2,3,exp)*rate"));
        assertTrue(core.contains("List<Entity> targets"));
        assertTrue(core.contains("AcademyDamageHelper.hurt(attacker,nearest"));
        assertTrue(core.contains("meltdowner_charge_slow"));

        String beam = source("entity/MeltdownBeamEntity.java");
        String effects = source("world/effect/EffectHelper.java");
        assertTrue(beam.contains("DEFAULT_LIFE = 50"));
        assertTrue(effects.contains("mdRay(l, from, to, 50)"));
    }
}
