package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.energy.impl.WiWorldData;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.CatEngine;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import com.mohistmc.academy.world.block.entity.CatEngineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real server interaction coverage for the 1.0.7 Toast-and-Cat generator flow. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class CatEngineGameTests {
    private CatEngineGameTests() {}

    @GameTest(template = "empty")
    public static void legacyBufferGenerationAndBandwidthAreRestored(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlock(pos, AcademyBlocks.CAT_ENGINE.get().defaultBlockState(), 3);
        if (!(helper.getLevel().getBlockEntity(pos) instanceof CatEngineBlockEntity cat)) {
            helper.fail("Cat Engine fixture did not create a block entity");
            return;
        }

        cat.tick(helper.getLevel(), pos, helper.getLevel().getBlockState(pos));
        if (cat.getMaxStorage() != 2_000 || cat.getBandwidth() != 200
                || cat.getStoredEnergy() != 500 || cat.getThisTickGeneration() != 500) {
            helper.fail("Cat Engine no longer matches the 1.0.7 2000/200/500 energy contract");
            return;
        }
        if (cat.getProvidedEnergy(200) != 200 || cat.getStoredEnergy() != 300) {
            helper.fail("Cat Engine buffer did not deliver energy without creating or losing it");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rightClickLinksAndUnlinksAStandaloneNode(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = helper.makeMockServerPlayerInLevel();
        BlockPos catPos = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos nodePos = catPos.east(3);
        level.setBlock(catPos, AcademyBlocks.CAT_ENGINE.get().defaultBlockState(), 3);
        level.setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(catPos) instanceof CatEngineBlockEntity cat)
                || !(level.getBlockEntity(nodePos) instanceof BaseNodeBlockEntity node)) {
            helper.fail("Cat Engine/node fixtures failed to place");
            return;
        }
        if (WirelessSystem.getNetwork(level, node) != null) {
            helper.fail("Standalone-node fixture unexpectedly belongs to a Matrix network");
            return;
        }

        CatEngine block = (CatEngine) AcademyBlocks.CAT_ENGINE.get();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(catPos),
                net.minecraft.core.Direction.UP, catPos, false);
        block.useWithoutItem(level.getBlockState(catPos), level, catPos, player, hit);
        WiWorldData data = WiWorldData.getNonCreate(level);
        if (data == null || data.getNodeConnection(cat) == null || !cat.isLinked()) {
            helper.fail("Cat Engine right-click did not link to a standalone nearby node");
            return;
        }

        // Exercise the real connection tick: the node must receive buffered
        // energy through the same path used by ordinary generators.
        cat.tick(level, catPos, level.getBlockState(catPos));
        data.tick();
        if (node.getEnergy() <= 0) {
            helper.fail("Linked Cat Engine did not feed its node");
            return;
        }

        block.useWithoutItem(level.getBlockState(catPos), level, catPos, player, hit);
        data.tick(); // unlink is deliberately committed at the connection tick boundary
        if (data.getNodeConnection(cat) != null || cat.isLinked()) {
            helper.fail("Second Cat Engine right-click did not unlink only this generator");
            return;
        }

        block.useWithoutItem(level.getBlockState(catPos), level, catPos, player, hit);
        if (data.getNodeConnection(cat) == null) {
            helper.fail("Cat Engine could not relink for its removal lifecycle check");
            return;
        }
        level.destroyBlock(catPos, false);
        if (data.getNodeConnection(cat) != null) {
            helper.fail("Removing Cat Engine left a stale generator edge");
            return;
        }
        helper.succeed();
    }
}
