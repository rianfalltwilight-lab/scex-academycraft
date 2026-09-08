package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.energy.impl.WiWorldData;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.network.*;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.entity.NodeBasicBlockEntity;
import com.mohistmc.academy.world.block.entity.PhaseGenBlockEntity;
import com.mohistmc.academy.world.menu.NodeBasicMenu;
import com.mohistmc.academy.world.menu.PhaseGenMenu;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Actual handler, NBT, public update and credential boundary checks for the reported editor flow. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class NodeConfigFlowGameTests {
    private NodeConfigFlowGameTests() {}

    @GameTest(template = "empty")
    public static void chineseNameAndPasswordSurviveHandlerCodecSaveAndReopen(GameTestHelper h) {
        var f = fixture(h);
        var menu = nodeMenu(f.player, f.node.getBlockPos(), 51);
        f.player.containerMenu = menu;
        String name = "研发节点一号";
        var request = new NodeConfigPacket(menu.nextActionToken(), f.node.getBlockPos(),
                Optional.of(name), Optional.of("密钥-Alpha42"));
        var bytes = Unpooled.buffer();
        try {
            NodeConfigPacket.STREAM_CODEC.encode(bytes, request);
            NodeConfigPacket.handle(NodeConfigPacket.STREAM_CODEC.decode(bytes), context(f.player));
        } finally { bytes.release(); }
        require(name.equals(f.node.getNodeName()), "accepted Chinese name was not committed");
        require("密钥-Alpha42".equals(f.node.getPassword()), "password was not committed");
        CompoundTag saved = new CompoundTag();
        f.node.saveAdditional(saved, h.getLevel().registryAccess());
        var restored = new NodeBasicBlockEntity(f.node.getBlockPos(), f.node.getBlockState());
        restored.loadAdditional(saved, h.getLevel().registryAccess());
        require(name.equals(restored.getNodeName()) && "密钥-Alpha42".equals(restored.getPassword()),
                "name/password changed at disk NBT boundary");
        CompoundTag publicTag = f.node.getUpdateTag(h.getLevel().registryAccess());
        require(name.equals(publicTag.getString("node_name")), "public update did not carry accepted name");
        require(!publicTag.contains("node_pass") && !publicTag.contains("password"), "public update leaked password");
        var mirror = new NodeBasicBlockEntity(f.node.getBlockPos(), f.node.getBlockState());
        mirror.loadAdditional(publicTag, h.getLevel().registryAccess());
        require(name.equals(mirror.getNodeName()), "public update receiver reverted accepted name to Unnamed");
        var reopened = nodeMenu(f.player, f.node.getBlockPos(), 52);
        require(name.equals(reopened.getInitialNodeName()), "reopened menu snapshot lost accepted name");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void publicPasswordStatusDistinguishesProtectedFromPublicWithoutSecret(GameTestHelper h) {
        var f = fixture(h);
        f.node.setPassword("Private-Secret");
        CompoundTag protectedTag = f.node.getUpdateTag(h.getLevel().registryAccess());
        require(protectedTag.getBoolean("node_has_password"), "protected node public update has no password-configured status");
        require(!protectedTag.contains("node_pass") && !protectedTag.contains("password"), "password status leaked plaintext");
        f.node.setPassword("");
        CompoundTag publicTag = f.node.getUpdateTag(h.getLevel().registryAccess());
        require(!publicTag.getBoolean("node_has_password"), "cleared password still appears protected");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void wrongThenCorrectAndChangedPasswordPreserveExistingBinding(GameTestHelper h) {
        var f = fixture(h);
        f.node.setPassword("Alpha");
        BlockPos otherPos = f.node.getBlockPos().west(2);
        h.getLevel().setBlock(otherPos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        var original = (NodeBasicBlockEntity) h.getLevel().getBlockEntity(otherPos);
        require(WirelessSystem.linkGenerator(h.getLevel(), original, f.generator, false, ""), "original link fixture failed");
        var menu = phaseMenu(f.player, f.generator.getBlockPos(), 61);
        f.player.containerMenu = menu;
        connect(f, menu, "Wrong");
        require(WiWorldData.get(h.getLevel()).getNodeConnection(f.generator).getNode() == original,
                "wrong password revoked or changed existing binding");
        h.runAfterDelay(21, () -> {
            connect(f, menu, "Alpha");
            require(WiWorldData.get(h.getLevel()).getNodeConnection(f.generator).getNode() == f.node,
                    "correct password after rejection could not connect");
            var config = nodeMenu(f.player, f.node.getBlockPos(), 62);
            f.player.containerMenu = config;
            NodeConfigPacket.handle(new NodeConfigPacket(config.nextActionToken(), f.node.getBlockPos(),
                    Optional.empty(), Optional.of("Beta")), context(f.player));
            var nextMenu = phaseMenu(f.player, f.generator.getBlockPos(), 63);
            f.player.containerMenu = nextMenu;
            DisconnectFromNodePacket.handle(new DisconnectFromNodePacket(nextMenu.nextActionToken(),
                    f.generator.getBlockPos()), context(f.player));
            connect(f, nextMenu, "Alpha");
            require(WiWorldData.get(h.getLevel()).getNodeConnection(f.generator) == null,
                    "previous password still authorized a new binding after password change");
            h.runAfterDelay(21, () -> {
                connect(f, nextMenu, "Beta");
                require(WiWorldData.get(h.getLevel()).getNodeConnection(f.generator).getNode() == f.node,
                        "new password could not connect after old password rejection");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void nodeInventoryRevokesOldOwnerAtEachInteraction(GameTestHelper h) {
        var f = fixture(h);
        var menu = nodeMenu(f.player, f.node.getBlockPos(), 71);
        f.player.containerMenu = menu;
        ItemStack energyItem = new ItemStack(AcademyItems.ENERGY_UNIT.get());
        for (int slot = 36; slot <= 37; slot++) {
            require(menu.getSlot(slot).mayPlace(energyItem) && menu.getSlot(slot).mayPickup(f.player),
                    "initial owner could not insert/extract at node slot " + slot);
        }
        menu.getSlot(0).set(energyItem.copy());
        require(!menu.quickMoveStack(f.player, 0).isEmpty() && menu.getSlot(0).getItem().isEmpty()
                        && menu.getSlot(36).getItem().is(AcademyItems.ENERGY_UNIT.get()),
                "authorized shift-click did not transfer the energy item into the node");
        f.node.setOwnerUUID(UUID.randomUUID());
        for (int slot = 36; slot <= 37; slot++) {
            require(!menu.getSlot(slot).mayPlace(energyItem) && !menu.getSlot(slot).mayPickup(f.player),
                    "old open node menu retained insert/extract authority at slot " + slot);
        }
        menu.getSlot(0).set(energyItem.copy());
        require(menu.quickMoveStack(f.player, 0).isEmpty() && menu.getSlot(0).getItem().getCount() == 1
                        && menu.getSlot(36).getItem().getCount() == 1 && menu.getSlot(37).getItem().isEmpty(),
                "revoked viewer shift-click still transferred an item into the node");
        require(menu.quickMoveStack(f.player, 36).isEmpty() && menu.getSlot(36).getItem().getCount() == 1,
                "revoked viewer shift-click still extracted the node item");
        // A pre-existing foreign stack can come from imported inventory NBT. Vanilla's
        // merge branch skips mayPlace, so the menu-level permission check must stop it too.
        menu.getSlot(36).set(new ItemStack(Items.STONE, 2));
        menu.getSlot(0).set(new ItemStack(Items.STONE, 3));
        require(menu.quickMoveStack(f.player, 0).isEmpty() && menu.getSlot(0).getItem().getCount() == 3
                        && menu.getSlot(36).getItem().getCount() == 2,
                "revoked viewer merged into a pre-existing node stack despite slot rejection");
        h.succeed();
    }

    private record Fixture(ServerPlayer player, NodeBasicBlockEntity node, PhaseGenBlockEntity generator) {}
    private static Fixture fixture(GameTestHelper h) {
        var level = h.getLevel();
        BlockPos pos = h.absolutePos(new BlockPos(3, 1, 3));
        level.setBlock(pos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(pos.east(2), AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        var node = (NodeBasicBlockEntity) level.getBlockEntity(pos);
        var player = h.makeMockServerPlayerInLevel();
        player.setPos(pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5);
        node.setOwnerUUID(player.getUUID());
        return new Fixture(player, node, (PhaseGenBlockEntity) level.getBlockEntity(pos.east(2)));
    }
    private static void connect(Fixture f, PhaseGenMenu menu, String password) {
        ConnectToNodePacket.handle(new ConnectToNodePacket(menu.nextActionToken(), f.generator.getBlockPos(),
                f.node.getBlockPos(), Optional.of(password)), context(f.player));
    }
    private static NodeBasicMenu nodeMenu(ServerPlayer p, BlockPos pos, int id) {
        var buf = new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos);
        try { return new NodeBasicMenu(id, p.getInventory(), buf); } finally { buf.release(); }
    }
    private static PhaseGenMenu phaseMenu(ServerPlayer p, BlockPos pos, int id) {
        var buf = new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos);
        try { return new PhaseGenMenu(id, p.getInventory(), buf); } finally { buf.release(); }
    }
    private static void require(boolean condition, String failure) {
        if (!condition) throw new AssertionError(failure);
    }
    private static IPayloadContext context(ServerPlayer player) {
        return (IPayloadContext) Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),
                new Class<?>[]{IPayloadContext.class}, (proxy, method, args) -> {
                    if (method.getName().equals("player")) return player;
                    if (method.getName().equals("enqueueWork")) {
                        if (args[0] instanceof Runnable work) { work.run(); return CompletableFuture.completedFuture(null); }
                        if (args[0] instanceof java.util.function.Supplier<?> work) return CompletableFuture.completedFuture(work.get());
                    }
                    if (method.getName().equals("toString")) return "NodeConfigFlowContext";
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
