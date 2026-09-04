package com.mohistmc.academy.client.gate;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.client.block.gui.AbilityInterfererGui;
import com.mohistmc.academy.client.block.gui.DevAdvancedGui;
import com.mohistmc.academy.client.block.gui.DevNormalGui;
import com.mohistmc.academy.client.block.gui.EnergyBridgeGui;
import com.mohistmc.academy.client.block.gui.ImagFusorGui;
import com.mohistmc.academy.client.block.gui.MatrixGui;
import com.mohistmc.academy.client.block.gui.MetalFomerGui;
import com.mohistmc.academy.client.block.gui.NodeAdvancedGui;
import com.mohistmc.academy.client.block.gui.NodeBasicGui;
import com.mohistmc.academy.client.block.gui.NodeStandardGui;
import com.mohistmc.academy.client.block.gui.PhaseGenGui;
import com.mohistmc.academy.client.block.gui.SolarGenGui;
import com.mohistmc.academy.client.block.gui.WindBaseGui;
import com.mohistmc.academy.client.block.gui.WindMainGui;
import com.mohistmc.academy.client.KeyInputHandler;
import com.mohistmc.academy.client.gui.AcademyBaseUI;
import com.mohistmc.academy.client.gui.SkillTreeGui;
import com.mohistmc.academy.energy.api.block.IWirelessUser;
import com.mohistmc.academy.energy.impl.WiWorldData;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.skill.AbilityCategory;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.SkillChargingManager;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.DevMachineBase;
import com.mohistmc.academy.world.block.Matrix;
import com.mohistmc.academy.world.block.NodeBasic;
import com.mohistmc.academy.world.block.PhaseGen;
import com.mohistmc.academy.world.block.WindGenBase;
import com.mohistmc.academy.world.block.WindGenFan;
import com.mohistmc.academy.world.block.WindGenMain;
import com.mohistmc.academy.world.block.AbilityInterferer;
import com.mohistmc.academy.world.block.EnergyBridgeBlock;
import com.mohistmc.academy.world.block.ImagFusor;
import com.mohistmc.academy.world.block.MetalFomer;
import com.mohistmc.academy.world.block.NodeAdvanced;
import com.mohistmc.academy.world.block.NodeStandard;
import com.mohistmc.academy.world.block.SolarGen;
import com.mohistmc.academy.world.block.entity.AbilityInterfererBlockEntity;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import com.mohistmc.academy.world.block.entity.DevAdvancedBlockEntity;
import com.mohistmc.academy.world.block.entity.DevNormalBlockEntity;
import com.mohistmc.academy.world.block.entity.EnergyBridgeBlockEntity;
import com.mohistmc.academy.world.block.entity.EnergyBridgeInputBlockEntity;
import com.mohistmc.academy.world.block.entity.ImagFusorBlockEntity;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import com.mohistmc.academy.world.block.entity.MetalFomerBlockEntity;
import com.mohistmc.academy.world.block.entity.NodeAdvancedBlockEntity;
import com.mohistmc.academy.world.block.entity.NodeStandardBlockEntity;
import com.mohistmc.academy.world.block.entity.PhaseGenBlockEntity;
import com.mohistmc.academy.world.block.entity.SolarGenBlockEntity;
import com.mohistmc.academy.world.block.entity.WindGenBaseBlockEntity;
import com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity;
import com.mohistmc.academy.world.item.EnergyUnit;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

/**
 * Self-terminating real-client regression gate for the machine workflows that
 * cannot be proven by a headless GameTest.  It is completely inert unless the
 * dedicated {@code clientMachineGate} run supplies
 * {@code -Dacademy.machineVisualGate=true}.
 *
 * <p>The gate opens blocks through their production server-side interaction,
 * waits for the registered client screen, clicks real UI hit boxes, exchanges
 * the normal payloads and captures the rendered result.  It never constructs a
 * menu or screen directly.</p>
 */
@EventBusSubscriber(modid = AcademyCraft.MODID, value = Dist.CLIENT)
public final class MachineVisualGate {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PROPERTY = "academy.machineVisualGate";
    private static final int STAGE_TIMEOUT = 20 * 25;
    private static final List<String> EVIDENCE = new ArrayList<>();

    private enum Stage {
        WAIT_WORLD,
        WAIT_DEVELOPER_TREE,
        CAPTURE_DEVELOPER_TREE,
        WAIT_DEVELOPER_NETWORK,
        WAIT_DEVELOPER_NETWORK_CONNECTED,
        CAPTURE_DEVELOPER_NETWORK,
        WAIT_DEVELOPER_RETURN,
        WAIT_DEVELOPER_RESET,
        CAPTURE_DEVELOPER_RESET,
        WAIT_PHASE,
        CAPTURE_PHASE,
        WAIT_PHASE_NODES,
        CAPTURE_PHASE_NODES,
        WAIT_PHASE_RAPID,
        WAIT_PHASE_RAPID_NODES,
        CAPTURE_PHASE_RAPID_NODES,
        WAIT_PHASE_RETURN,
        WAIT_PHASE_RETURN_NODES,
        WAIT_PHASE_CONNECTED,
        CAPTURE_PHASE_CONNECTED,
        WAIT_MATRIX_EMPTY,
        CAPTURE_MATRIX_EMPTY,
        WAIT_MATRIX_INIT,
        CAPTURE_MATRIX_INIT,
        WAIT_MATRIX_OPERATIONAL,
        CAPTURE_MATRIX_OPERATIONAL,
        WAIT_NODE,
        WAIT_NODE_RENAMED,
        CAPTURE_NODE,
        WAIT_NODE_NETWORKS,
        WAIT_NODE_CONNECTED,
        CAPTURE_NODE_CONNECTED,
        WAIT_WIND_MAIN_EMPTY,
        CAPTURE_WIND_MAIN_EMPTY,
        WAIT_WIND_MAIN,
        CAPTURE_WIND_MAIN,
        WAIT_WIND_BASE,
        CAPTURE_WIND_BASE,
        WAIT_NORMAL_DEVELOPER_TREE,
        CAPTURE_NORMAL_DEVELOPER_TREE,
        WAIT_NORMAL_DEVELOPER_DETAIL,
        CAPTURE_NORMAL_DEVELOPER_DETAIL,
        WAIT_NORMAL_DEVELOPER_NETWORK,
        CAPTURE_NORMAL_DEVELOPER_NETWORK,
        WAIT_AUX_MACHINE,
        CAPTURE_AUX_MACHINE,
        WAIT_AUX_WIRELESS_NODES,
        CAPTURE_AUX_WIRELESS_NODES,
        WAIT_AUX_WIRELESS_CONNECTED,
        CAPTURE_AUX_WIRELESS_CONNECTED,
        WAIT_AUX_NODE_NETWORKS,
        CAPTURE_AUX_NODE_NETWORKS,
        WAIT_AUX_NODE_CONNECTED,
        CAPTURE_AUX_NODE_CONNECTED,
        WAIT_JADE_WIND,
        CAPTURE_JADE_WIND,
        WAIT_JADE_MATRIX,
        CAPTURE_JADE_MATRIX,
        WAIT_ABILITY_SYNC,
        WAIT_ABILITY_TOGGLE_DOWN,
        WAIT_ABILITY_ACTIVE,
        WAIT_ABILITY_SKILL_DOWN,
        WAIT_ABILITY_EXECUTED,
        WAIT_CHARGING_SYNC,
        WAIT_CHARGING_ACTIVE,
        WAIT_CHARGING_RELEASED,
        FINISHED
    }

    private static Stage stage = Stage.WAIT_WORLD;
    private static int stageTicks;
    private static int worldTicks;
    private static long stageEnteredAtNanos = System.nanoTime();
    private static boolean operationPending;
    private static volatile String operationFailure;
    private static volatile boolean serverAssertionComplete;
    private static boolean screenshotStarted;
    private static volatile boolean screenshotFinished;
    private static volatile String screenshotMessage;
    private static long firstNodeListTick = Long.MIN_VALUE;
    private static BlockPos developerPos;
    private static BlockPos normalDeveloperPos;
    private static BlockPos phasePos;
    private static BlockPos phaseRapidPos;
    private static BlockPos nodePos;
    private static BlockPos matrixPos;
    /** Exposed upper proxy selected from the placed Matrix's real orientation. */
    private static BlockPos matrixJadeTarget;
    private static BlockPos windBasePos;
    private static BlockPos windMainPos;
    private static BlockPos interfererPos;
    private static BlockPos standardNodePos;
    private static BlockPos advancedNodePos;
    private static BlockPos imagFusorPos;
    private static BlockPos solarPos;
    private static BlockPos rfInputPos;
    private static BlockPos rfOutputPos;
    private static BlockPos metalFormerPos;
    private static final List<AuxMachineFixture> AUX_MACHINES = new ArrayList<>();
    private static int auxMachineIndex;
    private static float abilityStartCp;
    private static float chargingStartCp;

    private enum AuxMachine {
        INTERFERER,
        NODE_STANDARD,
        NODE_ADVANCED,
        IMAG_FUSOR,
        SOLAR,
        RF_INPUT,
        RF_OUTPUT,
        METAL_FORMER
    }

    private record AuxMachineFixture(AuxMachine kind, BlockPos pos,
                                     Class<? extends Screen> screenType,
                                     String label, String screenshot) {
        boolean isNode() {
            return kind == AuxMachine.NODE_STANDARD || kind == AuxMachine.NODE_ADVANCED;
        }

        boolean usesNodeLink() {
            return !isNode();
        }

        boolean opensWirelessPanelInitially() {
            return kind == AuxMachine.RF_INPUT || kind == AuxMachine.RF_OUTPUT;
        }
    }

