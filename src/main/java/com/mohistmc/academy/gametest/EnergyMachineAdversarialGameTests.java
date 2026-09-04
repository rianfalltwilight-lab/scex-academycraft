package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.entity.DevAdvancedBlockEntity;
import com.mohistmc.academy.world.block.entity.DevAdvancedSubBlockEntity;
import com.mohistmc.academy.world.block.entity.DevNormalBlockEntity;
import com.mohistmc.academy.world.block.entity.DevNormalSubBlockEntity;
import com.mohistmc.academy.world.block.entity.NodeBasicBlockEntity;
import com.mohistmc.academy.world.block.entity.PhaseGenBlockEntity;
import com.mohistmc.academy.world.block.entity.SolarGenBlockEntity;
import com.mohistmc.academy.energy.impl.WiWorldData;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Corrupt-NBT and energy-conservation boundaries for developer multiblocks. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class EnergyMachineAdversarialGameTests {
    private static final String EMPTY = "empty";

    private EnergyMachineAdversarialGameTests() {}

    @GameTest(template = EMPTY)
    public static void copiedRemoteDeveloperProxyCannotDestroyUnrelatedMain(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos mainPos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos copiedProxyPos = helper.absolutePos(new BlockPos(10, 1, 10));
        level.setBlock(mainPos, AcademyBlocks.DEV_ADVANCED.get().defaultBlockState(), 3);
        level.setBlock(copiedProxyPos, AcademyBlocks.DEV_ADVANCED_SUB.get().defaultBlockState(), 3);

        UUID copiedId = UUID.randomUUID();
        DevAdvancedBlockEntity main = (DevAdvancedBlockEntity) level.getBlockEntity(mainPos);
        DevAdvancedSubBlockEntity proxy = (DevAdvancedSubBlockEntity) level.getBlockEntity(copiedProxyPos);
        if (main == null || proxy == null) {
            helper.fail("developer identity fixture did not create block entities"); return;
        }
        main.setStructureId(copiedId);
        proxy.setStructureId(copiedId);
        proxy.setMainPos(mainPos);

        level.destroyBlock(copiedProxyPos, false);
        if (!level.getBlockState(mainPos).is(AcademyBlocks.DEV_ADVANCED.get())) {
            helper.fail("copied remote developer proxy destroyed an unrelated main"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void malformedDeveloperIdsAndFractionalEnergyRemainSafe(GameTestHelper helper) {
        CompoundTag malformed = new CompoundTag();
        malformed.putString("structureId", "not-a-uuid");
        var registries = helper.getLevel().registryAccess();

        DevNormalBlockEntity normal = new DevNormalBlockEntity(BlockPos.ZERO,
                AcademyBlocks.DEV_NORMAL.get().defaultBlockState());
        DevAdvancedBlockEntity advanced = new DevAdvancedBlockEntity(BlockPos.ZERO,
                AcademyBlocks.DEV_ADVANCED.get().defaultBlockState());
        DevNormalSubBlockEntity normalSub = new DevNormalSubBlockEntity(BlockPos.ZERO,
                AcademyBlocks.DEV_NORMAL_SUB.get().defaultBlockState());
        DevAdvancedSubBlockEntity advancedSub = new DevAdvancedSubBlockEntity(BlockPos.ZERO,
                AcademyBlocks.DEV_ADVANCED_SUB.get().defaultBlockState());
        normal.loadWithComponents(malformed, registries);
        advanced.loadWithComponents(malformed, registries);
        normalSub.loadWithComponents(malformed, registries);
        advancedSub.loadWithComponents(malformed, registries);
        if (normal.getStructureId() != null || advanced.getStructureId() != null
                || normalSub.getStructureId() != null || advancedSub.getStructureId() != null) {
            helper.fail("malformed developer UUID survived sanitization"); return;
        }

        normal.setEnergy(10);
        advanced.setEnergy(10);
        double normalRemainder = normal.injectEnergy(1.75);
        double advancedRemainder = advanced.injectEnergy(1.75);
        double invalidRemainder = advanced.injectEnergy(-5);
        if (normal.getEnergyStored() != 11 || advanced.getEnergyStored() != 11
                || Math.abs(normalRemainder - .75) > 1.0e-9
                || Math.abs(advancedRemainder - .75) > 1.0e-9
                || invalidRemainder != -5 || advanced.getEnergyStored() != 11) {
            helper.fail("developer receiver lost fractional energy or accepted invalid input"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void removingMachineOrNodeCannotTransferLinkToSameCoordinateReplacement(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos nodePos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos machinePos = helper.absolutePos(new BlockPos(5, 1, 2));
        level.setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(machinePos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        NodeBasicBlockEntity node = (NodeBasicBlockEntity) level.getBlockEntity(nodePos);
        PhaseGenBlockEntity phase = (PhaseGenBlockEntity) level.getBlockEntity(machinePos);
        if (node == null || phase == null || !WirelessSystem.linkGenerator(level, node, phase, false, "")) {
            helper.fail("wireless removal fixture could not establish generator link"); return;
        }

        level.destroyBlock(machinePos, false);
        level.setBlock(machinePos, AcademyBlocks.SOLAR_GEN.get().defaultBlockState(), 3);
        SolarGenBlockEntity replacementGenerator = (SolarGenBlockEntity) level.getBlockEntity(machinePos);
        WiWorldData data = WiWorldData.getNonCreate(level);
        if (replacementGenerator == null || data == null
                || data.getNodeConnection(replacementGenerator) != null) {
            helper.fail("same-coordinate generator replacement inherited removed machine link"); return;
        }

        if (!WirelessSystem.linkGenerator(level, node, replacementGenerator, false, "")) {
            helper.fail("replacement generator could not establish a fresh explicit link"); return;
        }
        level.destroyBlock(nodePos, false);
        level.setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        NodeBasicBlockEntity replacementNode = (NodeBasicBlockEntity) level.getBlockEntity(nodePos);
        if (replacementNode == null || data.getExistingNodeConnection(replacementNode) != null
                || data.getNodeConnection(replacementGenerator) != null) {
            helper.fail("same-coordinate node replacement inherited removed node topology"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void unownedLegacyNodeRejectsOrdinaryVisitorClaim(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlock(pos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        NodeBasicBlockEntity node = (NodeBasicBlockEntity) helper.getLevel().getBlockEntity(pos);
        var first = helper.makeMockServerPlayerInLevel();
        if (node == null || node.getOwnerUUID() != null) {
            helper.fail("legacy node ownership fixture was not unowned"); return;
        }
        if (node.claimLegacyOwnerIfAbsent(first)
                || node.getOwnerUUID() != null) {
            helper.fail("an ordinary visitor claimed an unowned legacy node"); return;
        }
        helper.succeed();
    }
}
