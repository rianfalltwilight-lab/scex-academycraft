package com.mohistmc.academy.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Guards the non-obvious 1.12.2 wind-generator invariants against regressions. */
class WindGeneratorSourceContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/mohistmc/academy").resolve(relative));
    }

    @Test
    void legacyStructureAndBandwidthBoundariesRemainExplicit() throws Exception {
        String block = source("world/block/WindGenBase.java");
        String entity = source("world/block/entity/WindGenBaseBlockEntity.java");
        assertTrue(block.contains("MIN_PILLARS = 8"));
        assertTrue(block.contains("MAX_PILLARS = 40"));
        assertTrue(block.contains("middleComplete = mainHeight >= MIN_PILLARS"));
        assertTrue(block.contains("mainHeight > MAX_PILLARS"));
        assertTrue(entity.contains("Math.min((int) storedEnergy, (int) getBandwidth())"));
        assertTrue(entity.contains("return 300"));
        assertTrue(entity.contains("now - lastClientSyncTick >= 10"));
        assertTrue(entity.contains("Block.UPDATE_CLIENTS"));
        assertFalse(entity.contains("Block.UPDATE_ALL"));
    }

    @Test
    void headUsesStructuralProxiesAndCachedClearanceWithoutEnergyCapability() throws Exception {
        String main = source("world/block/WindGenMain.java");
        String entity = source("world/block/entity/WindGenMainBlockEntity.java");
        String mod = source("AcademyCraft.java").replaceAll("\\s+", "");
        String renderer = source("client/block/entity/render/WindGenFanRender.java");
        assertTrue(main.contains("proxyPositions") && main.contains("hasCompleteProxySet"));
        assertTrue(entity.contains("now - lastRefreshTick >= 10"));
        assertTrue(entity.contains("boolean nowWorking = hasBase && installed && clear"));
        assertTrue(entity.contains("for (int dy = -7; dy <= 7; dy++)"));
        assertFalse(entity.contains("implements IFEnergyStorage"));
        assertFalse(mod.contains("AcademyBlockEntities.WINDGEN_MAIN.get(),(be,side)->newcom.mohistmc.academy.capability.ForgeEnergyView"));
        assertTrue(renderer.contains("getGameTime() + p_112308_"));
        assertTrue(renderer.contains("LEGACY_PROXY_BACK_OFFSET = 0.48f"));
        assertTrue(renderer.contains("case EAST -> Axis.XN"));
        assertTrue(renderer.contains("case WEST -> Axis.XP"));
        assertTrue(renderer.contains("case SOUTH -> Axis.ZN"));
        assertTrue(renderer.contains("case NORTH -> Axis.ZP"));
        assertFalse(renderer.contains("rH +="));
    }

    @Test
    void hiddenStructurePartsIdentifyAsTheirLogicalMachine() throws Exception {
        assertTrue(source("world/block/WindGenFan.java")
                .contains("return \"block.academy.windgen_main\""));
        assertTrue(source("world/block/WindGenBaseSubBlock.java")
                .contains("return \"block.academy.windgen_base\""));
    }
}
