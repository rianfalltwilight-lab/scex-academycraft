package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.energy.impl.WiWorldData;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.network.FreqTransmitterSessionManager;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.terminal.AppRegistry;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-authoritative integration coverage for the legacy two-step app flow. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class FreqTransmitterGameTests {
    private FreqTransmitterGameTests() {}

    @GameTest(template = "empty")
    public static void pendingOpenCanBeCanceledByItsClientRequestNonce(GameTestHelper helper) {
        BlockPos source = helper.absolutePos(new BlockPos(2, 1, 2));
        ServerPlayer player = transmitterPlayer(helper, source);
        UUID requestNonce = UUID.randomUUID();
        if (!requestNonce.equals(FreqTransmitterSessionManager.open(player, requestNonce))
                || !FreqTransmitterSessionManager.cancel(player, requestNonce)
                || FreqTransmitterSessionManager.selectBlock(player, requestNonce, source)
                    != FreqTransmitterSessionManager.SelectionResult.NO_SESSION) {
            helper.fail("pre-response cancel did not disarm the exact OPEN session");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void matrixLinksOnePasswordAuthorizedOwnedNode(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos matrixPos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos nodePos = matrixPos.east(2);
        level.setBlock(matrixPos, AcademyBlocks.MATRIX.get().defaultBlockState(), 3);
        level.setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(matrixPos) instanceof MatrixBlockEntity matrix)
                || !(level.getBlockEntity(nodePos) instanceof BaseNodeBlockEntity node)) {
            helper.fail("frequency transmitter fixtures failed to place");
            return;
        }

        matrix.setOwnerUUID(UUID.randomUUID());
        matrix.setInitialized(true);
        matrix.setSSID("legacy-matrix");
        matrix.setPassword("matrix-pass");
        matrix.getItems().set(MatrixBlockEntity.CORE_SLOT, new ItemStack(AcademyItems.MAT_CORE_0.get()));
        for (int slot = MatrixBlockEntity.PLATE_SLOT_0; slot <= MatrixBlockEntity.PLATE_SLOT_2; slot++) {
            matrix.getItems().set(slot, new ItemStack(AcademyItems.CONSTRAINT_PLATE.get()));
        }
        if (!WirelessSystem.createNetwork(level, matrix, "legacy-matrix", "matrix-pass")) {
            helper.fail("matrix network fixture failed to initialize");
            return;
        }

        ServerPlayer player = transmitterPlayer(helper, matrixPos);
        node.setOwnerUUID(player.getUUID());
        UUID nonce = FreqTransmitterSessionManager.open(player);
        if (nonce == null
                || FreqTransmitterSessionManager.selectBlock(player, nonce, matrixPos)
                    != FreqTransmitterSessionManager.SelectionResult.PASSWORD_REQUIRED
                || !FreqTransmitterSessionManager.authorize(player, nonce, "matrix-pass")
                || FreqTransmitterSessionManager.selectBlock(player, nonce, nodePos)
                    != FreqTransmitterSessionManager.SelectionResult.LINKED
                || WirelessSystem.getNetwork(level, node) != WiWorldData.get(level).getNetwork(matrix)) {
            helper.fail("password-authorized matrix-to-node flow did not link exactly that node");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void matrixCannotLinkAnotherPlayersNode(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos matrixPos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos nodePos = matrixPos.east(2);
        level.setBlock(matrixPos, AcademyBlocks.MATRIX.get().defaultBlockState(), 3);
        level.setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        MatrixBlockEntity matrix = (MatrixBlockEntity) level.getBlockEntity(matrixPos);
        BaseNodeBlockEntity node = (BaseNodeBlockEntity) level.getBlockEntity(nodePos);
        if (matrix == null || node == null) {
            helper.fail("foreign-node authorization fixtures failed to place");
            return;
        }
        matrix.setInitialized(true);
        matrix.setSSID("protected-matrix");
        matrix.setPassword("matrix-pass");
        matrix.getItems().set(MatrixBlockEntity.CORE_SLOT, new ItemStack(AcademyItems.MAT_CORE_0.get()));
        for (int slot = MatrixBlockEntity.PLATE_SLOT_0; slot <= MatrixBlockEntity.PLATE_SLOT_2; slot++) {
            matrix.getItems().set(slot, new ItemStack(AcademyItems.CONSTRAINT_PLATE.get()));
        }
        if (!WirelessSystem.createNetwork(level, matrix, "protected-matrix", "matrix-pass")) {
            helper.fail("foreign-node matrix fixture failed to initialize");
            return;
        }
        ServerPlayer attacker = transmitterPlayer(helper, matrixPos);
        node.setOwnerUUID(UUID.randomUUID());
        UUID nonce = FreqTransmitterSessionManager.open(attacker);
        if (nonce == null
                || FreqTransmitterSessionManager.selectBlock(attacker, nonce, matrixPos)
                    != FreqTransmitterSessionManager.SelectionResult.PASSWORD_REQUIRED
                || !FreqTransmitterSessionManager.authorize(attacker, nonce, "matrix-pass")
                || FreqTransmitterSessionManager.selectBlock(attacker, nonce, nodePos)
                    != FreqTransmitterSessionManager.SelectionResult.INVALID_TARGET
                || WirelessSystem.getNetwork(level, node) != null) {
            helper.fail("matrix password bypassed target node ownership");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyPasswordNodeStillRequiresConfirmationThenLinksOneMachine(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos nodePos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos generatorPos = nodePos.east(2);
        level.setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(generatorPos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(nodePos) instanceof BaseNodeBlockEntity node)
                || !(level.getBlockEntity(generatorPos)
                    instanceof com.mohistmc.academy.energy.api.block.IWirelessGenerator generator)) {
            helper.fail("node/generator fixtures failed to place");
            return;
        }
        node.setOwnerUUID(UUID.randomUUID());
        node.setPassword("");

        ServerPlayer player = transmitterPlayer(helper, nodePos);
        UUID nonce = FreqTransmitterSessionManager.open(player);
        if (FreqTransmitterSessionManager.selectBlock(player, nonce, nodePos)
                != FreqTransmitterSessionManager.SelectionResult.PASSWORD_REQUIRED) {
            helper.fail("empty-password node bypassed the legacy confirmation page");
            return;
        }
        if (!FreqTransmitterSessionManager.authorize(player, nonce, "")
                || FreqTransmitterSessionManager.selectBlock(player, nonce, generatorPos)
                    != FreqTransmitterSessionManager.SelectionResult.LINKED) {
            helper.fail("empty-password node could not link its selected generator");
            return;
        }
        var connection = WirelessSystem.getNodeConnection(level, node);
        if (connection == null || WiWorldData.get(level).getNodeConnection(generator) != connection) {
            helper.fail("frequency transmitter link was not committed to server WiWorldData");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wrongTargetAndPostAuthorizationPasswordChangeCloseSession(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos nodePos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos stonePos = nodePos.east();
        BlockPos generatorPos = nodePos.east(2);
        level.setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(stonePos, Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(generatorPos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        BaseNodeBlockEntity node = (BaseNodeBlockEntity) level.getBlockEntity(nodePos);
        node.setPassword("first");
        ServerPlayer player = transmitterPlayer(helper, nodePos);

        UUID wrongTargetNonce = FreqTransmitterSessionManager.open(player);
        if (FreqTransmitterSessionManager.selectBlock(player, wrongTargetNonce, stonePos)
                    != FreqTransmitterSessionManager.SelectionResult.INVALID_TARGET
                || FreqTransmitterSessionManager.selectBlock(player, wrongTargetNonce, nodePos)
                    != FreqTransmitterSessionManager.SelectionResult.NO_SESSION) {
            helper.fail("invalid source click did not terminate the server session");
            return;
        }

        UUID changedCredentialNonce = FreqTransmitterSessionManager.open(player);
        if (FreqTransmitterSessionManager.selectBlock(player, changedCredentialNonce, nodePos)
                    != FreqTransmitterSessionManager.SelectionResult.PASSWORD_REQUIRED
                || !FreqTransmitterSessionManager.authorize(player, changedCredentialNonce, "first")) {
            helper.fail("protected node could not reach target-selection state");
            return;
        }
        node.setPassword("changed-after-auth");
        if (FreqTransmitterSessionManager.selectBlock(player, changedCredentialNonce, generatorPos)
                    != FreqTransmitterSessionManager.SelectionResult.CLOSED
                || WiWorldData.get(level).getNodeConnection(
                    (com.mohistmc.academy.energy.api.block.IWirelessGenerator)
                            level.getBlockEntity(generatorPos)) != null) {
            helper.fail("changed source credential retained stale authority or linked a target");
            return;
        }
        helper.succeed();
    }

    private static ServerPlayer transmitterPlayer(GameTestHelper helper, BlockPos source) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(source.getX() + 0.5, source.getY(), source.getZ() - 0.5);
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setTerminalInstalled(true);
        data.installApp(AppRegistry.FREQ_TRANSMITTER.getAppId());
        return player;
    }
}
