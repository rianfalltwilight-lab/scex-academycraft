package com.mohistmc.academy.regression;

import com.mohistmc.academy.client.block.gui.DevAdvancedGui;
import com.mohistmc.academy.client.block.gui.DevNormalGui;
import com.mohistmc.academy.client.block.gui.BaseNodeGui;
import com.mohistmc.academy.client.gate.MachineVisualGate;
import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.client.gui.SkillTreeGui;
import com.mohistmc.academy.client.gui.WirelessDisplayName;
import com.mohistmc.academy.energy.api.block.IWirelessUser;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import com.mohistmc.academy.world.block.entity.DevAdvancedBlockEntity;
import com.mohistmc.academy.world.block.entity.DevNormalBlockEntity;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in test JAR only: production interactions, rendered geometry and real payloads. */
@EventBusSubscriber(modid = "academy_audit", value = Dist.CLIENT)
public final class MachineUiClientGate {
    private static final List<String> EVIDENCE = new ArrayList<>();
    private static final int TOTAL_CASES = Boolean.getBoolean("academy.machineUiLayoutOnly") ? 6 : 14;
    private static int stage, ticks, caseIndex;
    private static boolean finished, captureStarted;
    private static volatile boolean pending, captureDone;
    private static volatile String serverFailure;
    private static volatile net.minecraft.nbt.CompoundTag previousNodeSnapshot;
    private static BlockPos origin, developerPos, nodePos;