    private MachineVisualGate() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean(PROPERTY) || stage == Stage.FINISHED) return;
        Minecraft mc = Minecraft.getInstance();
        worldTicks++;
        if (operationFailure != null) {
            fail(mc, operationFailure);
            return;
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
            if (worldTicks > 20 * 90) fail(mc, "integrated world did not become ready");
            return;
        }
        stageTicks++;
        if (stageTicks > STAGE_TIMEOUT) {
            fail(mc, "timeout in " + stage + "; screen=" + screenName(mc.screen));
            return;
        }

        try {
            tickStage(mc);
        } catch (Throwable failure) {
            fail(mc, "client exception in " + stage + ": " + failure);
        }
    }

    private static void tickStage(Minecraft mc) {
        switch (stage) {
            case WAIT_WORLD -> {
                // Avoid opening the production screen beneath Minecraft's
                // terrain-loading overlay; the framebuffer would otherwise
                // record the overlay instead of the verified machine UI.
                if (operationPending || mc.getOverlay() != null) return;
                prepareFixturesAndOpenDeveloper(mc);
                enter(Stage.WAIT_DEVELOPER_TREE);
            }
            case WAIT_DEVELOPER_TREE -> {
                if (operationPending || !(mc.screen instanceof SkillTreeGui tree)
                        || !tree.isLegacyConsoleForVisualGate()) return;
                // Client ticks can run several times before the first world
                // framebuffer is presented during quick-play startup.
                if (stageTicks < 8 || stageAgeMillis() < 750) return;
                evidence("advanced developer opened the final-1.12.2 400x187 console canvas through S2C payload");
                enter(Stage.CAPTURE_DEVELOPER_TREE);
            }
            case CAPTURE_DEVELOPER_TREE -> capture(mc, "academy-gate-developer-skill-tree.png",
                    Stage.WAIT_DEVELOPER_NETWORK, () -> {
                        if (!(mc.screen instanceof SkillTreeGui tree)
                                || !tree.clickNetworkButtonForVisualGate()) {
                            throw new IllegalStateException("developer network hit box rejected the click");
                        }
                    });
            case WAIT_DEVELOPER_NETWORK -> {
                if (!(mc.screen instanceof DevAdvancedGui gui) || !gui.isNetworkPanelOpenForVisualGate()
                        || gui.visibleNodesForVisualGate() < 1 || stageTicks < 8) return;
                if (!gui.connectFirstProtectedNodeForVisualGate("gate-pass")) {
                    throw new IllegalStateException("developer protected-node click/password/Enter path failed");
                }
                evidence("developer network button opened authenticated slotless wireless page and discovered nodes");
                enter(Stage.WAIT_DEVELOPER_NETWORK_CONNECTED);
            }
            case WAIT_DEVELOPER_NETWORK_CONNECTED -> {
                if (!(mc.screen instanceof DevAdvancedGui gui) || !gui.hasActiveNodeForVisualGate()) return;
                if (!serverAssertionComplete && !operationPending) assertDeveloperNodeLink(mc);
                if (!serverAssertionComplete || operationPending || operationFailure != null || stageTicks < 12) return;
                evidence("advanced developer linked to the protected standalone node through the player-facing page");
                enter(Stage.CAPTURE_DEVELOPER_NETWORK);
            }
            case CAPTURE_DEVELOPER_NETWORK -> capture(mc, "academy-gate-developer-network.png",
                    Stage.WAIT_DEVELOPER_RETURN, () -> {
                        if (!(mc.screen instanceof DevAdvancedGui gui)
                                || !gui.clickReturnToSkillTreeForVisualGate()) {
                            throw new IllegalStateException("developer return hit box rejected the click");
                        }
                    });
            case WAIT_DEVELOPER_RETURN -> {
                if (!(mc.screen instanceof SkillTreeGui) || stageTicks < 5 || operationPending) return;
                configureResetAndOpenDeveloper(mc);
                enter(Stage.WAIT_DEVELOPER_RESET);
            }
            case WAIT_DEVELOPER_RESET -> {
                if (operationPending || !(mc.screen instanceof SkillTreeGui tree)
                        || !tree.isLegacyConsoleForVisualGate()
                        || !tree.isResetModeForVisualGate()
                        || !tree.hasDifferentFactorForVisualGate() || stageTicks < 8) return;
                evidence("advanced developer reset mode used Level 3, main-hand coil and inventory factor");
                enter(Stage.CAPTURE_DEVELOPER_RESET);
            }
            case CAPTURE_DEVELOPER_RESET -> capture(mc, "academy-gate-developer-reset.png",
                    Stage.WAIT_PHASE, () -> openBlock(mc, phasePos));
            case WAIT_PHASE -> {
                if (operationPending || !(mc.screen instanceof PhaseGenGui) || stageTicks < 8) return;
                evidence("phase generator opened registered PhaseGenGui through server menu");
                enter(Stage.CAPTURE_PHASE);
            }
            case CAPTURE_PHASE -> capture(mc, "academy-gate-phase-machine.png",
                    Stage.WAIT_PHASE_NODES, () -> {
                        if (!(mc.screen instanceof AcademyBaseUI<?> ui)
                                || !ui.clickWirelessSidebarForVisualGate()) {
                            throw new IllegalStateException("phase wireless sidebar rejected the click");
                        }
                    });
            case WAIT_PHASE_NODES -> {
                if (!(mc.screen instanceof AcademyBaseUI<?> ui) || ui.visibleNodesForVisualGate() < 1) return;
                firstNodeListTick = mc.level.getGameTime();
                evidence("standalone node was discoverable before Matrix initialization");
                enter(Stage.CAPTURE_PHASE_NODES);
            }
            case CAPTURE_PHASE_NODES -> capture(mc, "academy-gate-phase-node-list.png",
                    Stage.WAIT_PHASE_RAPID, () -> openBlock(mc, phaseRapidPos));
            case WAIT_PHASE_RAPID -> {
                if (operationPending || !(mc.screen instanceof PhaseGenGui) || stageTicks < 1) return;
                long elapsed = mc.level.getGameTime() - firstNodeListTick;
                if (elapsed >= 10) {
                    throw new IllegalStateException("rapid-reopen fixture missed the ten-tick limiter window: " + elapsed);
                }
                if (!(mc.screen instanceof AcademyBaseUI<?> ui)
                        || !ui.clickWirelessSidebarForVisualGate()) {
                    throw new IllegalStateException("second phase wireless sidebar rejected the rapid click");
                }
                evidence("second phase machine requested nodes " + elapsed
                        + " ticks after the first list, using a new menu session");
                enter(Stage.WAIT_PHASE_RAPID_NODES);
            }
            case WAIT_PHASE_RAPID_NODES -> {
                if (!(mc.screen instanceof AcademyBaseUI<?> ui) || ui.visibleNodesForVisualGate() < 1) return;
                evidence("rapidly opened second machine received its own correlated node list");
                enter(Stage.CAPTURE_PHASE_RAPID_NODES);
            }
            case CAPTURE_PHASE_RAPID_NODES -> capture(mc, "academy-gate-phase-rapid-node-list.png",
                    Stage.WAIT_PHASE_RETURN, () -> openBlock(mc, phasePos));
            case WAIT_PHASE_RETURN -> {
                if (operationPending || !(mc.screen instanceof AcademyBaseUI<?> ui) || stageTicks < 1) return;
                if (!ui.clickWirelessSidebarForVisualGate()) {
                    throw new IllegalStateException("original phase wireless sidebar rejected the return click");
                }
                enter(Stage.WAIT_PHASE_RETURN_NODES);
            }
            case WAIT_PHASE_RETURN_NODES -> {
                if (!(mc.screen instanceof AcademyBaseUI<?> ui) || ui.visibleNodesForVisualGate() < 1) return;
                if (!ui.connectFirstProtectedNodeForVisualGate("gate-pass")) {
                    throw new IllegalStateException("protected standalone-node click/password/Enter path failed");
                }
                enter(Stage.WAIT_PHASE_CONNECTED);
            }
            /* Connection is deliberately performed only after the rapid-open
             * regression above, so cached "already connected" state cannot
             * make an empty second response look successful. */
            case WAIT_PHASE_CONNECTED -> {
                if (!(mc.screen instanceof AcademyBaseUI<?> ui) || !ui.hasActiveNodeForVisualGate()) return;
                if (!serverAssertionComplete && !operationPending) assertStandalonePhaseLink(mc);
                if (!serverAssertionComplete || operationPending || operationFailure != null || stageTicks < 15) return;
                evidence("phase generator linked to a password-protected standalone node through click, typed password and Enter");
                enter(Stage.CAPTURE_PHASE_CONNECTED);
            }
            case CAPTURE_PHASE_CONNECTED -> capture(mc, "academy-gate-phase-node-connected.png",
                    Stage.WAIT_MATRIX_EMPTY, () -> prepareMatrixMaterialsAndOpen(mc));
            case WAIT_MATRIX_EMPTY -> {
                if (operationPending || !(mc.screen instanceof MatrixGui gui) || stageTicks < 8) return;
                if (gui.getMenu().isInitialized() || gui.getMenu().hasInitializationMaterials()) {
                    throw new IllegalStateException("player-placed Matrix did not open empty and uninitialized");
                }
                for (int slot = 36; slot < 40; slot++) {
                    if (gui.getMenu().getSlot(slot).hasItem()) {
                        throw new IllegalStateException("player-placed Matrix unexpectedly contained component " + slot);
                    }
                }
                if (!gui.getMenu().getSlot(0).getItem().is(AcademyItems.MAT_CORE_1.get())
                        || gui.getMenu().getSlot(0).getItem().getCount() != 64) {
                    throw new IllegalStateException("Matrix core was not present in the real player inventory slot");
                }
                if (!gui.getMenu().getSlot(1).getItem().is(AcademyItems.CONSTRAINT_PLATE.get())
                        || gui.getMenu().getSlot(1).getItem().getCount() != 64
                        || gui.getMenu().getSlot(2).hasItem() || gui.getMenu().getSlot(3).hasItem()) {
                    throw new IllegalStateException("Matrix plate stack was not isolated in one real player slot");
                }
                evidence("production Matrix item placed its complete proxy structure and opened with empty component slots");
                enter(Stage.CAPTURE_MATRIX_EMPTY);
            }
            case CAPTURE_MATRIX_EMPTY -> capture(mc, "academy-gate-matrix-empty.png",
                    Stage.WAIT_MATRIX_INIT, () -> shiftClickMatrixMaterials(mc));
            case WAIT_MATRIX_INIT -> {
                if (operationPending || !(mc.screen instanceof MatrixGui gui) || stageTicks < 8) return;
                if (gui.getMenu().isInitialized() || !gui.getMenu().hasInitializationMaterials()) {
                    throw new IllegalStateException("Matrix INIT opening snapshot was not uninitialized/ready");
                }
                for (int slot = 36; slot < 40; slot++) {
                    if (gui.getMenu().getSlot(slot).getItem().getCount() != 1) {
                        throw new IllegalStateException("Matrix component slot did not retain exactly one item: " + slot);
                    }
                }
                if (gui.getMenu().getSlot(0).getItem().getCount() != 63
                        || gui.getMenu().getSlot(1).getItem().getCount() != 61
                        || gui.getMenu().getSlot(2).hasItem() || gui.getMenu().getSlot(3).hasItem()) {
                    throw new IllegalStateException("Matrix Shift-click did not preserve the expected stack remainder");
                }
                evidence("two real client Shift-clicks split 64-item stacks into exactly one core and three one-item plates");
                enter(Stage.CAPTURE_MATRIX_INIT);
            }
            case CAPTURE_MATRIX_INIT -> capture(mc, "academy-gate-matrix-init.png",
                    Stage.WAIT_MATRIX_OPERATIONAL, () -> {
                        if (!(mc.screen instanceof MatrixGui gui) || !gui.clickInitForVisualGate()) {
                            throw new IllegalStateException("Matrix INIT hit box rejected the click");
                        }
                    });
            case WAIT_MATRIX_OPERATIONAL -> {
                if (!(mc.screen instanceof MatrixGui gui) || !gui.getMenu().isOperational()) return;
                if (!serverAssertionComplete && !operationPending) assertMatrixMaterialsPreserved(mc);
                if (!serverAssertionComplete || operationPending || operationFailure != null || stageTicks < 12) return;
                evidence("Matrix INIT packet created network and retained all four installed components");
                enter(Stage.CAPTURE_MATRIX_OPERATIONAL);
            }
            case CAPTURE_MATRIX_OPERATIONAL -> capture(mc, "academy-gate-matrix-operational.png",
                    Stage.WAIT_NODE, () -> openBlock(mc, nodePos));
            case WAIT_NODE -> {
                if (operationPending || !(mc.screen instanceof NodeBasicGui gui) || stageTicks < 8) return;
                if (!gui.renameNodeForVisualGate("Gate Renamed")) {
                    throw new IllegalStateException("basic-node production property editor rejected rename input");
                }
                enter(Stage.WAIT_NODE_RENAMED);
            }
            case WAIT_NODE_RENAMED -> {
                if (!serverAssertionComplete && !operationPending) assertNodeRenamePersistedAndReopen(mc);
                if (operationPending || !serverAssertionComplete
                        || !(mc.screen instanceof NodeBasicGui gui)
                        || !"Gate Renamed".equals(gui.getMenu().getInitialNodeName())) return;
                evidence("node rename traversed the real editor/C2S path, persisted to NBT and survived menu reopen");
                enter(Stage.CAPTURE_NODE);
            }
            case CAPTURE_NODE -> capture(mc, "academy-gate-node-machine.png",
                    Stage.WAIT_NODE_NETWORKS, () -> {
                        if (!(mc.screen instanceof AcademyBaseUI<?> ui)
                                || !ui.clickWirelessSidebarForVisualGate()) {
                            throw new IllegalStateException("node Matrix sidebar rejected the click");
                        }
                    });
            case WAIT_NODE_NETWORKS -> {
                if (!(mc.screen instanceof AcademyBaseUI<?> ui)
                        || ui.visibleMatrixNetworksForVisualGate() < 1) return;
                if (!ui.clickFirstMatrixNetworkForVisualGate()) {
                    throw new IllegalStateException("Matrix network hit box rejected the click");
                }
                evidence("node discovered initialized Matrix through correlated network list payload");
                enter(Stage.WAIT_NODE_CONNECTED);
            }
            case WAIT_NODE_CONNECTED -> {
                if (!(mc.screen instanceof AcademyBaseUI<?> ui)
                        || !ui.hasActiveMatrixNetworkForVisualGate()) return;
                if (!serverAssertionComplete && !operationPending) assertNodeStillServesPhase(mc);
                if (!serverAssertionComplete || operationPending || operationFailure != null || stageTicks < 15) return;
                evidence("node joined Matrix without losing its existing phase-generator link");
                enter(Stage.CAPTURE_NODE_CONNECTED);
            }
            case CAPTURE_NODE_CONNECTED -> capture(mc, "academy-gate-node-matrix-connected.png",
                    Stage.WAIT_WIND_MAIN_EMPTY, () -> prepareFanAndOpenWindMain(mc));
            case WAIT_WIND_MAIN_EMPTY -> {
                if (operationPending || !(mc.screen instanceof WindMainGui gui) || stageTicks < 8) return;
                if (!gui.getMenu().isStructureComplete() || gui.getMenu().isFanInstalled()
                        || gui.getMenu().isWorking()
                        || !gui.getMenu().getSlot(0).getItem().is(AcademyItems.WINDGEN_FAN.get())
                        || gui.getMenu().getSlot(36).hasItem()) {
                    throw new IllegalStateException("player-placed wind head did not open in the expected empty-fan state");
                }
                evidence("player-placed wind base/head retained their transactional proxy structures");
                evidence("wind main opened with one fan in player inventory and an empty one-item machine slot");
                enter(Stage.CAPTURE_WIND_MAIN_EMPTY);
            }
            case CAPTURE_WIND_MAIN_EMPTY -> capture(mc, "academy-gate-wind-main-empty.png",
                    Stage.WAIT_WIND_MAIN, () -> shiftClickPlayerSlot(mc, 0));
            case WAIT_WIND_MAIN -> {
                if (operationPending || !(mc.screen instanceof WindMainGui gui) || stageTicks < 8) return;
                if (!gui.getMenu().isStructureComplete() || !gui.getMenu().isFanInstalled()
                        || !gui.getMenu().isWorking()
                        || !gui.getMenu().getSlot(36).getItem().is(AcademyItems.WINDGEN_FAN.get())
                        || gui.getMenu().getSlot(36).getItem().getCount() != 1
                        || gui.getMenu().getSlot(0).hasItem()) return;
                evidence("real client shift-click inserted exactly one fan and started generation");
                enter(Stage.CAPTURE_WIND_MAIN);
            }
            case CAPTURE_WIND_MAIN -> capture(mc, "academy-gate-wind-main-working.png",
                    Stage.WAIT_WIND_BASE, () -> openBlock(mc, windBasePos));
            case WAIT_WIND_BASE -> {
                if (operationPending || !(mc.screen instanceof WindBaseGui) || stageTicks < 8) return;
                if (!serverAssertionComplete && !operationPending) assertWindGenerated(mc);
                if (!serverAssertionComplete || operationPending || operationFailure != null) return;
                evidence("wind base received live generation and charged its energy item");
                enter(Stage.CAPTURE_WIND_BASE);
            }
            case CAPTURE_WIND_BASE -> capture(mc, "academy-gate-wind-base-working.png",
                    Stage.WAIT_NORMAL_DEVELOPER_TREE, () -> openBlock(mc, normalDeveloperPos));
            case WAIT_NORMAL_DEVELOPER_TREE -> {
                if (operationPending || !(mc.screen instanceof SkillTreeGui) || stageTicks < 8) return;
                evidence("player-placed normal developer opened the production skill tree entry flow");
                enter(Stage.CAPTURE_NORMAL_DEVELOPER_TREE);
            }
            case CAPTURE_NORMAL_DEVELOPER_TREE -> capture(mc, "academy-gate-developer-normal-skill-tree.png",
                    Stage.WAIT_NORMAL_DEVELOPER_DETAIL, () -> {
                        if (!(mc.screen instanceof SkillTreeGui tree)
                                || !tree.openFirstSkillDetailForVisualGate()) {
                            throw new IllegalStateException("normal developer skill hit box rejected the click");
                        }
                    });
            case WAIT_NORMAL_DEVELOPER_DETAIL -> {
                if (!(mc.screen instanceof SkillTreeGui tree)
                        || !tree.isSkillDetailOpenForVisualGate() || stageTicks < 5) return;
                evidence("normal developer skill click opened the final-1.12.2 modal detail cover");
                enter(Stage.CAPTURE_NORMAL_DEVELOPER_DETAIL);
            }
            case CAPTURE_NORMAL_DEVELOPER_DETAIL -> capture(mc,
                    "academy-gate-developer-normal-skill-detail.png", Stage.WAIT_NORMAL_DEVELOPER_NETWORK,
                    () -> {
                        if (!(mc.screen instanceof SkillTreeGui tree)
                                || !tree.closeSkillDetailForVisualGate()
                                || !tree.clickNetworkButtonForVisualGate()) {
                            throw new IllegalStateException("normal developer detail close/network flow failed");
                        }
                    });
            case WAIT_NORMAL_DEVELOPER_NETWORK -> {
                if (!(mc.screen instanceof DevNormalGui gui)
                        || !gui.isNetworkPanelOpenForVisualGate() || stageTicks < 8) return;
                evidence("normal developer wireless button opened its registered authenticated menu");
                enter(Stage.CAPTURE_NORMAL_DEVELOPER_NETWORK);
            }
            case CAPTURE_NORMAL_DEVELOPER_NETWORK -> capture(mc,
                    "academy-gate-developer-normal-network.png", Stage.WAIT_AUX_MACHINE,
                    () -> startAuxiliaryMachines(mc));
            case WAIT_AUX_MACHINE -> {
                AuxMachineFixture fixture = currentAuxiliaryMachine();
                if (operationPending || !fixture.screenType().isInstance(mc.screen) || stageTicks < 8) return;
                assertAuxiliaryScreen(fixture, mc.screen);
                evidence(fixture.label() + " opened its registered production screen with live server data");
                enter(Stage.CAPTURE_AUX_MACHINE);
            }
            case CAPTURE_AUX_MACHINE -> {
                AuxMachineFixture fixture = currentAuxiliaryMachine();
                Stage next = fixture.isNode() ? Stage.WAIT_AUX_NODE_NETWORKS
                        : fixture.usesNodeLink() ? Stage.WAIT_AUX_WIRELESS_NODES
                        : hasNextAuxiliaryMachine() ? Stage.WAIT_AUX_MACHINE : Stage.WAIT_JADE_WIND;
                capture(mc, fixture.screenshot(), next, () -> {
                    if (fixture.isNode()) {
                        if (!(mc.screen instanceof AcademyBaseUI<?> ui)
                                || !ui.clickWirelessSidebarForVisualGate()) {
                            throw new IllegalStateException(fixture.label()
                                    + " Matrix sidebar rejected the click");
                        }
                    } else if (fixture.usesNodeLink() && !fixture.opensWirelessPanelInitially()) {
                        if (!(mc.screen instanceof AcademyBaseUI<?> ui)
                                || !ui.clickWirelessSidebarForVisualGate()) {
                            throw new IllegalStateException(fixture.label()
                                    + " wireless-node sidebar rejected the click");
                        }
                    } else if (!fixture.usesNodeLink()) {
                        advanceAuxiliaryMachine(mc);
                    }
                });
            }
            case WAIT_AUX_WIRELESS_NODES -> {
                AuxMachineFixture fixture = currentAuxiliaryMachine();
                if (!(mc.screen instanceof AcademyBaseUI<?> ui)
                        || ui.visibleNodesForVisualGate() < 1) return;
                evidence(fixture.label() + " discovered a standalone node through its own authenticated menu");
                enter(Stage.CAPTURE_AUX_WIRELESS_NODES);
            }
            case CAPTURE_AUX_WIRELESS_NODES -> {
                AuxMachineFixture fixture = currentAuxiliaryMachine();
                capture(mc, fixture.screenshot().replace(".png", "-node-list.png"),
                        Stage.WAIT_AUX_WIRELESS_CONNECTED, () -> {
                            if (!(mc.screen instanceof AcademyBaseUI<?> ui)
                                    || !ui.clickFirstNodeForVisualGate()) {
                                throw new IllegalStateException(fixture.label()
                                        + " standalone-node hit box rejected the click");
                            }
                        });
            }
            case WAIT_AUX_WIRELESS_CONNECTED -> {
                AuxMachineFixture fixture = currentAuxiliaryMachine();
                if (!(mc.screen instanceof AcademyBaseUI<?> ui)
                        || !ui.hasActiveNodeForVisualGate()) return;
                if (!serverAssertionComplete && !operationPending) {
                    assertAuxiliaryMachineNodeLink(mc, fixture);
                }
                if (!serverAssertionComplete || operationPending || operationFailure != null
                        || stageTicks < 12) return;
                evidence(fixture.label() + " persisted its selected standalone-node connection on the server");
                enter(Stage.CAPTURE_AUX_WIRELESS_CONNECTED);
            }
            case CAPTURE_AUX_WIRELESS_CONNECTED -> {
                AuxMachineFixture fixture = currentAuxiliaryMachine();
                Stage next = hasNextAuxiliaryMachine() ? Stage.WAIT_AUX_MACHINE : Stage.WAIT_JADE_WIND;
                capture(mc, fixture.screenshot().replace(".png", "-node-connected.png"), next,
                        () -> advanceAuxiliaryMachine(mc));
            }
            case WAIT_AUX_NODE_NETWORKS -> {
                AuxMachineFixture fixture = currentAuxiliaryMachine();
                if (!(mc.screen instanceof AcademyBaseUI<?> ui)
                        || ui.visibleMatrixNetworksForVisualGate() < 1) return;
                evidence(fixture.label() + " discovered the initialized Matrix through its own menu session");
                enter(Stage.CAPTURE_AUX_NODE_NETWORKS);
            }
            case CAPTURE_AUX_NODE_NETWORKS -> {
                AuxMachineFixture fixture = currentAuxiliaryMachine();
                capture(mc, fixture.screenshot().replace(".png", "-matrix-list.png"),
                        Stage.WAIT_AUX_NODE_CONNECTED, () -> {
                            if (!(mc.screen instanceof AcademyBaseUI<?> ui)
                                    || !ui.clickFirstMatrixNetworkForVisualGate()) {
                                throw new IllegalStateException(fixture.label()
                                        + " Matrix network hit box rejected the click");
                            }
                        });
            }
            case WAIT_AUX_NODE_CONNECTED -> {
                AuxMachineFixture fixture = currentAuxiliaryMachine();
                if (!(mc.screen instanceof AcademyBaseUI<?> ui)
                        || !ui.hasActiveMatrixNetworkForVisualGate()) return;
                if (!serverAssertionComplete && !operationPending) {
                    assertAuxiliaryNodeJoinedMatrix(mc, fixture);
                }
                if (!serverAssertionComplete || operationPending || operationFailure != null || stageTicks < 12) return;
                evidence(fixture.label() + " joined the Matrix without sharing stale state from another node menu");
                enter(Stage.CAPTURE_AUX_NODE_CONNECTED);
            }
            case CAPTURE_AUX_NODE_CONNECTED -> {
                AuxMachineFixture fixture = currentAuxiliaryMachine();
                Stage next = hasNextAuxiliaryMachine() ? Stage.WAIT_AUX_MACHINE : Stage.WAIT_JADE_WIND;
                capture(mc, fixture.screenshot().replace(".png", "-connected.png"), next,
                        () -> advanceAuxiliaryMachine(mc));
            }
            case WAIT_JADE_WIND -> {
                if (!readyForJadeCapture(mc, windBasePos.above()) || stageTicks < 20) return;
                evidence("Jade targeted the upper wind-base proxy while the active CP HUD was visible");
                enter(Stage.CAPTURE_JADE_WIND);
            }
            case CAPTURE_JADE_WIND -> capture(mc, "academy-gate-jade-wind-base-proxy.png",
                    Stage.WAIT_JADE_MATRIX, () -> prepareJadeView(mc, matrixJadeTarget, false));
            case WAIT_JADE_MATRIX -> {
                if (!readyForJadeCapture(mc, matrixJadeTarget) || stageTicks < 20) return;
                evidence("Jade targeted an upper Matrix proxy as the same logical Matrix machine");
                enter(Stage.CAPTURE_JADE_MATRIX);
            }
            case CAPTURE_JADE_MATRIX -> capture(mc, "academy-gate-jade-matrix-proxy.png",
                    Stage.WAIT_ABILITY_SYNC, () -> prepareAbilityInputTest(mc));
            case WAIT_ABILITY_SYNC -> {
                if (operationPending || mc.screen != null || mc.player == null) return;
                var data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
                if (data.getCurrentAbility() != AbilityCategory.ELECTROMASTER
                        || !data.hasLearnedSkill("arc_gen")
                        || !"arc_gen".equals(data.getSlotSkillId(0, 2))
                        || data.isAbilityActive()) return;
                KeyInputHandler.TOGGLE_ABILITY.setDown(true);
                enter(Stage.WAIT_ABILITY_TOGGLE_DOWN);
            }
            case WAIT_ABILITY_TOGGLE_DOWN -> {
                if (stageTicks < 3) return;
                KeyInputHandler.TOGGLE_ABILITY.setDown(false);
                enter(Stage.WAIT_ABILITY_ACTIVE);
            }
            case WAIT_ABILITY_ACTIVE -> {
                if (mc.player == null || !mc.player.getData(AcademyAttachments.PLAYER_ABILITY)
                        .isAbilityActive()) return;
                KeyInputHandler.SKILL_3.setDown(true);
                enter(Stage.WAIT_ABILITY_SKILL_DOWN);
            }
            case WAIT_ABILITY_SKILL_DOWN -> {
                if (stageTicks < 3) return;
                KeyInputHandler.SKILL_3.setDown(false);
                enter(Stage.WAIT_ABILITY_EXECUTED);
            }
            case WAIT_ABILITY_EXECUTED -> {
                if (!serverAssertionComplete && !operationPending) assertAbilityExecuted(mc);
                if (!serverAssertionComplete || operationPending || operationFailure != null) return;
                evidence("V activation and mapped keyboard skill traversed KeyMapping, C2S authority and ArcGen resource settlement");
                prepareChargingInputTest(mc);
                enter(Stage.WAIT_CHARGING_SYNC);
            }
            case WAIT_CHARGING_SYNC -> {
                if (operationPending || mc.player == null) return;
                var data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
                if (!data.isAbilityActive() || !data.hasLearnedSkill("charging")
                        || !"charging".equals(data.getSlotSkillId(0, 0))) return;
                KeyInputHandler.SKILL_1.setDown(true);
                enter(Stage.WAIT_CHARGING_ACTIVE);
            }
            case WAIT_CHARGING_ACTIVE -> {
                if (stageTicks < 8) return;
                if (!serverAssertionComplete && !operationPending) assertChargingStarted(mc);
                if (!serverAssertionComplete || operationPending || operationFailure != null) return;
                KeyInputHandler.SKILL_1.setDown(false);
                enter(Stage.WAIT_CHARGING_RELEASED);
            }
            case WAIT_CHARGING_RELEASED -> {
                if (stageTicks < 5 || KeyInputHandler.isSkillHeld(0)) return;
                if (!serverAssertionComplete && !operationPending) assertChargingReleased(mc);
                if (!serverAssertionComplete || operationPending || operationFailure != null) return;
                evidence("mapped legacy left-mouse skill completed the charging handshake, authoritative CP drain and key-up release");
                succeed(mc);
            }
            case FINISHED -> {}
        }
    }

    private static void prepareFixturesAndOpenDeveloper(Minecraft mc) {
        runServer(mc, (level, player) -> {
            BlockPos playerPos = player.blockPosition();
            int baseY = Math.max(playerPos.getY() + 3,
                    level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            playerPos.getX(), playerPos.getZ()) + 3);
            BlockPos origin = new BlockPos(playerPos.getX(), baseY, playerPos.getZ());
            developerPos = origin.offset(-6, 0, 0).immutable();
            normalDeveloperPos = origin.offset(-6, 0, 7).immutable();
            phasePos = origin.offset(2, 0, 0).immutable();
            phaseRapidPos = origin.offset(4, 0, 3).immutable();
            // Keep the protected basic node inside its own nine-block radio
            // radius from both developer and phase generators.  Discovery is
            // intentionally symmetric with ConnectToNodePacket; placing this
            // fixture twelve blocks from the developer only exercised the two
            // unprotected higher-tier nodes and could never test authentication.
            nodePos = origin.offset(3, 0, 0).immutable();
            matrixPos = origin.offset(11, 0, 0).immutable();
            interfererPos = origin.offset(0, 0, 8).immutable();
            standardNodePos = origin.offset(2, 0, 8).immutable();
            advancedNodePos = origin.offset(4, 0, 8).immutable();
            imagFusorPos = origin.offset(6, 0, 8).immutable();
            solarPos = origin.offset(8, 0, 8).immutable();
            rfInputPos = origin.offset(10, 0, 8).immutable();
            rfOutputPos = origin.offset(12, 0, 8).immutable();
            metalFormerPos = origin.offset(14, 0, 8).immutable();
            windBasePos = origin.offset(28, 0, 0).immutable();
            windMainPos = windBasePos.above(2 + WindGenBase.MIN_PILLARS).immutable();

            clearFixtureArea(level, origin);
            placeDeveloper(level, player);
            placeNormalDeveloper(level, player);
            placePhaseAndNode(level, player);
            placeMatrix(level, player);
            placeAuxiliaryMachines(level, player);
            placeWind(level, player);

            AUX_MACHINES.clear();
            AUX_MACHINES.add(new AuxMachineFixture(AuxMachine.NODE_STANDARD, standardNodePos,
                    NodeStandardGui.class, "standard wireless node", "academy-gate-node-standard.png"));
            AUX_MACHINES.add(new AuxMachineFixture(AuxMachine.NODE_ADVANCED, advancedNodePos,
                    NodeAdvancedGui.class, "advanced wireless node", "academy-gate-node-advanced.png"));
            // Inspect the seeded node tiers before any receiver starts drawing
            // from the live network.  Exact initial energy is meaningful here;
            // after the interferer connects it is expected to change every tick.
            AUX_MACHINES.add(new AuxMachineFixture(AuxMachine.INTERFERER, interfererPos,
                    AbilityInterfererGui.class, "ability interferer", "academy-gate-interferer.png"));
            AUX_MACHINES.add(new AuxMachineFixture(AuxMachine.IMAG_FUSOR, imagFusorPos,
                    ImagFusorGui.class, "imaginary fusion machine", "academy-gate-imag-fusor.png"));
            AUX_MACHINES.add(new AuxMachineFixture(AuxMachine.SOLAR, solarPos,
                    SolarGenGui.class, "solar generator", "academy-gate-solar-generator.png"));
            AUX_MACHINES.add(new AuxMachineFixture(AuxMachine.RF_INPUT, rfInputPos,
                    EnergyBridgeGui.class, "RF/FE input bridge", "academy-gate-rf-input.png"));
            AUX_MACHINES.add(new AuxMachineFixture(AuxMachine.RF_OUTPUT, rfOutputPos,
                    EnergyBridgeGui.class, "RF/FE output bridge", "academy-gate-rf-output.png"));
            AUX_MACHINES.add(new AuxMachineFixture(AuxMachine.METAL_FORMER, metalFormerPos,
                    MetalFomerGui.class, "metal former", "academy-gate-metal-former.png"));

            var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            data.reset();
            data.syncTo(player);
            player.getInventory().clearContent();
            player.inventoryMenu.broadcastChanges();
            interact(level, player, developerPos);
        });
    }

    private static void clearFixtureArea(ServerLevel level, BlockPos origin) {
        // Small isolated slab for ordinary machines.
        for (int x = -9; x <= 16; x++) {
            for (int z = -4; z <= 11; z++) {
                for (int y = -1; y <= 5; y++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // Wind head needs the official 1.12.2 15x15 clearance plane around a 10-block tower.
        for (int x = 21; x <= 35; x++) {
            for (int z = -7; z <= 7; z++) {
                for (int y = -1; y <= 17; y++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void placeDeveloper(ServerLevel level, ServerPlayer player) {
        level.setBlock(developerPos.below(), Blocks.STONE.defaultBlockState(), 3);
        player.setYRot(0.0f);
        if (!placeOnTop(player, new ItemStack(AcademyItems.DEV_ADVANCED.get()), developerPos.below())
                || !level.getBlockState(developerPos).is(AcademyBlocks.DEV_ADVANCED.get())) {
            throw new IllegalStateException("production advanced-developer item did not place its multiblock");
        }
        assertDeveloperStructure(level, developerPos);
        if (!(level.getBlockEntity(developerPos) instanceof DevAdvancedBlockEntity developer)) {
            throw new IllegalStateException("advanced developer block entity missing");
        }
        // The quick-play gate deliberately reuses its world.  WiWorldData is
        // coordinate based, so make the fixture's initial condition explicit:
        // this screen must prove a fresh player-driven link, not inherit one
        // left by a previous successful run at the same coordinates.
        WirelessSystem.unlinkUser(level, developer);
        if (WirelessSystem.getUserConnection(level, developer) != null) {
            throw new IllegalStateException("advanced developer retained a stale wireless link");
        }
        developer.setEnergy(DevAdvancedBlockEntity.MAX_ENERGY);
    }

    private static void placeNormalDeveloper(ServerLevel level, ServerPlayer player) {
        level.setBlock(normalDeveloperPos.below(), Blocks.STONE.defaultBlockState(), 3);
        player.setYRot(0.0f);
        if (!placeOnTop(player, new ItemStack(AcademyItems.DEV_NORMAL.get()), normalDeveloperPos.below())
                || !level.getBlockState(normalDeveloperPos).is(AcademyBlocks.DEV_NORMAL.get())) {
            throw new IllegalStateException("production normal-developer item did not place its multiblock");
        }
        assertDeveloperStructure(level, normalDeveloperPos);
        if (!(level.getBlockEntity(normalDeveloperPos) instanceof DevNormalBlockEntity developer)) {
            throw new IllegalStateException("normal developer block entity missing");
        }
        developer.setEnergy(DevNormalBlockEntity.MAX_ENERGY / 2);
    }

    private static void assertDeveloperStructure(ServerLevel level, BlockPos mainPos) {
        BlockState state = level.getBlockState(mainPos);
        if (!(state.getBlock() instanceof DevMachineBase machine)) {
            throw new IllegalStateException("developer main block disappeared at " + mainPos);
        }
        Direction direction = state.getValue(DevMachineBase.FACING).getOpposite();
        for (DevMachineBase.SubBlockPos sub : machine.getRotatedSubBlocks(direction)) {
            BlockPos part = mainPos.offset(sub.dx(), sub.dy(), sub.dz());
            if (!level.getBlockState(part).is(machine.getStructureSubBlock())) {
                throw new IllegalStateException("developer transaction left an invalid proxy at " + part);
            }
        }
    }

    private static void placePhaseAndNode(ServerLevel level, ServerPlayer player) {
        level.setBlock(phasePos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(phaseRapidPos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(nodePos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(phasePos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        level.setBlock(phaseRapidPos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        level.setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(phasePos) instanceof PhaseGenBlockEntity phase)
                || !(level.getBlockEntity(phaseRapidPos) instanceof PhaseGenBlockEntity rapidPhase)
                || !(level.getBlockEntity(nodePos) instanceof BaseNodeBlockEntity node)) {
            throw new IllegalStateException("phase/node block entity missing");
        }
        phase.getItems().set(0, new ItemStack(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get(), 2));
        phase.setChanged();
        rapidPhase.getItems().set(0, new ItemStack(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get()));
        rapidPhase.setChanged();
        node.setOwnerUUID(player.getUUID());
        node.setNodeName("Gate Node");
        node.setPassword("gate-pass");
    }

    private static void placeMatrix(ServerLevel level, ServerPlayer player) {
        level.setBlock(matrixPos.below(), Blocks.STONE.defaultBlockState(), 3);
        player.setYRot(0.0f);
        if (!placeOnTop(player, new ItemStack(AcademyItems.MATRIX.get()), matrixPos.below())
                || !level.getBlockState(matrixPos).is(AcademyBlocks.MATRIX.get())) {
            throw new IllegalStateException("production Matrix item did not place its multiblock");
        }
        BlockState state = level.getBlockState(matrixPos);
        matrixJadeTarget = null;
        for (BlockPos proxy : Matrix.structurePositions(matrixPos, state)) {
            if (!level.getBlockState(proxy).is(AcademyBlocks.MATRIX_SUB.get())) {
                throw new IllegalStateException("production Matrix item left an invalid proxy at " + proxy);
            }
            // The Jade camera approaches from positive Z.  Pick the actual
            // upper proxy nearest that camera instead of assuming that
            // matrixPos.above() is exposed for every horizontal orientation.
            if (proxy.getY() == matrixPos.getY() + 1
                    && (matrixJadeTarget == null || proxy.getZ() > matrixJadeTarget.getZ())) {
                matrixJadeTarget = proxy.immutable();
            }
        }
        if (matrixJadeTarget == null) {
            throw new IllegalStateException("production Matrix had no upper proxy for Jade targeting");
        }
        if (!(level.getBlockEntity(matrixPos) instanceof MatrixBlockEntity matrix)) {
            throw new IllegalStateException("Matrix block entity missing");
        }
        if (!player.getUUID().equals(matrix.getOwnerUUID())) {
            throw new IllegalStateException("production Matrix placement did not persist its owner");
        }
        matrix.setSSID("");
        matrix.setPassword("");
        matrix.setInitialized(false);
        for (int slot = 0; slot < matrix.getItems().size(); slot++) {
            matrix.getItems().set(slot, ItemStack.EMPTY);
        }
        matrix.setChanged();
    }

    private static void placeAuxiliaryMachines(ServerLevel level, ServerPlayer player) {
        List<BlockPos> positions = List.of(interfererPos, standardNodePos, advancedNodePos,
                imagFusorPos, solarPos, rfInputPos, rfOutputPos, metalFormerPos);
        for (BlockPos pos : positions) level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(interfererPos, AcademyBlocks.ABILITY_INTERFERER.get().defaultBlockState(), 3);
        level.setBlock(standardNodePos, AcademyBlocks.NODE_STANDARD.get().defaultBlockState(), 3);
        level.setBlock(advancedNodePos, AcademyBlocks.NODE_ADVANCED.get().defaultBlockState(), 3);
        level.setBlock(imagFusorPos, AcademyBlocks.IMAG_FUSOR.get().defaultBlockState(), 3);
        level.setBlock(solarPos, AcademyBlocks.SOLAR_GEN.get().defaultBlockState(), 3);
        level.setBlock(rfInputPos, AcademyBlocks.RF_INPUT.get().defaultBlockState(), 3);
        level.setBlock(rfOutputPos, AcademyBlocks.RF_OUTPUT.get().defaultBlockState(), 3);
        level.setBlock(metalFormerPos, AcademyBlocks.METAL_FORMER.get().defaultBlockState(), 3);

        if (!(level.getBlockEntity(interfererPos) instanceof AbilityInterfererBlockEntity interferer)
                || !(level.getBlockEntity(standardNodePos) instanceof NodeStandardBlockEntity standard)
                || !(level.getBlockEntity(advancedNodePos) instanceof NodeAdvancedBlockEntity advanced)) {
            throw new IllegalStateException("auxiliary interferer/node block entity missing");
        }
        interferer.assignOwnerOnPlacement(player);
        interferer.setEnergy(interferer.getMaxEnergyStored() / 2);
        interferer.setRange(20);
        interferer.setEnabled(true);
        standard.setOwnerUUID(player.getUUID());
        standard.setNodeName("Gate Standard");
        standard.setPassword("");
        standard.setEnergy(25_000);
        advanced.setOwnerUUID(player.getUUID());
        advanced.setNodeName("Gate Advanced");
        advanced.setPassword("");
        advanced.setEnergy(100_000);
    }

    private static void placeWind(ServerLevel level, ServerPlayer player) {
        level.setBlock(windBasePos.below(), Blocks.STONE.defaultBlockState(), 3);
        player.setYRot(0.0f);
        if (!placeOnTop(player, new ItemStack(AcademyItems.WINDGEN_BASE.get()), windBasePos.below())
                || !level.getBlockState(windBasePos).is(AcademyBlocks.WINDGEN_BASE.get())
                || !level.getBlockState(windBasePos.above()).is(AcademyBlocks.WIND_GEN_BASE_SUB.get())) {
            throw new IllegalStateException("production wind-base item did not place its two-block structure");
        }
        for (int i = 0; i < WindGenBase.MIN_PILLARS; i++) {
            level.setBlock(windBasePos.above(2 + i),
                    AcademyBlocks.WINDGEN_PILLAR.get().defaultBlockState(), 3);
        }
        if (!placeOnTop(player, new ItemStack(AcademyItems.WINDGEN_MAIN.get()), windMainPos.below())
                || !level.getBlockState(windMainPos).is(AcademyBlocks.WINDGEN_MAIN.get())) {
            throw new IllegalStateException("production wind-head item did not place its three-block structure");
        }
        BlockState mainState = level.getBlockState(windMainPos);
        for (BlockPos proxy : WindGenMain.proxyPositions(windMainPos, mainState)) {
            if (!level.getBlockState(proxy).is(AcademyBlocks.WINDGEN_FAN.get())
                    || level.getBlockState(proxy).getValue(WindGenFan.FACING)
                    != mainState.getValue(WindGenMain.FACING)) {
                throw new IllegalStateException("production wind-head item left an invalid proxy at " + proxy);
            }
        }
        if (!(level.getBlockEntity(windMainPos) instanceof WindGenMainBlockEntity main)
                || !(level.getBlockEntity(windBasePos) instanceof WindGenBaseBlockEntity base)) {
            throw new IllegalStateException("wind block entity missing");
        }
        if (!player.getUUID().equals(main.getOwnerUUID())) {
            throw new IllegalStateException("production wind-head placement did not persist its owner");
        }
        ItemStack battery = new ItemStack(AcademyItems.ENERGY_UNIT.get());
        battery.setDamageValue(EnergyUnit.MAX_ENERGY);
        base.getItems().set(0, battery);
        base.setChanged();
        if (main.refreshFanState(level, windMainPos, mainState.getValue(WindGenMain.FACING))
                || !main.isStructureComplete() || main.isFanInstalled()) {
            throw new IllegalStateException("empty production wind fixture reported an installed fan");
        }
    }

    private static boolean placeOnTop(ServerPlayer player, ItemStack held, BlockPos support) {
        player.setPos(support.getX() + 0.5, support.getY() + 1.5, support.getZ() + 1.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(support), Direction.UP, support, false);
        return held.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction();
    }

    private static void prepareMatrixMaterialsAndOpen(Minecraft mc) {
        runServer(mc, (level, player) -> {
            player.getInventory().clearContent();
            player.getInventory().setItem(9, new ItemStack(AcademyItems.MAT_CORE_1.get(), 64));
            player.getInventory().setItem(10, new ItemStack(AcademyItems.CONSTRAINT_PLATE.get(), 64));
            player.inventoryMenu.broadcastChanges();
            interact(level, player, matrixPos);
        });
    }

    private static void shiftClickMatrixMaterials(Minecraft mc) {
        if (!(mc.screen instanceof MatrixGui gui) || mc.gameMode == null || mc.player == null) {
            throw new IllegalStateException("Matrix menu vanished before the real client Shift-click sequence");
        }
        for (int playerSlot = 0; playerSlot <= 1; playerSlot++) {
            mc.gameMode.handleInventoryMouseClick(gui.getMenu().containerId, playerSlot, 0,
                    ClickType.QUICK_MOVE, mc.player);
        }
    }

    private static void prepareFanAndOpenWindMain(Minecraft mc) {
        runServer(mc, (level, player) -> {
            player.getInventory().setItem(9, new ItemStack(AcademyItems.WINDGEN_FAN.get()));
            player.inventoryMenu.broadcastChanges();
            interact(level, player, windMainPos);
        });
    }

    private static void shiftClickPlayerSlot(Minecraft mc, int menuSlot) {
        if (!(mc.screen instanceof WindMainGui gui) || mc.gameMode == null || mc.player == null) {
            throw new IllegalStateException("wind menu vanished before the client shift-click");
        }
        mc.gameMode.handleInventoryMouseClick(gui.getMenu().containerId, menuSlot, 0,
                ClickType.QUICK_MOVE, mc.player);
    }

    private static void startAuxiliaryMachines(Minecraft mc) {
        if (AUX_MACHINES.isEmpty()) {
            throw new IllegalStateException("auxiliary machine fixture list was not initialized");
        }
        auxMachineIndex = 0;
        prepareAndOpenAuxiliaryMachine(mc, currentAuxiliaryMachine());
    }

    private static AuxMachineFixture currentAuxiliaryMachine() {
        if (auxMachineIndex < 0 || auxMachineIndex >= AUX_MACHINES.size()) {
            throw new IllegalStateException("auxiliary machine index out of range: " + auxMachineIndex);
        }
        return AUX_MACHINES.get(auxMachineIndex);
    }

    private static boolean hasNextAuxiliaryMachine() {
        return auxMachineIndex + 1 < AUX_MACHINES.size();
    }

    private static void advanceAuxiliaryMachine(Minecraft mc) {
        auxMachineIndex++;
        if (auxMachineIndex < AUX_MACHINES.size()) {
            prepareAndOpenAuxiliaryMachine(mc, currentAuxiliaryMachine());
        } else {
            prepareJadeView(mc, windBasePos.above(), true);
        }
    }

    private static void prepareAndOpenAuxiliaryMachine(Minecraft mc, AuxMachineFixture fixture) {
        runServer(mc, (level, player) -> {
            switch (fixture.kind()) {
                case INTERFERER -> {
                    if (!(level.getBlockEntity(fixture.pos()) instanceof AbilityInterfererBlockEntity interferer)) {
                        throw new IllegalStateException("ability interferer block entity missing");
                    }
                    interferer.assignOwnerOnPlacement(player);
                    interferer.setEnergy(interferer.getMaxEnergyStored() / 2);
                    interferer.setRange(20);
                    interferer.setEnabled(true);
                }
                case IMAG_FUSOR -> {
                    if (!(level.getBlockEntity(fixture.pos()) instanceof ImagFusorBlockEntity fusor)) {
                        throw new IllegalStateException("imaginary fusion block entity missing");
                    }
                    fusor.getItems().set(ImagFusorBlockEntity.INPUT_SLOT,
                            new ItemStack(AcademyItems.CRYSTAL_LOW.get(), 2));
                    fusor.getItems().set(ImagFusorBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
                    fusor.getItems().set(ImagFusorBlockEntity.FLUID_INPUT_SLOT, ItemStack.EMPTY);
                    fusor.getItems().set(ImagFusorBlockEntity.EMPTY_UNIT_SLOT, ItemStack.EMPTY);
                    fusor.getFluidTank().setFluid(new net.neoforged.neoforge.fluids.FluidStack(
                            com.mohistmc.academy.world.AcademyFluids.PHASE_LIQUID.get(), 8_000));
                    fusor.injectEnergy(fusor.getMaxEnergy());
                    fusor.setChanged();
                }
                case METAL_FORMER -> {
                    if (!(level.getBlockEntity(fixture.pos()) instanceof MetalFomerBlockEntity former)) {
                        throw new IllegalStateException("metal former block entity missing");
                    }
                    former.getItems().set(MetalFomerBlockEntity.SLOT_IN, new ItemStack(Items.IRON_INGOT, 2));
                    former.getItems().set(MetalFomerBlockEntity.SLOT_OUT, ItemStack.EMPTY);
                    former.injectEnergy(MetalFomerBlockEntity.MAX_ENERGY);
                    former.setChanged();
                }
                case SOLAR -> {
                    level.setDayTime(6_000L);
                    level.setWeatherParameters(0, 6_000, false, false);
                    if (!(level.getBlockEntity(fixture.pos()) instanceof SolarGenBlockEntity solar)) {
                        throw new IllegalStateException("solar generator block entity missing");
                    }
                    solar.setEnergy(500);
                }
                case RF_INPUT, RF_OUTPUT -> {
                    if (!(level.getBlockEntity(fixture.pos()) instanceof EnergyBridgeBlockEntity bridge)) {
                        throw new IllegalStateException("energy bridge block entity missing");
                    }
                    bridge.receiveExternalFe(1_600, false);
                }
                default -> {
                    // Interferer and node data were seeded with their owner at fixture placement.
                }
            }
            interact(level, player, fixture.pos());
        });
    }

    private static void assertAuxiliaryScreen(AuxMachineFixture fixture, Screen screen) {
        switch (fixture.kind()) {
            case INTERFERER -> {
                AbilityInterfererGui gui = (AbilityInterfererGui) screen;
                if (gui.getMenu().getEnergy() <= 0 || gui.getMenu().getMaxEnergy() <= 0
                        || gui.getMenu().getRange() != 20 || !gui.getMenu().isEnabled()) {
                    throw new IllegalStateException("interferer menu lost its authoritative state: energy="
                            + gui.getMenu().getEnergy() + ", max=" + gui.getMenu().getMaxEnergy()
                            + ", range=" + gui.getMenu().getRange() + ", enabled="
                            + gui.getMenu().isEnabled());
                }
            }
            case NODE_STANDARD -> {
                NodeStandardGui gui = (NodeStandardGui) screen;
                if (gui.getMenu().getNodeEnergy() != 25_000
                        || gui.getMenu().getNodeMaxEnergy() != 50_000
                        || gui.getMenu().getNodeBandwidth() != 300
                        || !"Gate Standard".equals(gui.getMenu().getInitialNodeName())
                        || !gui.getMenu().canEditNode()) {
                    throw new IllegalStateException("standard-node menu data was truncated or bound to the wrong tier");
                }
            }
            case NODE_ADVANCED -> {
                NodeAdvancedGui gui = (NodeAdvancedGui) screen;
                if (gui.getMenu().getNodeEnergy() != 100_000
                        || gui.getMenu().getNodeMaxEnergy() != 200_000
                        || gui.getMenu().getNodeBandwidth() != 900
                        || !"Gate Advanced".equals(gui.getMenu().getInitialNodeName())
                        || !gui.getMenu().canEditNode()) {
                    throw new IllegalStateException("advanced-node menu data was truncated or bound to the wrong tier");
                }
            }
            case IMAG_FUSOR -> {
                ImagFusorGui gui = (ImagFusorGui) screen;
                if (gui.getMenu().getFluidAmount() != 8_000
                        || gui.getMenu().getEnergy() <= 0
                        || gui.getMenu().getCurrentRecipePhaseLiquid() != 3_000
                        || gui.getMenu().getProcessingTime() <= 0) {
                    throw new IllegalStateException("imaginary fusion menu did not expose a live official recipe");
                }
            }
            case SOLAR -> {
                SolarGenGui gui = (SolarGenGui) screen;
                if (gui.getMenu().getMaxEnergy() != 1_000 || gui.getMenu().getEnergy() < 500
                        || gui.getMenu().getStatus() != SolarGenBlockEntity.SolarStatus.STRONG) {
                    throw new IllegalStateException("solar menu did not expose the seeded daylight status");
                }
            }
            case RF_INPUT, RF_OUTPUT -> {
                EnergyBridgeGui gui = (EnergyBridgeGui) screen;
                boolean wantedInput = fixture.kind() == AuxMachine.RF_INPUT;
                if (gui.getMenu().isInput() != wantedInput
                        || Math.abs(gui.getMenu().getStoredIf() - 400.0) > 0.001
                        || Math.abs(gui.getMenu().getMaxIf() - 2_000.0) > 0.001) {
                    throw new IllegalStateException("energy bridge menu direction or 4:1 buffer conversion was wrong");
                }
            }
            case METAL_FORMER -> {
                MetalFomerGui gui = (MetalFomerGui) screen;
                if (gui.getMenu().getEnergy() <= 0
                        || gui.getMenu().getMode() != com.mohistmc.academy.crafting.MetalFormerRecipes.Mode.PLATE
                        || gui.getMenu().getProgress() <= 0) {
                    throw new IllegalStateException("metal former menu did not expose a live plate recipe");
                }
            }
        }
        if (screen instanceof AcademyBaseUI<?> academy && !fixture.pos().equals(academy.getMenu().pos)) {
            throw new IllegalStateException(fixture.label() + " screen was correlated to another block position");
        }
    }

    private static void assertAuxiliaryNodeJoinedMatrix(Minecraft mc, AuxMachineFixture fixture) {
        operationPending = true;
        runServerUnchecked(mc, (level, player) -> {
            if (!(level.getBlockEntity(fixture.pos()) instanceof BaseNodeBlockEntity node)
                    || !(level.getBlockEntity(matrixPos) instanceof MatrixBlockEntity matrix)) {
                throw new IllegalStateException("auxiliary node or Matrix vanished before network assertion");
            }
            WiWorldData data = WiWorldData.getNonCreate(level);
            if (data == null || data.getNetwork(node) == null
                    || data.getNetwork(node) != data.getNetwork(matrix)) {
                throw new IllegalStateException(fixture.label() + " did not persist the selected Matrix network");
            }
            serverAssertionComplete = true;
        });
    }

    private static void assertAuxiliaryMachineNodeLink(Minecraft mc, AuxMachineFixture fixture) {
        operationPending = true;
        runServerUnchecked(mc, (level, player) -> {
            if (!(level.getBlockEntity(fixture.pos()) instanceof IWirelessUser user)) {
                throw new IllegalStateException(fixture.label()
                        + " is not a wireless generator or receiver on the server");
            }
            WiWorldData data = WiWorldData.getNonCreate(level);
            var connection = data == null ? null : data.getNodeConnection(user);
            if (connection == null || connection.getNode() == null) {
                throw new IllegalStateException(fixture.label()
                        + " did not persist the selected standalone-node connection");
            }
            serverAssertionComplete = true;
        });
    }

    private static void prepareJadeView(Minecraft mc, BlockPos target, boolean configureAbility) {
        if (mc.player == null) throw new IllegalStateException("client player vanished before Jade capture");
        if (target == null) throw new IllegalStateException("Jade target was not initialized");
        mc.player.closeContainer();
        runServer(mc, (level, player) -> {
            if (target.equals(matrixJadeTarget)) {
                BlockState matrixState = level.getBlockState(matrixPos);
                if (!(matrixState.getBlock() instanceof Matrix)
                        || !level.getBlockState(target).is(AcademyBlocks.MATRIX_SUB.get())
                        || !Matrix.structurePositions(matrixPos, matrixState).contains(target)) {
                    throw new IllegalStateException("Matrix upper proxy vanished or no longer belonged to its main block");
                }
            }
            player.closeContainer();
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            player.teleportTo(level, target.getX() + 0.5, target.getY(), target.getZ() + 3.5,
                    180.0f, 0.0f);
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    Vec3.atCenterOf(target));
            if (configureAbility) {
                var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
                data.reset();
                data.setCurrentAbility(AbilityCategory.ELECTROMASTER);
                data.setPlayerLevel(3);
                data.recalculateMaxResources(true);
                data.setAbilityActive(true);
                data.syncTo(player);
            }
        });
    }

    private static boolean readyForJadeCapture(Minecraft mc, BlockPos target) {
        if (operationPending || mc.screen != null || mc.player == null || mc.level == null) return false;
        if (!(mc.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(target)) return false;
        var data = mc.player.getData(AcademyAttachments.PLAYER_ABILITY);
        return data.hasAbility() && data.isAbilityActive();
    }

    private static void configureResetAndOpenDeveloper(Minecraft mc) {
        runServer(mc, (level, player) -> {
            var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            data.reset();
            data.setCurrentAbility(AbilityCategory.ELECTROMASTER);
            data.setPlayerLevel(3);
            data.recalculateMaxResources(true);
            player.getInventory().clearContent();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(AcademyItems.MAGNETIC_COIL.get()));
            player.getInventory().setItem(9, new ItemStack(AcademyItems.FACTOR_TELEPORTER.get()));
            player.inventoryMenu.broadcastChanges();
            data.syncTo(player);
            interact(level, player, developerPos);
        });
    }

    private static void openBlock(Minecraft mc, BlockPos pos) {
        if (operationPending) throw new IllegalStateException("server operation already pending");
        runServer(mc, (level, player) -> interact(level, player, pos));
    }

    private static void interact(ServerLevel level, ServerPlayer player, BlockPos pos) {
        // Fixture machines sit above the copied world's irregular spawn cave.
        // Keep the synthetic player stationary so the eight-block vanilla
        // menu distance check measures the interaction, not a subsequent fall.
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 0.5,
                pos.getZ() + 2.5, 180.0f, 0.0f);
        BlockState state = level.getBlockState(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        InteractionResult result;
        Block block = state.getBlock();
        if (block instanceof DevMachineBase machine) {
            result = machine.useWithoutItem(state, level, pos, player, hit);
        } else if (block instanceof AbilityInterferer machine) {
            result = machine.useWithoutItem(state, level, pos, player, hit);
        } else if (block instanceof NodeStandard machine) {
            result = machine.useWithoutItem(state, level, pos, player, hit);
        } else if (block instanceof NodeAdvanced machine) {
            result = machine.useWithoutItem(state, level, pos, player, hit);
        } else if (block instanceof ImagFusor machine) {
            result = machine.useWithoutItem(state, level, pos, player, hit);
        } else if (block instanceof SolarGen machine) {
            result = machine.useWithoutItem(state, level, pos, player, hit);
        } else if (block instanceof EnergyBridgeBlock machine) {
            result = machine.useWithoutItem(state, level, pos, player, hit);
        } else if (block instanceof MetalFomer machine) {
            result = machine.useWithoutItem(state, level, pos, player, hit);
        } else if (block instanceof PhaseGen machine) {
            result = machine.useWithoutItem(state, level, pos, player, hit);
        } else if (block instanceof NodeBasic machine) {
            result = machine.useWithoutItem(state, level, pos, player, hit);
        } else if (block instanceof Matrix machine) {
            result = machine.useWithoutItem(state, level, pos, player, hit);
        } else if (block instanceof WindGenMain machine) {
            result = machine.useWithoutItem(state, level, pos, player, hit);
        } else if (block instanceof WindGenBase machine) {
            result = machine.useWithoutItem(state, level, pos, player, hit);
        } else {
            throw new IllegalStateException("unsupported fixture block at " + pos + ": " + block);
        }
        if (!result.consumesAction()) {
            throw new IllegalStateException("interaction was not consumed at " + pos + ": " + result);
        }
    }

    private static void assertStandalonePhaseLink(Minecraft mc) {
        operationPending = true;
        runServerUnchecked(mc, (level, player) -> {
            if (!(level.getBlockEntity(phasePos) instanceof PhaseGenBlockEntity phase)
                    || !(level.getBlockEntity(nodePos) instanceof BaseNodeBlockEntity node)) {
                throw new IllegalStateException("phase/node disappeared before standalone assertion");
            }
            WiWorldData data = WiWorldData.getNonCreate(level);
            if (data == null || data.getNodeConnection(phase) == null
                    || data.getNodeConnection(phase).getNode() != node) {
                throw new IllegalStateException("phase generator did not persist its standalone node link");
            }
            if (!(level.getBlockEntity(matrixPos) instanceof MatrixBlockEntity matrix)
                    || matrix.isInitialized()) {
                throw new IllegalStateException("standalone link was only tested after Matrix initialization");
            }
            // Keep later auxiliary-machine cases password-free; this mutation
            // happens only after the protected production link was verified.
            node.setPassword("");
            serverAssertionComplete = true;
        });
    }

    private static void assertDeveloperNodeLink(Minecraft mc) {
        operationPending = true;
        runServerUnchecked(mc, (level, player) -> {
            if (!(level.getBlockEntity(developerPos) instanceof DevAdvancedBlockEntity developer)
                    || !(level.getBlockEntity(nodePos) instanceof BaseNodeBlockEntity node)) {
                throw new IllegalStateException("developer/node disappeared before binding assertion");
            }
            WiWorldData data = WiWorldData.getNonCreate(level);
            var connection = data == null ? null : data.getNodeConnection(developer);
            if (connection == null || connection.getNode() != node) {
                throw new IllegalStateException("advanced developer did not persist its selected node connection");
            }
            serverAssertionComplete = true;
        });
    }

    private static void assertNodeRenamePersistedAndReopen(Minecraft mc) {
        operationPending = true;
        runServerUnchecked(mc, (level, player) -> {
            if (!(level.getBlockEntity(nodePos) instanceof BaseNodeBlockEntity node)
                    || !"Gate Renamed".equals(node.getNodeName())) {
                throw new IllegalStateException("node rename packet did not update the authoritative block entity");
            }
            net.minecraft.nbt.CompoundTag saved = node.saveWithFullMetadata(level.registryAccess());
            if (!"Gate Renamed".equals(saved.getString("node_name"))) {
                throw new IllegalStateException("node rename was absent from the serialized block-entity tag");
            }
            interact(level, player, nodePos);
            serverAssertionComplete = true;
        });
    }

    private static void prepareAbilityInputTest(Minecraft mc) {
        if (mc.player != null) mc.player.closeContainer();
        runServer(mc, (level, player) -> {
            player.closeContainer();
            var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            data.reset();
            data.setCurrentAbility(AbilityCategory.ELECTROMASTER);
            data.setPlayerLevel(1);
            data.learnSkill("arc_gen");
            data.setCurrentPreset(0);
            data.setSlot(0, 2, "arc_gen");
            data.setAbilityActive(false);
            data.recalculateMaxResources(true);
            abilityStartCp = data.getCurrentCp();
            data.syncTo(player);
        });
    }

    private static void assertAbilityExecuted(Minecraft mc) {
        operationPending = true;
        runServerUnchecked(mc, (level, player) -> {
            var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            if (!data.isAbilityActive() || data.getCurrentCp() >= abilityStartCp
                    || !data.isOnCooldown("arc_gen")) {
                throw new IllegalStateException("mapped ArcGen key did not execute authoritatively: active="
                        + data.isAbilityActive() + ", cp=" + data.getCurrentCp()
                        + "/" + abilityStartCp + ", cooldown=" + data.getCooldownTicks("arc_gen"));
            }
            serverAssertionComplete = true;
        });
    }

    private static void prepareChargingInputTest(Minecraft mc) {
        runServer(mc, (level, player) -> {
            var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            data.reset();
            data.setCurrentAbility(AbilityCategory.ELECTROMASTER);
            data.setPlayerLevel(1);
            data.learnSkill("charging");
            data.setCurrentPreset(0);
            data.setSlot(0, 0, "charging");
            data.setAbilityActive(true);
            data.recalculateMaxResources(true);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            chargingStartCp = data.getCurrentCp();
            data.syncTo(player);
        });
    }

    private static void assertChargingStarted(Minecraft mc) {
        operationPending = true;
        runServerUnchecked(mc, (level, player) -> {
            var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            if (!SkillChargingManager.isCharging(player.getUUID())
                    || data.getCurrentCp() >= chargingStartCp) {
                throw new IllegalStateException("mapped charging key did not create an authoritative session: cp="
                        + data.getCurrentCp() + "/" + chargingStartCp + ", charging="
                        + SkillChargingManager.isCharging(player.getUUID()));
            }
            serverAssertionComplete = true;
        });
    }

    private static void assertChargingReleased(Minecraft mc) {
        operationPending = true;
        runServerUnchecked(mc, (level, player) -> {
            var data = player.getData(AcademyAttachments.PLAYER_ABILITY);
            if (SkillChargingManager.isCharging(player.getUUID())
                    || data.getCurrentCp() >= chargingStartCp) {
                throw new IllegalStateException("charging key-up did not close the authoritative session: cp="
                        + data.getCurrentCp() + "/" + chargingStartCp + ", charging="
                        + SkillChargingManager.isCharging(player.getUUID()));
            }
            serverAssertionComplete = true;
        });
    }

    private static void assertMatrixMaterialsPreserved(Minecraft mc) {
        operationPending = true;
        runServerUnchecked(mc, (level, player) -> {
            if (!(level.getBlockEntity(matrixPos) instanceof MatrixBlockEntity matrix)
                    || !matrix.isOperational() || matrix.getItems().size() != 4
                    || matrix.getItems().stream().anyMatch(stack -> stack.isEmpty() || stack.getCount() != 1)) {
                throw new IllegalStateException("Matrix initialization consumed or lost installed components");
            }
            serverAssertionComplete = true;
        });
    }

    private static void assertNodeStillServesPhase(Minecraft mc) {
        operationPending = true;
        runServerUnchecked(mc, (level, player) -> {
            if (!(level.getBlockEntity(phasePos) instanceof PhaseGenBlockEntity phase)
                    || !(level.getBlockEntity(nodePos) instanceof BaseNodeBlockEntity node)) {
                throw new IllegalStateException("phase/node disappeared after Matrix link");
            }
            WiWorldData data = WiWorldData.getNonCreate(level);
            if (data == null || data.getNodeConnection(phase) == null
                    || data.getNodeConnection(phase).getNode() != node
                    || data.getNetwork(node) == null) {
                throw new IllegalStateException("joining Matrix discarded the standalone machine connection");
            }
            serverAssertionComplete = true;
        });
    }

    private static void assertWindGenerated(Minecraft mc) {
        operationPending = true;
        runServerUnchecked(mc, (level, player) -> {
            if (!(level.getBlockEntity(windMainPos) instanceof WindGenMainBlockEntity main)
                    || !(level.getBlockEntity(windBasePos) instanceof WindGenBaseBlockEntity base)
                    || !main.isStructureComplete() || !main.isFanInstalled() || !main.isWorking()
                    || base.getItems().getFirst().getCount() != 1
                    || base.getItems().getFirst().getDamageValue() >= EnergyUnit.MAX_ENERGY) {
                throw new IllegalStateException("wind turbine did not produce energy in the real client world");
            }
            serverAssertionComplete = true;
        });
    }

    private static void runServer(Minecraft mc, BiConsumer<ServerLevel, ServerPlayer> action) {
        if (operationPending) return;
        operationPending = true;
        runServerUnchecked(mc, action);
    }

    private static void runServerUnchecked(Minecraft mc, BiConsumer<ServerLevel, ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        UUID playerId = mc.player == null ? null : mc.player.getUUID();
        if (server == null || playerId == null) {
            operationPending = false;
            operationFailure = "integrated server/player vanished";
            return;
        }
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) throw new IllegalStateException("server player not found");
                action.accept(player.serverLevel(), player);
            } catch (Throwable failure) {
                operationFailure = failure.toString();
                LOGGER.error("Machine visual gate server operation failed", failure);
            } finally {
                operationPending = false;
            }
        });
    }

    private static void capture(Minecraft mc, String fileName, Stage next, Runnable after) {
        Path expected = mc.gameDirectory.toPath().resolve("screenshots").resolve(fileName);
        if (!screenshotStarted) {
            screenshotStarted = true;
            screenshotFinished = false;
            screenshotMessage = null;
            try {
                Files.deleteIfExists(expected);
            } catch (IOException failure) {
                throw new IllegalStateException("could not clear screenshot target " + expected, failure);
            }
            Screenshot.grab(mc.gameDirectory, fileName, mc.getMainRenderTarget(), message -> {
                screenshotMessage = message.getString();
                screenshotFinished = true;
            });
            return;
        }
        if (!screenshotFinished) return;
        if (!Files.isRegularFile(expected)) {
            throw new IllegalStateException("screenshot callback completed without file: " + screenshotMessage);
        }
        evidence("captured " + fileName + " (" + screenshotMessage + ")");
        after.run();
        enter(next);
    }

    private static void enter(Stage next) {
        stage = next;
        stageTicks = 0;
        stageEnteredAtNanos = System.nanoTime();
        screenshotStarted = false;
        screenshotFinished = false;
        screenshotMessage = null;
        serverAssertionComplete = false;
        LOGGER.info("Machine visual gate -> {}", next);
    }

    private static long stageAgeMillis() {
        return (System.nanoTime() - stageEnteredAtNanos) / 1_000_000L;
    }

    private static void evidence(String line) {
        EVIDENCE.add(line);
        LOGGER.info("Machine visual gate evidence: {}", line);
    }

    private static void succeed(Minecraft mc) {
        releaseSyntheticKeys();
        writeResult(mc, "PASS", null);
        LOGGER.info("Machine visual gate completed with {} evidence rows", EVIDENCE.size());
        stage = Stage.FINISHED;
        mc.stop();
    }

    private static void fail(Minecraft mc, String reason) {
        if (stage == Stage.FINISHED) return;
        releaseSyntheticKeys();
        LOGGER.error("Machine visual gate FAILED: {}", reason);
        writeResult(mc, "FAIL", reason);
        stage = Stage.FINISHED;
        mc.stop();
    }

    private static void writeResult(Minecraft mc, String status, String reason) {
        Path result = mc.gameDirectory.toPath().resolve("academy-machine-gate-result.txt");
        List<String> lines = new ArrayList<>();
        lines.add("status=" + status);
        lines.add("stage=" + stage);
        if (reason != null) lines.add("reason=" + reason);
        lines.add("evidence=" + EVIDENCE.size());
        for (int i = 0; i < EVIDENCE.size(); i++) lines.add((i + 1) + ". " + EVIDENCE.get(i));
        try {
            Files.write(result, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
        } catch (IOException failure) {
            LOGGER.error("Could not write machine visual gate result", failure);
        }
    }

    private static String screenName(Screen screen) {
        return screen == null ? "null" : screen.getClass().getName();
    }

    private static void releaseSyntheticKeys() {
        KeyInputHandler.TOGGLE_ABILITY.setDown(false);
        KeyInputHandler.SKILL_3.setDown(false);
    }
}
