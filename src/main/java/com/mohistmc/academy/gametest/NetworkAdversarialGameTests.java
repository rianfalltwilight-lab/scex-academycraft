package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.energy.impl.WiWorldData;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.network.NodeConfigPacket;
import com.mohistmc.academy.network.MenuActionToken;
import com.mohistmc.academy.network.ConnectToNodePacket;
import com.mohistmc.academy.network.DisconnectFromNodePacket;
import com.mohistmc.academy.world.block.entity.DevNormalBlockEntity;
import com.mohistmc.academy.world.menu.PhaseGenMenu;
import java.util.UUID;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.block.entity.NodeBasicBlockEntity;
import com.mohistmc.academy.world.block.entity.PhaseGenBlockEntity;
import com.mohistmc.academy.world.menu.NodeBasicMenu;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Adversarial tests of real menu handlers and wireless transport, not source contracts. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class NetworkAdversarialGameTests {
    private NetworkAdversarialGameTests() {}

    @GameTest(template = "empty")
    public static void rebindRevokesOldGeneratorBeforeEnergyTick(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos aPos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos bPos = aPos.east(4);
        BlockPos gPos = aPos.east(2);
        level.setBlock(aPos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(bPos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(gPos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        var a = (NodeBasicBlockEntity) level.getBlockEntity(aPos);
        var b = (NodeBasicBlockEntity) level.getBlockEntity(bPos);
        var generator = (PhaseGenBlockEntity) level.getBlockEntity(gPos);
        CompoundTag charge = new CompoundTag();
        charge.putFloat("storedEnergy", 1000);
        generator.loadAdditional(charge, level.registryAccess());
        if (!WirelessSystem.linkGenerator(level, a, generator, false, "")
                || !WirelessSystem.linkGenerator(level, b, generator, false, "")) {
            helper.fail("rebind fixture could not link both nodes"); return;
        }
        WiWorldData.get(level).tick();
        if (a.getEnergy() != 0 || b.getEnergy() != 50 || generator.getStoredEnergy() != 950) {
            helper.fail("rebound generator still fed old node: old=" + a.getEnergy()
                    + " new=" + b.getEnergy() + " generator=" + generator.getStoredEnergy()); return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rebindSnapshotContainsOneAuthoritativeGeneratorEdge(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos aPos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos bPos = aPos.east(4);
        BlockPos gPos = aPos.east(2);
        level.setBlock(aPos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(bPos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(gPos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        var a = (NodeBasicBlockEntity) level.getBlockEntity(aPos);
        var b = (NodeBasicBlockEntity) level.getBlockEntity(bPos);
        var generator = (PhaseGenBlockEntity) level.getBlockEntity(gPos);
        WirelessSystem.linkGenerator(level, a, generator, false, "");
        WirelessSystem.linkGenerator(level, b, generator, false, "");
        CompoundTag saved = WiWorldData.get(level).save(new CompoundTag(), level.registryAccess());
        int edges = 0;
        var connections = saved.getCompound("node").getList("list", 10);
        for (int i = 0; i < connections.size(); i++) {
            var generators = connections.getCompound(i).getList("generators", 10);
            for (int j = 0; j < generators.size(); j++) {
                CompoundTag entry = generators.getCompound(j);
                if (entry.getInt("x") == gPos.getX() && entry.getInt("y") == gPos.getY()
                        && entry.getInt("z") == gPos.getZ()) edges++;
            }
        }
        if (edges != 1) { helper.fail("immediate save retained " + edges + " generator edges after rebind"); return; }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void closedNodeMenuPacketCannotMutateReopenedMenu(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(pos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        var node = (NodeBasicBlockEntity) level.getBlockEntity(pos);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        node.setOwnerUUID(player.getUUID());
        NodeBasicMenu oldMenu = menu(player, pos, 31);
        player.containerMenu = oldMenu;
        NodeConfigPacket delayed = new NodeConfigPacket(oldMenu.nextActionToken(), pos, Optional.of("old-session"), Optional.empty());
        player.containerMenu = player.inventoryMenu;
        player.containerMenu = menu(player, pos, 31);
        node.setNodeName("new-session");
        NodeConfigPacket.handle(delayed, immediateContext(player));
        if (!node.getNodeName().equals("new-session")) {
            helper.fail("closed menu packet overwrote reopened node with " + node.getNodeName()); return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nodeHandlerRejectsDuplicateReorderedForgedAndCrossViewerPackets(GameTestHelper helper) {
        var fixture = nodeFixture(helper);
        var player = fixture.player();
        var menu = fixture.menu();
        MenuActionToken first = menu.nextActionToken();
        MenuActionToken second = menu.nextActionToken();
        NodeConfigPacket.handle(new NodeConfigPacket(second, fixture.pos(), Optional.of("latest"), Optional.empty()), immediateContext(player));
        NodeConfigPacket.handle(new NodeConfigPacket(first, fixture.pos(), Optional.of("late"), Optional.empty()), immediateContext(player));
        NodeConfigPacket.handle(new NodeConfigPacket(second, fixture.pos(), Optional.of("duplicate"), Optional.empty()), immediateContext(player));
        require(helper, fixture.node().getNodeName().equals("latest"), "duplicate/reordered node mutation was accepted");

        MenuActionToken third = menu.nextActionToken();
        var forged = new MenuActionToken(menu.containerId, UUID.randomUUID(), Long.MAX_VALUE);
        NodeConfigPacket.handle(new NodeConfigPacket(forged, fixture.pos(), Optional.of("forged"), Optional.empty()), immediateContext(player));
        var other = helper.makeMockServerPlayerInLevel();
        other.setPos(player.getX(), player.getY(), player.getZ());
        var otherMenu = menu(other, fixture.pos(), menu.containerId);
        other.containerMenu = otherMenu;
        NodeConfigPacket.handle(new NodeConfigPacket(third, fixture.pos(), Optional.of("stolen"), Optional.empty()), immediateContext(other));
        require(helper, fixture.node().getNodeName().equals("latest"), "forged/cross-viewer token was accepted");
        NodeConfigPacket.handle(new NodeConfigPacket(third, fixture.pos(), Optional.of("valid-third"), Optional.empty()), immediateContext(player));
        require(helper, fixture.node().getNodeName().equals("valid-third"), "invalid token poisoned a valid action stream");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void independentMenuPasswordCommitPreservesEarlierRename(GameTestHelper helper) {
        var fixture = nodeFixture(helper);
        var player = fixture.player();
        var first = fixture.menu();
        // Separate opening snapshots/action streams. Real simultaneous clients
        // exercise the same field-specific GUI path in the concurrent-menu gate.
        var second = menu(player, fixture.pos(), 32);
        require(helper, second.getInitialNodeName().equals(first.getInitialNodeName()), "opening snapshots did not match");
        NodeConfigPacket.handle(new NodeConfigPacket(first.nextActionToken(), fixture.pos(), Optional.of("renamed-by-A"), Optional.empty()), immediateContext(player));
        player.containerMenu = second;
        NodeConfigPacket.handle(new NodeConfigPacket(second.nextActionToken(), fixture.pos(), Optional.empty(), Optional.of("password-by-B")), immediateContext(player));
        require(helper, fixture.node().getNodeName().equals("renamed-by-A")
                && fixture.node().getPassword().equals("password-by-B"), "password-only commit overwrote another menu's name");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wrongOwnerDeadAndDistantViewerCannotMutateNode(GameTestHelper helper) {
        var fixture = nodeFixture(helper);
        var player = fixture.player();
        String initial = fixture.node().getNodeName();
        fixture.node().setOwnerUUID(UUID.randomUUID());
        NodeConfigPacket.handle(new NodeConfigPacket(fixture.menu().nextActionToken(), fixture.pos(), Optional.of("wrong-owner"), Optional.empty()), immediateContext(player));
        require(helper, fixture.node().getNodeName().equals(initial), "wrong owner changed node");
        fixture.node().setOwnerUUID(player.getUUID());
        var valid = new NodeConfigPacket(fixture.menu().nextActionToken(), fixture.pos(), Optional.of("authorized"), Optional.empty());
        player.setHealth(0);
        NodeConfigPacket.handle(valid, immediateContext(player));
        require(helper, fixture.node().getNodeName().equals(initial), "dead viewer changed node");
        player.setHealth(20);
        player.setPos(fixture.pos().getX() + 30, fixture.pos().getY(), fixture.pos().getZ());
        NodeConfigPacket.handle(valid, immediateContext(player));
        require(helper, fixture.node().getNodeName().equals(initial), "distant viewer changed node");
        player.setPos(fixture.pos().getX() + .5, fixture.pos().getY() + .5, fixture.pos().getZ() + .5);
        NodeConfigPacket.handle(valid, immediateContext(player));
        require(helper, fixture.node().getNodeName().equals("authorized"), "invalid viewer state consumed the pending valid action");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void replacedNodeInvalidatesOldMenuEvenAtSamePosition(GameTestHelper helper) {
        var fixture = nodeFixture(helper);
        var delayed = new NodeConfigPacket(fixture.menu().nextActionToken(), fixture.pos(), Optional.of("stale"), Optional.empty());
        var level = helper.getLevel();
        level.removeBlock(fixture.pos(), false);
        level.setBlock(fixture.pos(), AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        var replacement = (NodeBasicBlockEntity) level.getBlockEntity(fixture.pos());
        replacement.setOwnerUUID(fixture.player().getUUID());
        replacement.setNodeName("replacement");
        NodeConfigPacket.handle(delayed, immediateContext(fixture.player()));
        require(helper, replacement.getNodeName().equals("replacement"), "old menu mutated a different block entity at the same position");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reorderedDisconnectCannotUndoNewGeneratorBinding(GameTestHelper helper) {
        var level = helper.getLevel();
        var fixture = nodeFixture(helper);
        var a = fixture.node();
        BlockPos bPos = fixture.pos().east(4), gPos = fixture.pos().east(2);
        level.setBlock(bPos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(gPos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        var b = (NodeBasicBlockEntity) level.getBlockEntity(bPos);
        var generator = (PhaseGenBlockEntity) level.getBlockEntity(gPos);
        require(helper, WirelessSystem.linkGenerator(level, a, generator, false, ""), "initial generator binding failed");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(gPos);
        PhaseGenMenu menu;
        try { menu = new PhaseGenMenu(32, fixture.player().getInventory(), buffer); }
        finally { buffer.release(); }
        fixture.player().containerMenu = menu;
        var oldDisconnect = new DisconnectFromNodePacket(menu.nextActionToken(), gPos);
        var newConnect = new ConnectToNodePacket(menu.nextActionToken(), gPos, bPos, Optional.empty());
        ConnectToNodePacket.handle(newConnect, immediateContext(fixture.player()));
        DisconnectFromNodePacket.handle(oldDisconnect, immediateContext(fixture.player()));
        require(helper, WiWorldData.get(level).getNodeConnection(generator) != null
                && WiWorldData.get(level).getNodeConnection(generator).getNode() == b, "late disconnect undid a newer generator binding");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void wrongNodePasswordAndBackoffCannotChangeBinding(GameTestHelper helper) {
        var fixture = nodeFixture(helper);
        var level = helper.getLevel();
        var node = fixture.node();
        node.setPassword("correct-secret");
        BlockPos machinePos = fixture.pos().east(2);
        level.setBlock(machinePos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        var generator = (PhaseGenBlockEntity) level.getBlockEntity(machinePos);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(machinePos);
        PhaseGenMenu menu;
        try { menu = new PhaseGenMenu(32, fixture.player().getInventory(), buffer); }
        finally { buffer.release(); }
        fixture.player().containerMenu = menu;
        var context = immediateContext(fixture.player());
        ConnectToNodePacket.handle(new ConnectToNodePacket(menu.nextActionToken(), machinePos, fixture.pos(), Optional.of("incorrect-secret")), context);
        require(helper, WiWorldData.get(level).getNodeConnection(generator) == null, "wrong nonempty password bypassed authentication");
        ConnectToNodePacket.handle(new ConnectToNodePacket(menu.nextActionToken(), machinePos, fixture.pos(), Optional.of("correct-secret")), context);
        require(helper, WiWorldData.get(level).getNodeConnection(generator) == null, "authentication backoff was bypassed");
        helper.runAfterDelay(21, () -> {
            ConnectToNodePacket.handle(new ConnectToNodePacket(menu.nextActionToken(), machinePos, fixture.pos(), Optional.of("correct-secret")), context);
            var connection = WiWorldData.get(level).getNodeConnection(generator);
            require(helper, connection != null && connection.getNode() == node, "valid password could not bind after backoff elapsed");
            ConnectToNodePacket.forgetPlayer(fixture.player().getUUID());
            helper.succeed();
        });
    }
    @GameTest(template = "empty")
    public static void receiverRebindRevokesOldEnergySourceAndSaveEdge(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos aPos = helper.absolutePos(new BlockPos(2, 1, 2)), bPos = aPos.east(4), rPos = aPos.east(2);
        level.setBlock(aPos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(bPos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(rPos, AcademyBlocks.DEV_NORMAL.get().defaultBlockState(), 3);
        var a = (NodeBasicBlockEntity) level.getBlockEntity(aPos);
        var b = (NodeBasicBlockEntity) level.getBlockEntity(bPos);
        var receiver = (DevNormalBlockEntity) level.getBlockEntity(rPos);
        a.setEnergy(1000); b.setEnergy(1000);
        require(helper, WirelessSystem.linkReceiver(level, a, receiver, false, "")
                && WirelessSystem.linkReceiver(level, b, receiver, false, ""), "receiver rebind fixture failed");
        require(helper, savedEdges(helper, "receivers", rPos) == 1, "immediate save retained obsolete receiver edge");
        WiWorldData.get(level).tick();
        require(helper, a.getEnergy() == 1000 && b.getEnergy() == 900 && receiver.getEnergyStored() == 100,
                "receiver drew from old and new sources during one tick");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void generatorCallbackCanRebindAToBToAWithoutDuplicateOrConcurrentModification(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos aPos = helper.absolutePos(new BlockPos(2, 1, 2)), bPos = aPos.east(4), gPos = aPos.east(2), anchorPos = aPos.south();
        level.setBlock(aPos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(bPos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(gPos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        level.setBlock(anchorPos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        var a = (NodeBasicBlockEntity) level.getBlockEntity(aPos);
        var b = (NodeBasicBlockEntity) level.getBlockEntity(bPos);
        var generator = new ReentrantGenerator(gPos);
        level.setBlockEntity(generator);
        CompoundTag charge = new CompoundTag(); charge.putFloat("storedEnergy", 1000);
        generator.loadAdditional(charge, level.registryAccess());
        var anchor = (PhaseGenBlockEntity) level.getBlockEntity(anchorPos);
        require(helper, WirelessSystem.linkGenerator(level, a, generator, false, "")
                && WirelessSystem.linkGenerator(level, a, anchor, false, ""), "callback fixture could not create A edges");
        generator.callback = () -> {
            require(helper, WirelessSystem.linkGenerator(level, b, generator, false, ""), "callback A-to-B failed");
            require(helper, WirelessSystem.linkGenerator(level, a, generator, false, ""), "callback B-to-A failed");
        };
        WiWorldData.get(level).tick();
        var connection = WiWorldData.get(level).getNodeConnection(generator);
        require(helper, connection != null && connection.getNode() == a && connection.getLoad() == 2,
                "callback lost/duplicated authoritative A edge");
        require(helper, generator.calls == 1 && a.getEnergy() == 50 && b.getEnergy() == 0 && generator.getStoredEnergy() == 950,
                "callback caused repeated energy extraction");
        require(helper, savedEdges(helper, "generators", gPos) == 1, "callback left duplicate saved generator edges");
        WiWorldData.get(level).tick();
        require(helper, generator.calls == 2 && generator.getStoredEnergy() == 900 && a.getEnergy() == 100,
                "callback damaged next tick's edge or transfer");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void receiverCallbackCanRebindAToBToAWithoutDuplicateOrConcurrentModification(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos aPos = helper.absolutePos(new BlockPos(2, 1, 2)), bPos = aPos.east(4), rPos = aPos.east(2), anchorPos = aPos.south();
        level.setBlock(aPos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(bPos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(rPos, AcademyBlocks.DEV_NORMAL.get().defaultBlockState(), 3);
        level.setBlock(anchorPos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        var a = (NodeBasicBlockEntity) level.getBlockEntity(aPos);
        var b = (NodeBasicBlockEntity) level.getBlockEntity(bPos);
        a.setEnergy(1000); b.setEnergy(1000);
        var receiver = new ReentrantReceiver(rPos);
        level.setBlockEntity(receiver);
        var anchor = (PhaseGenBlockEntity) level.getBlockEntity(anchorPos);
        require(helper, WirelessSystem.linkReceiver(level, a, receiver, false, "")
                && WirelessSystem.linkGenerator(level, a, anchor, false, ""), "receiver callback fixture failed");
        receiver.callback = () -> {
            require(helper, WirelessSystem.linkReceiver(level, b, receiver, false, ""), "receiver A-to-B failed");
            require(helper, WirelessSystem.linkReceiver(level, a, receiver, false, ""), "receiver B-to-A failed");
        };
        WiWorldData.get(level).tick();
        var connection = WiWorldData.get(level).getNodeConnection(receiver);
        require(helper, connection != null && connection.getNode() == a && connection.getLoad() == 2,
                "receiver callback lost/duplicated authoritative A edge");
        require(helper, receiver.calls == 1 && receiver.getEnergyStored() == 100 && a.getEnergy() == 900 && b.getEnergy() == 1000,
                "receiver callback performed more than one transfer");
        require(helper, savedEdges(helper, "receivers", rPos) == 1, "callback left duplicate saved receiver edges");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidGeneratorReturnsCannotCreateEnergy(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos nodePos = helper.absolutePos(new BlockPos(2, 1, 2)), sourcePos = nodePos.east(2);
        level.setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(sourcePos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        var node = (NodeBasicBlockEntity) level.getBlockEntity(nodePos);
        var source = new InvalidReturnGenerator(sourcePos);
        level.setBlockEntity(source);
        require(helper, WirelessSystem.linkGenerator(level, node, source, false, ""), "invalid provider fixture failed");
        for (double invalid : new double[] {0, -10, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            source.returnValue = invalid;
            WiWorldData.get(level).tick();
            require(helper, node.getEnergy() == 0, "invalid provider return created energy: " + invalid + " -> " + node.getEnergy());
        }
        source.returnValue = 25;
        WiWorldData.get(level).tick();
        require(helper, node.getEnergy() == 25, "valid provider return did not recover after invalid samples");
        helper.succeed();
    }

    private static final class InvalidReturnGenerator extends PhaseGenBlockEntity {
        private double returnValue;
        InvalidReturnGenerator(BlockPos pos) { super(pos, AcademyBlocks.PHASE_GEN.get().defaultBlockState()); }
        @Override public double getProvidedEnergy(double required) { return returnValue; }
    }
    @GameTest(template = "empty")
    public static void sessionReadyWaitsForEverySignedUuidWordAndMarker(GameTestHelper helper) {
        var server = new MenuActionToken.Session(UUID.fromString("ffff8000-0000-ffff-8000-0000ffff8000"));
        var client = new MenuActionToken.Session();
        expectPending(helper, client);
        // Deliberately deliver the marker before the UUID and reverse the
        // words, including signed shorts; a missing zero word also blocks readiness.
        client.receiveWord(8, 1);
        for (int i = 7; i > 0; i--) client.receiveWord(i, (short) server.word(i));
        expectPending(helper, client);
        client.receiveWord(0, (short) server.word(0));
        require(helper, client.ready(), "complete client nonce remained unready");
        var token = client.next(7);
        require(helper, token.sequence() == 1 && server.accept(token, 7), "nonce words/initial sequence did not survive sync");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void completeSessionUuidWaitsForReadyMarker(GameTestHelper helper) {
        var server = new MenuActionToken.Session(UUID.randomUUID());
        var client = new MenuActionToken.Session();
        for (int i = 0; i < 8; i++) client.receiveWord(i, (short) server.word(i));
        expectPending(helper, client);
        client.receiveWord(8, 1);
        require(helper, server.accept(client.next(31), 31), "complete nonce did not activate after marker");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sessionRepeatedAndOlderSequencesCannotReplaceLatest(GameTestHelper helper) {
        var server = new MenuActionToken.Session(UUID.randomUUID());
        var first = server.next(31);
        var second = server.next(31);
        require(helper, server.accept(second, 31), "newest action rejected");
        require(helper, !server.accept(first, 31) && !server.accept(second, 31), "old/repeated action accepted");
        require(helper, server.accept(server.next(31), 31), "stream failed to advance after replay rejection");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void forgedSessionAndContainerCannotPoisonValidStream(GameTestHelper helper) {
        var server = new MenuActionToken.Session(UUID.randomUUID());
        var valid = server.next(31);
        require(helper, !server.accept(new MenuActionToken(31, UUID.randomUUID(), Long.MAX_VALUE), 31), "forged UUID accepted");
        require(helper, !server.accept(new MenuActionToken(32, valid.session(), Long.MAX_VALUE), 31), "wrong container accepted");
        require(helper, !server.accept(new MenuActionToken(31, valid.session(), 0), 31)
                && !server.accept(new MenuActionToken(31, valid.session(), -1), 31), "nonpositive sequence accepted");
        require(helper, server.accept(valid, 31), "forged token poisoned valid stream");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reusedContainerIdCannotReusePreviousSession(GameTestHelper helper) {
        var old = new MenuActionToken.Session(UUID.randomUUID());
        var reopened = new MenuActionToken.Session(UUID.randomUUID());
        require(helper, !reopened.accept(old.next(31), 31), "reopened container accepted old nonce");
        require(helper, reopened.accept(reopened.next(31), 31), "new nonce rejected");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void actionTokenWireCodecPreservesUuidAndFullSequence(GameTestHelper helper) {
        var buffer = Unpooled.buffer();
        try {
            var expected = new MenuActionToken(100, UUID.randomUUID(), Long.MAX_VALUE - 1);
            MenuActionToken.STREAM_CODEC.encode(buffer, expected);
            require(helper, expected.equals(MenuActionToken.STREAM_CODEC.decode(buffer)) && !buffer.isReadable(),
                    "action token codec lost/retained unexpected bytes");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void loadingLegacyDuplicateEdgesReleasesOldNodeCapacity(GameTestHelper helper) {
        // Load the same endpoint from two historical connection records, as
        // produced by the independently reproduced 0.0.17 instant-save bug.
        var data = new WiWorldData();
        CompoundTag entry = new CompoundTag(); entry.putInt("x", 4); entry.putInt("y", 1); entry.putInt("z", 2);
        var list = new net.minecraft.nbt.ListTag(); list.add(entry);
        CompoundTag firstTag = new CompoundTag(), secondTag = new CompoundTag();
        firstTag.put("node", new CompoundTag()); secondTag.put("node", new CompoundTag());
        firstTag.put("generators", list.copy()); firstTag.put("receivers", list.copy());
        secondTag.put("generators", list.copy()); secondTag.put("receivers", list.copy());
        var first = new com.mohistmc.academy.energy.impl.NodeConn(data, firstTag);
        var second = new com.mohistmc.academy.energy.impl.NodeConn(data, secondTag);
        require(helper, first.getLoad() == 0 && second.getLoad() == 2, "restoring old duplicate edges left phantom capacity on the first node");
        helper.succeed();
    }

    private static void expectPending(GameTestHelper helper, MenuActionToken.Session client) {
        require(helper, !client.ready(), "partial client session marked ready");
        boolean blocked = false;
        try { client.next(31); } catch (IllegalStateException expected) { blocked = true; }
        require(helper, blocked, "partial nonce emitted a mutation token");
    }
    private record NodeFixture(BlockPos pos, NodeBasicBlockEntity node, ServerPlayer player, NodeBasicMenu menu) {}
    private static NodeFixture nodeFixture(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlock(pos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        var node = (NodeBasicBlockEntity) helper.getLevel().getBlockEntity(pos);
        var player = helper.makeMockServerPlayerInLevel();
        player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        node.setOwnerUUID(player.getUUID());
        var menu = menu(player, pos, 31);
        player.containerMenu = menu;
        return new NodeFixture(pos, node, player, menu);
    }
    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) helper.fail(message);
    }
    private static int savedEdges(GameTestHelper helper, String kind, BlockPos pos) {
        var level = helper.getLevel();
        var connections = WiWorldData.get(level).save(new CompoundTag(), level.registryAccess()).getCompound("node").getList("list", 10);
        int count = 0;
        for (int i = 0; i < connections.size(); i++) {
            var entries = connections.getCompound(i).getList(kind, 10);
            for (int j = 0; j < entries.size(); j++) {
                var entry = entries.getCompound(j);
                if (entry.getInt("x") == pos.getX() && entry.getInt("y") == pos.getY() && entry.getInt("z") == pos.getZ()) count++;
            }
        }
        return count;
    }
    private static final class ReentrantGenerator extends PhaseGenBlockEntity {
        private Runnable callback;
        private int calls;
        ReentrantGenerator(BlockPos pos) { super(pos, AcademyBlocks.PHASE_GEN.get().defaultBlockState()); }
        @Override public double getProvidedEnergy(double required) {
            calls++;
            Runnable action = callback; callback = null;
            if (action != null) action.run();
            return super.getProvidedEnergy(required);
        }
    }
    private static final class ReentrantReceiver extends DevNormalBlockEntity {
        private Runnable callback;
        private int calls;
        ReentrantReceiver(BlockPos pos) { super(pos, AcademyBlocks.DEV_NORMAL.get().defaultBlockState()); }
        @Override public double injectEnergy(double amount) {
            calls++;
            Runnable action = callback; callback = null;
            if (action != null) action.run();
            return super.injectEnergy(amount);
        }
    }
    private static NodeBasicMenu menu(ServerPlayer player, BlockPos pos, int id) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos);
        try { return new NodeBasicMenu(id, player.getInventory(), buffer); }
        finally { buffer.release(); }
    }

    private static IPayloadContext immediateContext(ServerPlayer player) {
        return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
                new Class<?>[] {IPayloadContext.class}, (proxy, method, args) -> {
                    if (method.getName().equals("player")) return player;
                    if (method.getName().equals("enqueueWork")) {
                        if (args[0] instanceof Runnable work) { work.run(); return CompletableFuture.completedFuture(null); }
                        if (args[0] instanceof java.util.function.Supplier<?> work)
                            return CompletableFuture.completedFuture(work.get());
                    }
                    if (method.getName().equals("toString")) return "NetworkAdversarialGameTestContext";
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