    private MachineUiClientGate() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.machineUiGate") || finished) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (serverFailure != null) throw new IllegalStateException(serverFailure);
            if (Files.exists(mc.gameDirectory.toPath().resolve("stop-request.txt"))) {
                throw new IllegalStateException("runner requested normal shutdown");
            }
            if (++ticks > 600) throw new IllegalStateException("timeout stage=" + stage + " case=" + caseIndex);
            if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null
                    || mc.getOverlay() != null || pending) return;
            if (ticks == 120) {
                EVIDENCE.add("stalled stage=" + stage + " case=" + caseIndex + " screen="
                        + (mc.screen == null ? "null" : mc.screen.getClass().getSimpleName())
                        + " clientMenu=" + mc.player.containerMenu.getClass().getSimpleName());
                if (mc.screen instanceof AcademyBaseUI<?> stalled) {
                    EVIDENCE.add("wireless=" + field(stalled, "wirelessState") + " active=" + field(stalled, "panelActive")
                            + " nodeResponse=" + field(stalled, "nodeResponseReceived")
                            + " nodes=" + field(stalled, "serverNodes"));
                }
                Screenshot.grab(mc.gameDirectory, "machineui-" + caseIndex + "-stage-" + stage + ".png",
                        mc.getMainRenderTarget(), message -> {});
                runServer(mc, (level, player) -> EVIDENCE.add("serverMenu=" + player.containerMenu.getClass().getSimpleName()
                        + " playerPos=" + player.position() + " machine=" + developerPos + " node=" + nodePos));
                return;
            }
            switch (stage) {
                case 0 -> {
                    require("未命名".equals(WirelessDisplayName.display("Unnamed")), "Chinese placeholder");
                    for (String name : List.of("自定义网络", "UnnamedHome", "unnamed", "")) {
                        require(name.equals(WirelessDisplayName.display(name)), "literal name preserved: " + name);
                    }
                    mc.options.guiScale().set(caseIndex < 6 ? 2 + caseIndex / 2 : 3);
                    mc.resizeDisplay();
                    runServer(mc, MachineUiClientGate::prepare);
                    enter(6);
                }
                case 6 -> {
                    if (!(mc.screen instanceof BaseNodeGui<?> gui) || ticks < 12
                            || !gui.getMenu().actionSessionReady()) return;
                    if (caseIndex == 1 && previousNodeSnapshot != null) {
                        var clientNode = (BaseNodeBlockEntity) mc.level.getBlockEntity(nodePos);
                        var currentSnapshot = clientNode.getUpdateTag(mc.level.registryAccess());
                        clientNode.loadAdditional(previousNodeSnapshot, mc.level.registryAccess());
                        require("Unnamed".equals(gui.getMenu().getCurrentNodeName()),
                                "replacement menu rejects previous node's higher revision");
                        clientNode.loadAdditional(currentSnapshot, mc.level.registryAccess());
                        require(clientNode.getNodeInstanceId().equals(currentSnapshot.getUUID("node_instance_id")),
                                "new public instance supersedes previous revision stream");
                        EVIDENCE.add("PASS stale previous-instance revision rejected at same position");
                    }
                    int x = (int) field(gui, "infoPanelX");
                    float row = (float) field(gui, "nameRowY");
                    gui.mouseClicked(x + 50.0, gui.getGuiTop() + 5 + Math.round(row) + 4.0, 0);
                    require("NAME".equals(field(gui, "editFocus").toString()), "name input focused");
                    StringBuilder buffer = (StringBuilder) field(gui, "nodeNameInput");
                    for (int remaining = buffer.length(); remaining > 0; remaining--) gui.keyPressed(259, 0, 0);
                    require(buffer.isEmpty(), "previous name cleared");
                    for (char ch : nodeName().toCharArray()) gui.charTyped(ch, 0);
                    require(nodeName().contentEquals(buffer), "typed name matches input");
                    gui.keyPressed(257, 0, 0);
                    enter(7);
                }
                case 7 -> {
                    if (ticks == 60) {
                        if (mc.screen instanceof BaseNodeGui<?> stalled) {
                            EVIDENCE.add("rename client: initial=" + stalled.getMenu().getInitialNodeName()
                                    + " current=" + stalled.getMenu().getCurrentNodeName()
                                    + " revision=" + stalled.getMenu().getCurrentConfigRevision()
                                    + " buffer=" + field(stalled, "nodeNameInput")
                                    + " dirty=" + field(stalled, "nodeNameEdited")
                                    + " pending=" + field(stalled, "pendingNameToken")
                                    + " rejected=" + field(stalled, "saveRejected"));
                        }
                        Screenshot.grab(mc.gameDirectory, "machineui-" + caseIndex + "-rename-diagnostic.png",
                                mc.getMainRenderTarget(), message -> {});
                        runServer(mc, (level, player) -> {
                            var node = (BaseNodeBlockEntity) level.getBlockEntity(nodePos);
                            EVIDENCE.add("rename server: name=" + node.getNodeName() + " revision=" + node.getConfigRevision()
                                    + " menu=" + player.containerMenu.getClass().getSimpleName()
                                    + " id=" + player.containerMenu.containerId);
                        });
                        return;
                    }
                    if (!(mc.screen instanceof BaseNodeGui<?> gui) || ticks < 12
                            || !gui.getMenu().getCurrentNodeName().equals(nodeName())
                            || (boolean) field(gui, "nodeNameEdited")) return;
                    runServer(mc, (level, player) -> {
                        BaseNodeBlockEntity node = (BaseNodeBlockEntity) level.getBlockEntity(nodePos);
                        require(nodeName().equals(node.getNodeName()), "real UI rename acknowledged by server");
                        player.closeContainer();
                        interact(level, player, nodePos);
                    });
                    enter(8);
                }
                case 8 -> {
                    if (!(mc.screen instanceof BaseNodeGui<?> gui) || ticks < 12
                            || !gui.getMenu().actionSessionReady()) return;
                    require(nodeName().equals(gui.getMenu().getCurrentNodeName()), "renamed node survives reopening");
                    runServer(mc, (level, player) -> interact(level, player, developerPos));
                    enter(caseIndex < 6 ? 1 : 2);
                }
                case 1 -> {
                    if (!(mc.screen instanceof SkillTreeGui tree) || ticks < 15) return;
                    int left = (int) field(tree, "guiLeft");
                    int top = (int) field(tree, "guiTop");
                    int width = (int) field(tree, "guiWidth");
                    int x = left + (int) Math.round(8.25 * width / 400.0);
                    int y = top + (int) Math.round(114.5 * width / 400.0);
                    require((int) field(tree, "networkActionLeft") == x, "network draw/click X");
                    require((int) field(tree, "networkActionTop") == y, "network draw/click Y");
                    require(left >= 0 && left + width <= tree.width, "skill tree horizontal bounds");
                    if (!capture(mc, "tree")) return;
                    require(tree.mouseClicked(x + 0.25, y + 0.25, 0), "network top-left edge click");
                    enter(2);
                }
                case 2 -> {
                    if (!(mc.screen instanceof AcademyBaseUI<?> gui) || ticks < 15
                            || !developerPos.equals(gui.getMenu().pos)) return;
                    if (caseIndex < 6) require(caseIndex % 2 == 0 ? gui instanceof DevNormalGui : gui instanceof DevAdvancedGui,
                            "registered developer tier");
                    if (!(boolean) field(gui, "panelActive")) {
                        gui.mouseClicked(gui.getGuiLeft() - 11, gui.getGuiTop() + 29, 0);
                        ticks = 0;
                        return;
                    }
                    require((boolean) field(gui, "panelActive"), "wireless page active");
                    if (((List<?>) field(gui, "serverNodes")).isEmpty()) return;
                    int left = gui.getGuiLeft();
                    int top = gui.getGuiTop();
                    if (caseIndex < 6) {
                        var sidebar = gui.getClass().getDeclaredMethod("getSidebarLeft");
                        sidebar.setAccessible(true);
                        require((int) sidebar.invoke(gui) == left - 20, "sidebar gap is 2 px");
                        require(left - 20 >= 0 && left + 176 <= gui.width, "wireless composition fits");
                        require(Math.abs((left - 20) - (gui.width - left - 176)) <= 1,
                                "wireless composition centered");
                    }
                    require(((List<?>) field(gui, "serverNodes")).stream().anyMatch(entry -> {
                        try { return nodeName().equals(field(entry, "name")); }
                        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                    }), "renamed node listed");
                    if (!captureStarted) EVIDENCE.add("case=" + caseIndex + " machine=" + gui.getClass().getSimpleName() + " scale=" + mc.options.guiScale().get()
                            + " viewport=" + gui.width + "x" + gui.height + " wirelessLeft=" + left);
                    if (!capture(mc, "nodes")) return;
                    gui.mouseClicked(left + 145.0, top + 70.0, 0);
                    enter(3);
                }
                case 3 -> {
                    if (!(mc.screen instanceof AcademyBaseUI<?> gui) || gui.activeNode < 0 || ticks < 12) return;
                    if (!capture(mc, "connected")) return;
                    runServer(mc, (level, player) -> {
                        BaseNodeBlockEntity node = (BaseNodeBlockEntity) level.getBlockEntity(nodePos);
                        require(nodeName().equals(node.getNodeName()), "server name unchanged by display");
                        require(nodeName().equals(node.saveWithFullMetadata(level.registryAccess()).getString("node_name")),
                                "serialized name remains literal");
                        require(!node.saveWithFullMetadata(level.registryAccess()).contains("node_instance_id"),
                                "runtime identity does not change saved schema");
                        require(WirelessSystem.getUserConnection(level,
                                (IWirelessUser) level.getBlockEntity(developerPos)) != null, "authoritative link");
                        if (caseIndex == 0) {
                            node.setNodeName("状态栏改名");
                            previousNodeSnapshot = node.getUpdateTag(level.registryAccess());
                        }
                    });
                    enter(5);
                }
                case 5 -> {
                    if (!(mc.screen instanceof AcademyBaseUI<?> gui) || ticks < 25 || gui.activeNode < 0) return;
                    var nodes = (List<?>) field(gui, "serverNodes");
                    if (!linkedName().equals(field(nodes.get(gui.activeNode), "name"))) return;
                    if (caseIndex == 0 && !capture(mc, "renamed")) return;
                    if (caseIndex >= 6) { completeCase(mc); return; }
                    gui.mouseClicked(gui.getGuiLeft() - 11.0, gui.getGuiTop() + 9.0, 0);
                    enter(4);
                }
                case 4 -> {
                    if (!(mc.screen instanceof SkillTreeGui tree) || ticks < 12) return;
                    require(linkedName().equals(field(tree, "linkedNodeName")), "returned tree received raw linked name");
                    if (!capture(mc, "return")) return;
                    completeCase(mc);
                }
                default -> throw new IllegalStateException("unknown stage");
            }
        } catch (Throwable failure) {
            finish(mc, "FAIL", failure.toString());
        }
    }

    private static void completeCase(Minecraft mc) {
        EVIDENCE.add("PASS case=" + caseIndex + " name=" + nodeName() + " real rename/connect/persistence");
        if (++caseIndex == TOTAL_CASES) finish(mc, "PASS", "developer scale/EXP cases; extra machine cases when full gate enabled");
        else {
            // Close the fixture on the same server executor as its next setup.
            // A client close packet can otherwise arrive after that setup and close the new menu.
            mc.setScreen(null);
            runServer(mc, (level, player) -> player.closeContainer());
            enter(0);
        }
    }

    private static String nodeName() { return "回归节点-" + caseIndex; }
    private static String linkedName() { return caseIndex == 0 ? "状态栏改名" : nodeName(); }

    private static void prepare(ServerLevel level, ServerPlayer player) {
        player.closeContainer();
        if (origin == null) origin = player.blockPosition().above(8).immutable();
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-5, -1, -5), origin.offset(5, 4, 5))) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        developerPos = origin;
        nodePos = origin.offset(3, 0, 0);
        level.setBlock(developerPos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(nodePos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(nodePos, switch (caseIndex % 3) {
            case 0 -> AcademyBlocks.NODE_BASIC.get().defaultBlockState();
            case 1 -> AcademyBlocks.NODE_STANDARD.get().defaultBlockState();
            default -> AcademyBlocks.NODE_ADVANCED.get().defaultBlockState();
        }, 3);
        BaseNodeBlockEntity node = (BaseNodeBlockEntity) level.getBlockEntity(nodePos);
        require("Unnamed".equals(node.getNodeName()), "new node default");
        node.setOwnerUUID(player.getUUID());
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.setYRot(0.0f);
        player.setPos(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 1.5);
        if (caseIndex < 6) {
        ItemStack held = new ItemStack(caseIndex % 2 == 0 ? AcademyItems.DEV_NORMAL.get() : AcademyItems.DEV_ADVANCED.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockHitResult placement = new BlockHitResult(Vec3.atCenterOf(origin.below()), Direction.UP, origin.below(), false);
        require(held.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, placement)).consumesAction(),
                "developer item placement");
        if (level.getBlockEntity(origin) instanceof DevNormalBlockEntity dev) dev.setEnergy(10000);
        if (level.getBlockEntity(origin) instanceof DevAdvancedBlockEntity dev) dev.setEnergy(10000);
        } else {
            var block = switch (caseIndex) {
                case 6 -> AcademyBlocks.PHASE_GEN.get();
                case 7 -> AcademyBlocks.SOLAR_GEN.get();
                case 8 -> AcademyBlocks.IMAG_FUSOR.get();
                case 9 -> AcademyBlocks.METAL_FORMER.get();
                case 10 -> AcademyBlocks.ABILITY_INTERFERER.get();
                case 11 -> AcademyBlocks.RF_INPUT.get();
                case 12 -> AcademyBlocks.RF_OUTPUT.get();
                default -> AcademyBlocks.WINDGEN_BASE.get();
            };
            level.setBlock(origin, block.defaultBlockState(), 3);
            if (level.getBlockEntity(origin) instanceof com.mohistmc.academy.world.block.entity.AbilityInterfererBlockEntity interferer) {
                interferer.assignOwnerOnPlacement(player);
            }
        }
        WirelessSystem.unlinkUser(level, (IWirelessUser) level.getBlockEntity(origin));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.reset();
        data.setCurrentAbility(AbilityCategory.ELECTROMASTER);
        data.setPlayerLevel(1);
        data.setLevelProgressExp(data.getLevelProgressThreshold() * switch (caseIndex % 3) {
            case 0 -> 0.25f;
            case 1 -> 0.5f;
            default -> 1.0f;
        });
        data.syncTo(player);
        player.teleportTo(level, origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 2.5, 180.0f, 0.0f);
        interact(level, player, nodePos);
    }

    /** Reuse the established test fixture's real block-interaction adapter; no fabricated client screen. */
    private static void interact(ServerLevel level, ServerPlayer player, BlockPos pos) {
        try {
            var method = MachineVisualGate.class.getDeclaredMethod("interact", ServerLevel.class, ServerPlayer.class, BlockPos.class);
            method.setAccessible(true);
            method.invoke(null, level, player, pos);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private static void runServer(Minecraft mc, BiConsumer<ServerLevel, ServerPlayer> action) {
        pending = true;
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                action.accept(player.serverLevel(), player);
            } catch (Throwable failure) {
                serverFailure = failure.toString();
            } finally { pending = false; }
        });
    }

    private static Object field(Object object, String name) throws ReflectiveOperationException {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(object);
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static boolean capture(Minecraft mc, String page) {
        String name = "machineui-" + caseIndex + "-" + page + ".png";
        if (!captureStarted) {
            captureStarted = true;
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> captureDone = true);
            return false;
        }
        if (!captureDone) return false;
        require(Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(name)), "screenshot written");
        return true;
    }

    private static void enter(int next) { stage = next; ticks = 0; captureStarted = false; captureDone = false; }
    private static void require(boolean condition, String reason) { if (!condition) throw new IllegalStateException(reason); }
    private static void finish(Minecraft mc, String status, String reason) {
        finished = true;
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("academy-machineui-gate-result.txt"),
                    "status=" + status + "\ncompletedCases=" + caseIndex + "/" + TOTAL_CASES + "\nreason=" + reason + "\n" + String.join("\n", EVIDENCE));
        } catch (Exception failure) { throw new IllegalStateException(failure); }
        mc.stop();
    }
}
