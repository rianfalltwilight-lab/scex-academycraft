package com.mohistmc.academy.skill;

import com.mohistmc.academy.skill.ability.electromaster.ElectromasterRules;
import com.mohistmc.academy.config.LegacyMetalIdRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElectromasterRulesTest {

    @Test void hostileChargingCommitIsBoundedBySimulation() {
        assertEquals(0, ElectromasterRules.committedCharge(10, 0));
        assertEquals(1, ElectromasterRules.committedCharge(10, 1));
        assertEquals(0, ElectromasterRules.committedCharge(10, -1));
        assertEquals(10, ElectromasterRules.committedCharge(10, 1000));
        assertEquals(0, ElectromasterRules.committedCharge(-5, 3));
    }
    @Test void bodyIntensifyOnlyConsumesThroughLegacyFortyTickWindow() {
        assertTrue(ElectromasterRules.shouldConsumeBodyIntensifyTick(40));
        assertFalse(ElectromasterRules.shouldConsumeBodyIntensifyTick(41));
        assertEquals(40, ElectromasterRules.bodyIntensifyEffectiveTicks(100));
        assertEquals(30.0 / 18.0, ElectromasterRules.bodyIntensifyProbability(40), 1e-9);
    }

    @Test void thunderClapKeepsExactLegacyChargeCurve() {
        assertTrue(ElectromasterRules.shouldConsumeThunderClapTick(40));
        assertFalse(ElectromasterRules.shouldConsumeThunderClapTick(41));
        assertEquals(1.0f, ElectromasterRules.thunderClapChargeFactor(40), 1e-6);
        assertEquals(1.0666667f, ElectromasterRules.thunderClapChargeFactor(60), 1e-6);
    }

    @Test void mineDetectKeepsLegacyRangeCapAndAdvancedColorIndices() {
        assertEquals(15.0f, ElectromasterRules.mineDetectRange(0), 1e-6);
        assertEquals(22.5f, ElectromasterRules.mineDetectRange(.5f), 1e-6);
        assertEquals(28.0f, ElectromasterRules.mineDetectRange(1), 1e-6);
        assertEquals(1, ElectromasterRules.mineDetectColorLevel(false, false));
        assertEquals(2, ElectromasterRules.mineDetectColorLevel(true, false));
        assertEquals(3, ElectromasterRules.mineDetectColorLevel(true, true));
    }

    @Test void thunderBoltAoeIsTheLegacyEightBlockSphere() {
        assertTrue(ElectromasterRules.thunderBoltAoeContains(8, 0, 0));
        assertFalse(ElectromasterRules.thunderBoltAoeContains(8, 8, 0));
        assertFalse(ElectromasterRules.thunderBoltAoeContains(4.7, 4.7, 4.7));
    }

    @Test void thunderClapFadesDamageAndWalkSpeedLikeTheLegacyContexts() {
        assertEquals(72, ElectromasterRules.thunderClapDamageAtDistance(72, 0, 30), 1e-6);
        assertEquals(36, ElectromasterRules.thunderClapDamageAtDistance(72, 15, 30), 1e-6);
        assertEquals(0, ElectromasterRules.thunderClapDamageAtDistance(72, 30, 30), 1e-6);
        assertEquals(.1f, ElectromasterRules.thunderClapWalkSpeed(.1f, 0), 1e-6);
        assertEquals(.001f, ElectromasterRules.thunderClapWalkSpeed(.1f, 1), 1e-6);
    }

    @Test void legacyMetalConfigurationNamesMapToModernRegistryIds() {
        assertEquals("minecraft:powered_rail", LegacyMetalIdRules.blockId("golden_rail"));
        assertEquals("minecraft:iron_block", LegacyMetalIdRules.blockId("iron_block"));
        assertEquals("#forge:storage_blocks/iron", LegacyMetalIdRules.blockId("#forge:storage_blocks/iron"));
        assertEquals("minecraft:minecart", LegacyMetalIdRules.entityId("MinecartRideable"));
        assertEquals("academy:mag_hook", LegacyMetalIdRules.entityId("academy-craft.ac_Entity_EntityMagHook"));
        assertEquals("minecraft:iron_golem", LegacyMetalIdRules.entityId("VillagerGolem"));
    }

    @Test void rejectedEnergyReceiveIsNotEffective() {
        assertFalse(ElectromasterRules.acceptedCharge(0));
        assertTrue(ElectromasterRules.acceptedCharge(1));
    }
}
