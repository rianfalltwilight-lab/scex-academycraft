package com.mohistmc.academy.gametest;

import com.mohistmc.academy.AcademyCraft;
import com.mohistmc.academy.entity.PlasmaOrbEntity;
import com.mohistmc.academy.entity.ShieldEffectEntity;
import com.mohistmc.academy.network.FlashingStatePacket;
import com.mohistmc.academy.network.MineDetectResultPacket;
import com.mohistmc.academy.network.LocationConsentResponsePacket;
import com.mohistmc.academy.network.LocationTeleportSyncPacket;
import com.mohistmc.academy.network.SafePayloadSender;
import com.mohistmc.academy.network.SyncChargingStatePacket;
import com.mohistmc.academy.network.SettingsConfigPacket;
import com.mohistmc.academy.skill.SkillChargingManager;
import com.mohistmc.academy.skill.PlayerAbilityData;
import com.mohistmc.academy.skill.PlayerAbilityDataCodec;
import com.mohistmc.academy.skill.ability.teleporter.FlashingSessionManager;
import com.mohistmc.academy.skill.ability.teleporter.FlashingTargeting;
import com.mohistmc.academy.skill.ability.teleporter.MarkTeleportEffect;
import com.mohistmc.academy.skill.ability.electromaster.RailgunEffect;
import com.mohistmc.academy.skill.ability.meltdowner.RayBarrageEffect;
import com.mohistmc.academy.entity.MeltdownBarrageEntity;
import com.mohistmc.academy.entity.MeltdownBeamEntity;
import com.mohistmc.academy.world.AcademyEntities;
import com.mohistmc.academy.world.AcademyBlocks;
import com.mohistmc.academy.world.AcademyItems;
import com.mohistmc.academy.world.block.entity.MatrixBlockEntity;
import com.mohistmc.academy.world.block.entity.PhaseGenBlockEntity;
import com.mohistmc.academy.world.block.entity.ImagFusorBlockEntity;
import com.mohistmc.academy.world.block.entity.DevAdvancedBlockEntity;
import com.mohistmc.academy.world.block.entity.NodeBasicBlockEntity;
import com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity;
import com.mohistmc.academy.world.block.entity.AcademyContainerBlockEntity;
import com.mohistmc.academy.world.block.entity.SolarGenBlockEntity;
import com.mohistmc.academy.energy.impl.WirelessSystem;
import com.mohistmc.academy.energy.impl.WiWorldData;
import com.mohistmc.academy.world.menu.MatrixMenu;
import com.mohistmc.academy.world.menu.PhaseGenMenu;
import com.mohistmc.academy.world.menu.ImagFusorMenu;
import com.mohistmc.academy.world.menu.DevAdvancedMenu;
import com.mohistmc.academy.crafting.AcademyRecipeTypes;
import com.mohistmc.academy.crafting.ImagFusorRecipe;
import com.mohistmc.academy.crafting.ImagFusorRecipeInput;
import com.mohistmc.academy.crafting.MetalFormingRecipe;
import com.mohistmc.academy.crafting.MetalFormingRecipeInput;
import com.mohistmc.academy.crafting.MetalFormerRecipes;
import com.mohistmc.academy.world.block.entity.MetalFomerBlockEntity;
import com.mohistmc.academy.world.entity.MagManipBlockEntity;
import com.mohistmc.academy.world.entity.MagManipTransferPolicy;
import com.mohistmc.academy.world.entity.MagManipTransactionData;
import com.mohistmc.academy.world.entity.EntitySilbarn;
import com.mohistmc.academy.world.entity.EntityMagHook;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import com.mohistmc.academy.network.MatrixNodesPacket;
import com.mohistmc.academy.world.item.MatterUnitPermissions;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.function.Consumer;
import com.mohistmc.academy.api.event.AbilityEvents;
import com.mohistmc.academy.skill.AbilityMutationService;
import com.mohistmc.academy.skill.AcademyAttachments;
import com.mohistmc.academy.skill.SkillRegistry;
import com.mohistmc.academy.skill.AcademyDamageHelper;
import com.mohistmc.academy.config.ACConfig;
import com.mohistmc.academy.listener.ServerListener;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;

/** Dedicated-server integration tests. These deliberately avoid client-only state. */
@GameTestHolder(AcademyCraft.MODID)
@PrefixGameTestTemplate(false)
public final class AcademyServerGameTests {
    private static final String EMPTY = "empty";

    private AcademyServerGameTests() {}

    @GameTest(template = EMPTY)
    public static void academyDamageBoundaryHonorsLegacyPvpRuntimeSwitch(GameTestHelper helper) {
        ServerPlayer attacker=helper.makeMockServerPlayerInLevel();
        ServerPlayer playerTarget=helper.makeMockServerPlayerInLevel();
        attacker.setGameMode(GameType.SURVIVAL);playerTarget.setGameMode(GameType.SURVIVAL);
        Zombie mob=EntityType.ZOMBIE.create(helper.getLevel());
        if(mob==null){helper.fail("zombie target was not created");return;}
        mob.setPos(attacker.getX()+2,attacker.getY(),attacker.getZ());helper.getLevel().addFreshEntity(mob);
        boolean original=ACConfig.Server.PVP_ENABLED.get();
        try {
            if(!original){helper.fail("legacy PvP default must be true");return;}
            float playerStart=playerTarget.getHealth(),mobStart=mob.getHealth();
            ACConfig.Server.PVP_ENABLED.set(false);
            if(AcademyDamageHelper.allowsTarget(playerTarget)||!AcademyDamageHelper.allowsTarget(mob)){helper.fail("PvP-disabled live target policy mismatch");return;}
            if(AcademyDamageHelper.hurt(attacker,playerTarget,attacker.damageSources().playerAttack(attacker),2)||playerTarget.getHealth()!=playerStart){helper.fail("PvP-disabled Academy damage reached a player");return;}
            if(!AcademyDamageHelper.hurt(attacker,mob,attacker.damageSources().playerAttack(attacker),2)||mob.getHealth()>=mobStart){helper.fail("PvP switch incorrectly blocked a non-player");return;}
            ACConfig.Server.PVP_ENABLED.set(true);
            if(!AcademyDamageHelper.allowsTarget(playerTarget)||!AcademyDamageHelper.allowsTarget(mob)){helper.fail("runtime PvP enable did not take effect");return;}
        } finally { ACConfig.Server.PVP_ENABLED.set(original); }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void matrixCanAuthoritativelyLinkAndUnlinkLoadedNode(GameTestHelper helper) {
        BlockPos matrixPos = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos nodePos = helper.absolutePos(new BlockPos(2, 1, 1));
        helper.getLevel().setBlock(matrixPos, AcademyBlocks.MATRIX.get().defaultBlockState(), 3);
        helper.getLevel().setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        if (!(helper.getLevel().getBlockEntity(matrixPos) instanceof com.mohistmc.academy.world.block.entity.MatrixBlockEntity matrix)
                || !(helper.getLevel().getBlockEntity(nodePos) instanceof com.mohistmc.academy.energy.api.block.IWirelessNode node)) {
            helper.fail("Matrix/node block entities were not created"); return;
        }
        // A final 1.12.2 matrix has no operational capacity/range until its matrix
        // core and all three constraint plates are installed.  Exercise the
        // real initialized topology instead of bypassing that material gate
        // with only the persisted boolean flag.
        matrix.getItems().set(MatrixBlockEntity.CORE_SLOT, new ItemStack(AcademyItems.MAT_CORE_0.get()));
        for (int slot = MatrixBlockEntity.PLATE_SLOT_0; slot <= MatrixBlockEntity.PLATE_SLOT_2; slot++) {
            matrix.getItems().set(slot, new ItemStack(AcademyItems.CONSTRAINT_PLATE.get()));
        }
        matrix.setInitialized(true); matrix.setSSID("gametest"); matrix.setPassword("secret");
        if (!com.mohistmc.academy.energy.impl.WirelessSystem.createNetwork(helper.getLevel(), matrix, "gametest", "secret")
                || !com.mohistmc.academy.energy.impl.WirelessSystem.linkNode(helper.getLevel(), matrix, node, "secret")
                || com.mohistmc.academy.energy.impl.WirelessSystem.getNetwork(helper.getLevel(), node) == null) {
            helper.fail("Matrix could not link a loaded in-range node"); return;
        }
        if (!com.mohistmc.academy.energy.impl.WirelessSystem.unlinkNode(helper.getLevel(), matrix, node)) {
            helper.fail("Matrix could not request node unlink"); return;
        }
        com.mohistmc.academy.energy.impl.WiWorldData.get(helper.getLevel()).tick();
        if (com.mohistmc.academy.energy.impl.WirelessSystem.getNetwork(helper.getLevel(), node) != null) {
            helper.fail("Unlinked node remained in matrix network"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void standaloneNodeLinksGeneratorAndReceiverWithoutMatrix(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos nodePos = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos generatorPos = nodePos.east(2);
        BlockPos receiverPos = nodePos.west(2);
        level.setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(generatorPos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        level.setBlock(receiverPos, AcademyBlocks.METAL_FORMER.get().defaultBlockState(), 3);

        var node = (com.mohistmc.academy.energy.api.block.IWirelessNode) level.getBlockEntity(nodePos);
        var generator = (com.mohistmc.academy.energy.api.block.IWirelessGenerator) level.getBlockEntity(generatorPos);
        var receiver = (com.mohistmc.academy.energy.api.block.IWirelessReceiver) level.getBlockEntity(receiverPos);
        if (node == null || generator == null || receiver == null) {
            helper.fail("standalone topology block entities were not created"); return;
        }
        if (WirelessSystem.getNetwork(level, node) != null) {
            helper.fail("fresh standalone node unexpectedly belongs to a matrix network"); return;
        }
        if (!WirelessSystem.linkGenerator(level, node, generator, false, "")
                || !WirelessSystem.linkReceiver(level, node, receiver, false, "")) {
            helper.fail("standalone node rejected generator or receiver without a matrix"); return;
        }
        var conn = WirelessSystem.getNodeConnection(level, node);
        if (conn == null || conn.getLoad() != 2
                || WiWorldData.get(level).getNodeConnection(generator) != conn
                || WiWorldData.get(level).getNodeConnection(receiver) != conn) {
            helper.fail("standalone NodeConn did not retain both machine links"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void phaseGeneratorRebindSurvivesOldNodeCleanup(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos firstNodePos = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos secondNodePos = helper.absolutePos(new BlockPos(7, 2, 3));
        BlockPos generatorPos = helper.absolutePos(new BlockPos(5, 2, 3));
        level.setBlock(firstNodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(secondNodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(generatorPos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);

        var firstNode = (com.mohistmc.academy.energy.api.block.IWirelessNode) level.getBlockEntity(firstNodePos);
        var secondNode = (com.mohistmc.academy.energy.api.block.IWirelessNode) level.getBlockEntity(secondNodePos);
        var generator = (com.mohistmc.academy.energy.api.block.IWirelessGenerator) level.getBlockEntity(generatorPos);
        if (firstNode == null || secondNode == null || generator == null
                || !WirelessSystem.linkGenerator(level, firstNode, generator, false, "")) {
            helper.fail("Phase Generator could not establish its first standalone node link"); return;
        }
        var oldConnection = WiWorldData.get(level).getNodeConnection(generator);
        if (!WirelessSystem.linkGenerator(level, secondNode, generator, false, "")) {
            helper.fail("Phase Generator could not rebind to the second standalone node"); return;
        }
        var newConnection = WirelessSystem.getNodeConnection(level, secondNode);
        if (newConnection == null || newConnection == oldConnection
                || WiWorldData.get(level).getNodeConnection(generator) != newConnection) {
            helper.fail("Phase Generator did not immediately point at its new node"); return;
        }

        // Process the old connection's queued removal.  Its cleanup must not
        // delete the newer mapping for the same generator.
        WiWorldData.get(level).tick();
        if (WiWorldData.get(level).getNodeConnection(generator) != newConnection
                || newConnection.getLoad() != 1) {
            helper.fail("Old node cleanup removed the Phase Generator's new link"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void phaseGeneratorImmediateReconnectCancelsQueuedUnlink(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos nodePos = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos generatorPos = helper.absolutePos(new BlockPos(5, 2, 3));
        level.setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(generatorPos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        var node = (NodeBasicBlockEntity) level.getBlockEntity(nodePos);
        var generator = (PhaseGenBlockEntity) level.getBlockEntity(generatorPos);
        if (!WirelessSystem.linkGenerator(level, node, generator, false, "")) {
            helper.fail("Initial Phase Generator link failed"); return;
        }
        if (!WirelessSystem.unlinkUser(level, generator)
                || !WirelessSystem.linkGenerator(level, node, generator, false, "")) {
            helper.fail("Immediate reconnect was rejected"); return;
        }
        WiWorldData.get(level).tick();
        var connection = WiWorldData.get(level).getNodeConnection(generator);
        if (connection == null || connection.getNode() != node || connection.getLoad() != 1) {
            helper.fail("Queued unlink removed an immediate same-node reconnect"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void nodeClientUpdateDoesNotExposePassword(GameTestHelper helper) {
        BlockPos nodePos = helper.absolutePos(new BlockPos(4, 2, 4));
        helper.getLevel().setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        if (!(helper.getLevel().getBlockEntity(nodePos) instanceof NodeBasicBlockEntity node)) {
            helper.fail("Node block entity was not created"); return;
        }
        node.setNodeName("public-name");
        node.setPassword("server-secret");
        CompoundTag update = node.getUpdateTag(helper.getLevel().registryAccess());
        if (update.contains("node_pass") || !"public-name".equals(update.getString("node_name"))) {
            helper.fail("Node client update exposed its password or lost public state"); return;
        }
        CompoundTag persisted = node.saveWithoutMetadata(helper.getLevel().registryAccess());
        if (!"server-secret".equals(persisted.getString("node_pass"))) {
            helper.fail("Separating the client update accidentally broke password persistence"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void destroyingAdjacentMatrixDoesNotCascade(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos first = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos second = first.east(2);
        var firstState = AcademyBlocks.MATRIX.get().defaultBlockState();
        var secondState = AcademyBlocks.MATRIX.get().defaultBlockState();
        var matrix = (com.mohistmc.academy.world.block.Matrix) AcademyBlocks.MATRIX.get();
        level.setBlock(first, firstState, 3);
        matrix.createStructure(level, first, firstState);
        level.setBlock(second, secondState, 3);
        matrix.createStructure(level, second, secondState);

        level.destroyBlock(first, false);

        if (!(level.getBlockState(second).getBlock() instanceof com.mohistmc.academy.world.block.Matrix)) {
            helper.fail("destroying one matrix cascaded into its adjacent matrix main"); return;
        }
        for (BlockPos part : com.mohistmc.academy.world.block.Matrix.structurePositions(second, secondState)) {
            if (!(level.getBlockState(part).getBlock() instanceof com.mohistmc.academy.world.block.MatrixSubBlock)) {
                helper.fail("destroying one matrix cascaded into an adjacent matrix part at " + part); return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void matrixNodeBulkActionRejectsFloodButAllowsFirstAndCooldown(GameTestHelper helper) {
        UUID player = UUID.randomUUID();
        BlockPos matrix = helper.absolutePos(new BlockPos(1, 1, 1));
        if (!MatrixNodesPacket.claimRequest(player, matrix, 100)
                || MatrixNodesPacket.claimRequest(player, matrix, 100)
                || MatrixNodesPacket.claimRequest(player, matrix, 109)
                || !MatrixNodesPacket.claimRequest(player, matrix, 110)) {
            helper.fail("Matrix bulk-action cooldown contract failed"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void basicOresAndMachineHaveRealDestroyDrops(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        var cases = List.of(
                new Object[]{AcademyBlocks.CRYSTAL_ORE.get(), AcademyItems.CRYSTAL_LOW.get()},
                new Object[]{AcademyBlocks.RESO_ORE.get(), AcademyItems.RESO_CRYSTAL.get()},
                new Object[]{AcademyBlocks.IMAGSIL_ORE.get(), AcademyItems.IMAGSIL_ORE.get()},
                new Object[]{AcademyBlocks.CONSTRAIN_METAL.get(), AcademyItems.CONSTRAIN_METAL.get()},
                new Object[]{AcademyBlocks.METAL_FORMER.get(), AcademyItems.METAL_FORMER.get()});
        int index = 0;
        for (Object[] entry : cases) {
            BlockPos pos = helper.absolutePos(new BlockPos(1 + index % 3, 1, 1 + index++ / 3));
            var block = (net.minecraft.world.level.block.Block) entry[0];
            var expected = (net.minecraft.world.item.Item) entry[1];
            helper.getLevel().setBlock(pos, block.defaultBlockState(), 3);
            if (!helper.getLevel().destroyBlock(pos, true, player)) {
                helper.fail("Could not destroy " + block); return;
            }
            boolean found = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    new AABB(pos).inflate(1.5), e -> e.getItem().is(expected)).size() > 0;
            if (!found) { helper.fail("No expected real loot entity for " + block); return; }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void breakingInternalMultiblockPartDropsExactlyOneStatefulMain(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        BlockPos matrixPos = helper.absolutePos(new BlockPos(3, 1, 3));
        player.setPos(matrixPos.getX() + 0.5, matrixPos.getY(), matrixPos.getZ() + 0.5);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        var matrixState = AcademyBlocks.MATRIX.get().defaultBlockState();
        level.setBlock(matrixPos, matrixState, 3);
        ((com.mohistmc.academy.world.block.Matrix) AcademyBlocks.MATRIX.get())
                .createStructure(level, matrixPos, matrixState);
        MatrixBlockEntity matrix = (MatrixBlockEntity) level.getBlockEntity(matrixPos);
        matrix.getItems().set(MatrixBlockEntity.CORE_SLOT, new ItemStack(AcademyItems.MAT_CORE_1.get()));
        // Break an upper proxy specifically; its main is one block below and
        // exercises the legacy 2x2x2 forwarding path.
        BlockPos matrixPart = com.mohistmc.academy.world.block.Matrix
                .structurePositions(matrixPos, matrixState).getLast();
        boolean destroyed = player.gameMode.destroyBlock(matrixPart);
        List<ItemEntity> matrixDrops = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(matrixPos).inflate(3), e -> e.getItem().is(AcademyItems.MATRIX.get()));
        long internalDrops = level.getEntitiesOfClass(ItemEntity.class, new AABB(matrixPos).inflate(3),
                e -> e.getItem().is(AcademyBlocks.MATRIX_SUB.get().asItem())).size();
        int pickedUpMatrix = player.getInventory().countItem(AcademyItems.MATRIX.get());
        int matrixMaterialized = matrixDrops.size() + pickedUpMatrix;
        // The embedded mock can report creative=true while instabuild=false. That state cannot
        // occur for a real player: ServerLevel may suppress the requested loot or emit it depending
        // on which flag its current path observes. In that synthetic state this test checks cleanup
        // and non-duplication; real survival loot is covered by the direct loot-table destruction test.
        boolean matrixDropInvalid = player.isCreative() ? matrixMaterialized > 1 : matrixMaterialized != 1;
        if (matrixDropInvalid || internalDrops != 0 || !level.getBlockState(matrixPos).isAir()) {
            helper.fail("Matrix part break result drops=" + matrixDrops.size()
                    + " destroyed=" + destroyed + " creative=" + player.isCreative()
                    + " pickedUp=" + pickedUpMatrix + " mainAir=" + level.getBlockState(matrixPos).isAir()
                    + " subDrops=" + internalDrops); return;
        }

        BlockPos devPos = helper.absolutePos(new BlockPos(8, 1, 8));
        var devState = AcademyBlocks.DEV_NORMAL.get().defaultBlockState();
        level.setBlock(devPos, devState, 3);
        var dev = (com.mohistmc.academy.world.block.DevMachineBase) AcademyBlocks.DEV_NORMAL.get();
        var devTargets = dev.getRotatedSubBlocks(devState.getValue(com.mohistmc.academy.world.block.DevMachineBase.FACING).getOpposite()).stream()
                .map(s -> devPos.offset(s.dx(), s.dy(), s.dz())).toList();
        for (BlockPos target : devTargets) level.setBlock(target, dev.getStructureSubBlock().defaultBlockState(), 19);
        dev.initializeStructure(level, devPos, devTargets);
        var offset = dev.getRotatedSubBlocks(devState.getValue(com.mohistmc.academy.world.block.DevMachineBase.FACING).getOpposite()).getFirst();
        BlockPos devPart = devPos.offset(offset.dx(), offset.dy(), offset.dz());
        player.gameMode.destroyBlock(devPart);
        long devDrops = level.getEntitiesOfClass(ItemEntity.class, new AABB(devPos).inflate(5),
                e -> e.getItem().is(AcademyItems.DEV_NORMAL.get())).size();
        int pickedUpDev = player.getInventory().countItem(AcademyItems.DEV_NORMAL.get());
        long devMaterialized = devDrops + pickedUpDev;
        boolean devDropInvalid = player.isCreative() ? devMaterialized > 1 : devMaterialized != 1;
        if (devDropInvalid || !level.getBlockState(devPos).isAir()) {
            helper.fail("Developer part break did not yield exactly one main item"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void multiblockPlacementIsAtomicAndProtectionChecked(GameTestHelper helper) {
        var level=helper.getLevel(); ServerPlayer player=helper.makeMockServerPlayerInLevel();
        player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos windMain=helper.absolutePos(new BlockPos(2,1,2));
        level.setBlock(windMain.below(),Blocks.STONE.defaultBlockState(),3);
        level.setBlock(windMain.above(),Blocks.CHEST.defaultBlockState(),3);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(AcademyItems.WINDGEN_BASE.get()));
        var windHit=new BlockHitResult(Vec3.atCenterOf(windMain.below()),net.minecraft.core.Direction.UP,windMain.below(),false);
        AcademyItems.WINDGEN_BASE.get().useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,windHit));
        if(!level.getBlockState(windMain.above()).is(Blocks.CHEST)||!level.getBlockState(windMain).isAir()||player.getMainHandItem().getCount()!=1){helper.fail("Wind base overwrote obstruction or consumed item");return;}

        BlockPos devMain=helper.absolutePos(new BlockPos(5,1,5));
        level.setBlock(devMain.below(),Blocks.STONE.defaultBlockState(),3);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(AcademyItems.DEV_NORMAL.get()));
        var dev=(com.mohistmc.academy.world.block.DevMachineBase)AcademyBlocks.DEV_NORMAL.get();
        var predicted=dev.defaultBlockState();
        var targets=dev.getRotatedSubBlocks(predicted.getValue(com.mohistmc.academy.world.block.DevMachineBase.FACING).getOpposite()).stream().map(s->devMain.offset(s.dx(),s.dy(),s.dz())).toList();
        int[] seen={0}; Consumer<BlockEvent.EntityPlaceEvent> denyThird=e->{if(e.getPlacedBlock().is(AcademyBlocks.DEV_NORMAL_SUB.get())&&++seen[0]==3)e.setCanceled(true);};
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class,denyThird);
        try { var hit=new BlockHitResult(Vec3.atCenterOf(devMain.below()),net.minecraft.core.Direction.UP,devMain.below(),false);AcademyItems.DEV_NORMAL.get().useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit)); }
        finally { NeoForge.EVENT_BUS.unregister(denyThird); }
        java.util.List<BlockPos> remainingSubs = new java.util.ArrayList<>();
        for(BlockPos p:BlockPos.betweenClosed(devMain.offset(-3,0,-3),devMain.offset(3,3,3))) {
            if(level.getBlockState(p).is(AcademyBlocks.DEV_NORMAL_SUB.get())) remainingSubs.add(p.immutable());
        }
        BlockState remainingMain = level.getBlockState(devMain);
        int heldCount = player.getMainHandItem().getCount();
        if(!remainingMain.isAir()||!remainingSubs.isEmpty()||heldCount!=1){
            helper.fail("Dev partial cancellation did not roll back whole transaction events="+seen[0]
                    +" main="+remainingMain+" subs="+remainingSubs+" held="+player.getMainHandItem());return;}

        // World-edit/admin placement can leave a genuinely incomplete
        // footprint.  initializeStructure must validate every participant
        // before publishing any UUID, so the already-present proxies cannot
        // become half-linked when a later proxy is absent.
        level.setBlock(devMain,predicted,3);
        for(int i=0;i<targets.size();i++) {
            if(i!=2) level.setBlock(targets.get(i),dev.getStructureSubBlock().defaultBlockState(),19);
        }
        boolean partialCommitted=dev.initializeStructure(level,devMain,targets);
        var partialMain=level.getBlockEntity(devMain);
        boolean publishedMain=partialMain instanceof com.mohistmc.academy.world.block.IDevStructure structure
                && structure.getStructureId()!=null;
        boolean publishedSub=false;
        for(BlockPos target:targets) {
            var partialSub=level.getBlockEntity(target);
            if(partialSub instanceof com.mohistmc.academy.world.block.IDevSubStructure structure
                    && structure.getStructureId()!=null) { publishedSub=true; break; }
        }
        if(partialCommitted||publishedMain||publishedSub){
            helper.fail("Incomplete developer footprint published structure identity committed="+partialCommitted
                    +" mainId="+publishedMain+" subId="+publishedSub);return;}
        for(BlockPos target:targets) level.setBlock(target,Blocks.AIR.defaultBlockState(),3);
        level.setBlock(devMain,Blocks.AIR.defaultBlockState(),3);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void automaticWindFanHonorsProtectionAndBacksOff(GameTestHelper helper) {
        BlockPos main=helper.absolutePos(new BlockPos(2,1,2)),fan=helper.absolutePos(new BlockPos(3,1,2));
        helper.getLevel().setBlock(main,AcademyBlocks.WINDGEN_MAIN.get().defaultBlockState(),3);
        var be=(com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity)helper.getLevel().getBlockEntity(main);
        be.setOwnerUUID(java.util.UUID.randomUUID());
        int[] events={0}; Consumer<BlockEvent.EntityPlaceEvent> deny=e->{if(e.getPos().equals(fan)){events[0]++;e.setCanceled(true);}};
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class,deny);
        try { be.tryPlaceFan(helper.getLevel(),fan,net.minecraft.core.Direction.EAST);be.tryPlaceFan(helper.getLevel(),fan,net.minecraft.core.Direction.EAST); }
        finally { NeoForge.EVENT_BUS.unregister(deny); }
        if(!helper.getLevel().getBlockState(fan).isAir()||events[0]!=1){helper.fail("Fan cancellation changed world or flooded events: "+events[0]);return;}
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void matrixThirdSubCancellationRollsBackAndPreservesListenerComponents(GameTestHelper helper) {
        var level=helper.getLevel(); ServerPlayer player=helper.makeMockServerPlayerInLevel();
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos main=helper.absolutePos(new BlockPos(3,1,3));
        level.setBlock(main.below(),Blocks.STONE.defaultBlockState(),3);
        ItemStack held=new ItemStack(AcademyItems.MATRIX.get(),2);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,held);
        int[] seen={0}; Consumer<BlockEvent.EntityPlaceEvent> listener=e->{
            if(e.getPlacedBlock().is(AcademyBlocks.MATRIX_SUB.get())) {
                if(++seen[0]==1) player.getMainHandItem().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("listener-kept"));
                if(seen[0]==3)e.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class,listener);
        try { var hit=new BlockHitResult(Vec3.atCenterOf(main.below()),net.minecraft.core.Direction.UP,main.below(),false);AcademyItems.MATRIX.get().useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit)); }
        finally { NeoForge.EVENT_BUS.unregister(listener); }
        boolean anySub=false;for(BlockPos p:BlockPos.betweenClosed(main.offset(-2,0,-2),main.offset(2,1,2)))if(level.getBlockState(p).is(AcademyBlocks.MATRIX_SUB.get())){anySub=true;break;}
        net.minecraft.network.chat.Component name=player.getMainHandItem().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if(seen[0]!=3||!level.getBlockState(main).isAir()||anySub||player.getMainHandItem().getCount()!=2||name==null||!name.getString().equals("listener-kept")){
            helper.fail("Matrix rollback lost state events="+seen[0]+" mainAir="+level.getBlockState(main).isAir()+" sub="+anySub+" count="+player.getMainHandItem().getCount()+" name="+(name==null?"null":name.getString()));return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void matrixCommittedValidationFailureRollsBackListenerMutation(GameTestHelper helper) {
        var level=helper.getLevel();ServerPlayer player=helper.makeMockServerPlayerInLevel();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        BlockPos main=helper.absolutePos(new BlockPos(3,1,3));level.setBlock(main.below(),Blocks.STONE.defaultBlockState(),3);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(AcademyItems.MATRIX.get(),2));
        int[] seen={0};List<BlockPos> touched=new ArrayList<>();Consumer<BlockEvent.EntityPlaceEvent> mutateThird=e->{if(e.getPlacedBlock().is(AcademyBlocks.MATRIX_SUB.get())){touched.add(e.getPos().immutable());if(++seen[0]==3)level.setBlock(e.getPos(),Blocks.STONE.defaultBlockState(),3);}};
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class,mutateThird);
        try{var hit=new BlockHitResult(Vec3.atCenterOf(main.below()),net.minecraft.core.Direction.UP,main.below(),false);AcademyItems.MATRIX.get().useOn(new net.minecraft.world.item.context.UseOnContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,hit));}
        finally{NeoForge.EVENT_BUS.unregister(mutateThird);}
        boolean residue=touched.stream().anyMatch(p->!level.getBlockState(p).isAir());
        if(seen[0]!=3||!level.getBlockState(main).isAir()||residue||player.getMainHandItem().getCount()!=2){helper.fail("Matrix committed failure state events="+seen[0]+" mainAir="+level.getBlockState(main).isAir()+" residue="+residue+" count="+player.getMainHandItem().getCount());return;}
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void matterUnitProtectionCancellationPreservesWorldAndInventory(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos source = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos target = helper.absolutePos(new BlockPos(2, 1, 1));
        helper.getLevel().setBlock(source, AcademyBlocks.PHASE_LIQUID.get().defaultBlockState(), 3);
        helper.getLevel().setBlock(target, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        ItemStack empty = new ItemStack(AcademyItems.MATTER_UNIT_NONE.get());
        ItemStack full = new ItemStack(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get());
        Consumer<BlockEvent.BreakEvent> denyBreak = event -> {
            if (event.getPos().equals(source)) event.setCanceled(true);
        };
        Consumer<BlockEvent.EntityPlaceEvent> denyPlace = event -> {
            if (event.getPos().equals(target)) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class, denyBreak);
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, denyPlace);
        try {
            if (MatterUnitPermissions.mayDrain(helper.getLevel(), player, source)
                    || MatterUnitPermissions.tryPlace(helper.getLevel(), player, target, net.minecraft.core.Direction.UP)
                    || !helper.getLevel().getBlockState(source).is(AcademyBlocks.PHASE_LIQUID.get())
                    || !helper.getLevel().getBlockState(target).is(Blocks.GRASS_BLOCK)
                    || empty.getCount() != 1 || full.getCount() != 1) {
                helper.fail("Cancelled matter-unit transaction changed world or inventory"); return;
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(denyBreak);
            NeoForge.EVENT_BUS.unregister(denyPlace);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void windGeneratorFanUsesPersistentOwnerAndHonorsPlaceCancellation(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos mainPos = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos fanPos = mainPos.east();
        level.setBlock(mainPos, AcademyBlocks.WINDGEN_MAIN.get().defaultBlockState(), 3);
        var wind = (com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity) level.getBlockEntity(mainPos);
        UUID owner = UUID.randomUUID();
        wind.setOwnerUUID(owner);
        final boolean[] sawOwnerActor = {false};
        Consumer<BlockEvent.EntityPlaceEvent> deny = event -> {
            if (event.getPos().equals(fanPos)) {
                sawOwnerActor[0] = event.getEntity() != null && owner.equals(event.getEntity().getUUID());
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, deny);
        long start = level.getGameTime();
        try {
            if (wind.tryPlaceFan(level, fanPos, net.minecraft.core.Direction.EAST)
                    || !level.getBlockState(fanPos).isAir() || !sawOwnerActor[0]) {
                helper.fail("cancelled wind fan placement bypassed owner-backed place event"); return;
            }
        } finally {
            NeoForge.EVENT_BUS.unregister(deny);
        }
        if (wind.tryPlaceFan(level, fanPos, net.minecraft.core.Direction.EAST)) {
            helper.fail("wind fan ignored 40 tick cancellation backoff"); return;
        }
        CompoundTag tag = wind.saveWithoutMetadata(level.registryAccess());
        var restored = new com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity(mainPos,
                AcademyBlocks.WINDGEN_MAIN.get().defaultBlockState());
        restored.loadWithComponents(tag, level.registryAccess());
        if (!owner.equals(restored.getOwnerUUID())) {
            helper.fail("wind generator owner did not survive NBT round-trip"); return;
        }
        helper.runAfterDelay(40, () -> {
            if (!wind.tryPlaceFan(level, fanPos, net.minecraft.core.Direction.EAST)
                    || !level.getBlockState(fanPos).is(AcademyBlocks.WINDGEN_FAN.get())) {
                helper.fail("owner-authorized wind fan did not place after backoff"); return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void windGeneratorRuntimeStateIsPerPlacedMachine(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos basePos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos validPos = basePos.above(10);
        BlockPos invalidPos = helper.absolutePos(new BlockPos(8, 3, 8));
        net.minecraft.world.level.block.state.BlockState mainState = AcademyBlocks.WINDGEN_MAIN.get().defaultBlockState()
                .setValue(com.mohistmc.academy.world.block.WindGenMain.FACING, net.minecraft.core.Direction.EAST);
        clearLegacyWindRotorPlane(level, validPos, net.minecraft.core.Direction.EAST);
        level.setBlock(basePos, AcademyBlocks.WINDGEN_BASE.get().defaultBlockState(), 3);
        level.setBlock(basePos.above(), AcademyBlocks.WIND_GEN_BASE_SUB.get().defaultBlockState(), 3);
        for (int i = 0; i < com.mohistmc.academy.world.block.WindGenBase.MIN_PILLARS; i++) {
            level.setBlock(basePos.above(2 + i), AcademyBlocks.WINDGEN_PILLAR.get().defaultBlockState(), 3);
        }
        level.setBlock(validPos, mainState, 3);
        level.setBlock(invalidPos, mainState, 3);
        for (BlockPos proxy : com.mohistmc.academy.world.block.WindGenMain.proxyPositions(validPos, mainState)) {
            level.setBlock(proxy, AcademyBlocks.WINDGEN_FAN.get().defaultBlockState()
                    .setValue(com.mohistmc.academy.world.block.WindGenFan.FACING,
                            mainState.getValue(com.mohistmc.academy.world.block.WindGenMain.FACING)), 3);
        }
        for (BlockPos proxy : com.mohistmc.academy.world.block.WindGenMain.proxyPositions(invalidPos, mainState)) {
            level.setBlock(proxy, AcademyBlocks.WINDGEN_FAN.get().defaultBlockState()
                    .setValue(com.mohistmc.academy.world.block.WindGenFan.FACING,
                            mainState.getValue(com.mohistmc.academy.world.block.WindGenMain.FACING)), 3);
        }
        var valid = (com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity) level.getBlockEntity(validPos);
        var invalid = (com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity) level.getBlockEntity(invalidPos);
        valid.getItems().set(0, new ItemStack(AcademyItems.WINDGEN_FAN.get()));
        var mainBlock = (com.mohistmc.academy.world.block.WindGenMain) AcademyBlocks.WINDGEN_MAIN.get();

        valid.tick(mainBlock, level, validPos, net.minecraft.core.Direction.EAST);
        invalid.tick(mainBlock, level, invalidPos, net.minecraft.core.Direction.EAST);
        if (!valid.hasValidFan() || invalid.hasValidFan()) {
            helper.fail("wind runtime state leaked from invalid machine into valid machine"); return;
        }
        invalid.tick(mainBlock, level, invalidPos, net.minecraft.core.Direction.EAST);
        valid.tick(mainBlock, level, validPos, net.minecraft.core.Direction.EAST);
        if (!valid.hasValidFan() || invalid.hasValidFan()) {
            helper.fail("wind runtime state depends on interleaved tick order"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void legacyHeightWindGeneratorActuallyProducesEnergy(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos basePos = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos mainPos = basePos.above(10);
        level.setBlock(basePos, AcademyBlocks.WINDGEN_BASE.get().defaultBlockState(), 3);
        level.setBlock(basePos.above(), AcademyBlocks.WIND_GEN_BASE_SUB.get().defaultBlockState(), 3);
        for (int i = 0; i < com.mohistmc.academy.world.block.WindGenBase.MIN_PILLARS; i++) {
            level.setBlock(basePos.above(2 + i), AcademyBlocks.WINDGEN_PILLAR.get().defaultBlockState(), 3);
        }
        var mainState = AcademyBlocks.WINDGEN_MAIN.get().defaultBlockState()
                .setValue(com.mohistmc.academy.world.block.WindGenMain.FACING, net.minecraft.core.Direction.EAST);
        clearLegacyWindRotorPlane(level, mainPos, net.minecraft.core.Direction.EAST);
        level.setBlock(mainPos, mainState, 3);
        for (BlockPos proxy : com.mohistmc.academy.world.block.WindGenMain.proxyPositions(mainPos, mainState)) {
            level.setBlock(proxy, AcademyBlocks.WINDGEN_FAN.get().defaultBlockState()
                    .setValue(com.mohistmc.academy.world.block.WindGenFan.FACING, net.minecraft.core.Direction.EAST), 3);
        }
        var main = (com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity) level.getBlockEntity(mainPos);
        var base = (com.mohistmc.academy.world.block.entity.WindGenBaseBlockEntity) level.getBlockEntity(basePos);
        main.getItems().set(0, new ItemStack(AcademyItems.WINDGEN_FAN.get()));
        main.setChanged();
        helper.runAfterDelay(5, () -> {
            if (!main.hasValidFan() || base.getEnergyStored() <= 0
                    || !level.getBlockState(basePos).getValue(com.mohistmc.academy.world.block.WindGenBase.ENABLE)) {
                helper.fail("Legacy 8-pillar turbine with one fan did not generate or enable"); return;
            }
            if (main.getItems().get(0).getCount() != 1) {
                helper.fail("Wind generator fan slot retained more than one fan"); return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void removingWindHeadProxyStopsGenerationAndDropsOneMain(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos basePos = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos mainPos = basePos.above(10);
        level.setBlock(basePos, AcademyBlocks.WINDGEN_BASE.get().defaultBlockState(), 3);
        level.setBlock(basePos.above(), AcademyBlocks.WIND_GEN_BASE_SUB.get().defaultBlockState(), 3);
        for (int i = 0; i < com.mohistmc.academy.world.block.WindGenBase.MIN_PILLARS; i++) {
            level.setBlock(basePos.above(2 + i), AcademyBlocks.WINDGEN_PILLAR.get().defaultBlockState(), 3);
        }
        var mainState = AcademyBlocks.WINDGEN_MAIN.get().defaultBlockState()
                .setValue(com.mohistmc.academy.world.block.WindGenMain.FACING, net.minecraft.core.Direction.EAST);
        clearLegacyWindRotorPlane(level, mainPos, net.minecraft.core.Direction.EAST);
        level.setBlock(mainPos, mainState, 3);
        var proxies = com.mohistmc.academy.world.block.WindGenMain.proxyPositions(mainPos, mainState);
        for (BlockPos proxy : proxies) level.setBlock(proxy, AcademyBlocks.WINDGEN_FAN.get().defaultBlockState()
                .setValue(com.mohistmc.academy.world.block.WindGenFan.FACING, net.minecraft.core.Direction.EAST), 3);
        var main = (com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity) level.getBlockEntity(mainPos);
        var base = (com.mohistmc.academy.world.block.entity.WindGenBaseBlockEntity) level.getBlockEntity(basePos);
        main.getItems().set(0, new ItemStack(AcademyItems.WINDGEN_FAN.get()));
        helper.runAfterDelay(4, () -> {
            int energyBeforeBreak = base.getEnergyStored();
            if (energyBeforeBreak <= 0 || !player.gameMode.destroyBlock(proxies.getFirst())) {
                helper.fail("formed wind head did not generate or proxy could not be broken"); return;
            }
            long dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(mainPos).inflate(4),
                    e -> e.getItem().is(AcademyItems.WINDGEN_MAIN.get())).size();
            int pickedUp = player.getInventory().countItem(AcademyItems.WINDGEN_MAIN.get());
            if (dropped + pickedUp != 1 || !level.getBlockState(mainPos).isAir()
                    || proxies.stream().anyMatch(p -> !level.getBlockState(p).isAir())) {
                helper.fail("wind proxy teardown did not yield exactly one main and clear all head parts"); return;
            }
            helper.runAfterDelay(3, () -> {
                if (base.getEnergyStored() != energyBeforeBreak) {
                    helper.fail("wind base kept generating after a head proxy was removed"); return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 45)
    public static void windObstacleStopsGenerationWithinTenTicksAndResumesWhenCleared(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos basePos = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos mainPos = basePos.above(10);
        // The isolated shared fixture is 16 blocks tall, but the shortest
        // legal legacy turbine already needs ten blocks above its base and a
        // further six blocks of vertical rotor clearance.  Establish the
        // complete 15x15 plane explicitly: otherwise the fixture boundary,
        // rather than a player-placed obstacle, hides the fan before this
        // test starts.  Official 1.12.2 checks offsets -7..7 around the
        // forward fan plane and stops both rendering and generation when any
        // non-air block occupies that plane.
        clearLegacyWindRotorPlane(level, mainPos, net.minecraft.core.Direction.EAST);
        level.setBlock(basePos, AcademyBlocks.WINDGEN_BASE.get().defaultBlockState(), 3);
        level.setBlock(basePos.above(), AcademyBlocks.WIND_GEN_BASE_SUB.get().defaultBlockState(), 3);
        for (int i = 0; i < com.mohistmc.academy.world.block.WindGenBase.MIN_PILLARS; i++)
            level.setBlock(basePos.above(2 + i), AcademyBlocks.WINDGEN_PILLAR.get().defaultBlockState(), 3);
        var mainState = AcademyBlocks.WINDGEN_MAIN.get().defaultBlockState()
                .setValue(com.mohistmc.academy.world.block.WindGenMain.FACING, net.minecraft.core.Direction.EAST);
        level.setBlock(mainPos, mainState, 3);
        for (BlockPos proxy : com.mohistmc.academy.world.block.WindGenMain.proxyPositions(mainPos, mainState))
            level.setBlock(proxy, AcademyBlocks.WINDGEN_FAN.get().defaultBlockState()
                    .setValue(com.mohistmc.academy.world.block.WindGenFan.FACING, net.minecraft.core.Direction.EAST), 3);
        var main = (com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity) level.getBlockEntity(mainPos);
        var base = (com.mohistmc.academy.world.block.entity.WindGenBaseBlockEntity) level.getBlockEntity(basePos);
        main.getItems().set(0, new ItemStack(AcademyItems.WINDGEN_FAN.get()));
        helper.runAfterDelay(2, () -> {
            if (!main.isVisualFanVisible()) { helper.fail("clear formed turbine did not show its fan"); return; }
            int beforeObstacle = base.getEnergyStored();
            level.setBlock(mainPos.east().above(), Blocks.STONE.defaultBlockState(), 3);
            helper.runAfterDelay(12, () -> {
                if (main.isVisualFanVisible() || main.isWorking() || base.isWorking()) {
                    helper.fail("1.12.2 rotor obstacle did not stop generation and hide the fan"); return;
                }
                int stoppedEnergy = base.getEnergyStored();
                if (stoppedEnergy < beforeObstacle) {
                    helper.fail("wind energy moved backwards while stopping"); return;
                }
                helper.runAfterDelay(3, () -> {
                    if (base.getEnergyStored() != stoppedEnergy) {
                        helper.fail("obstructed turbine continued generating after its refresh interval"); return;
                    }
                    level.destroyBlock(mainPos.east().above(), false);
                    helper.runAfterDelay(12, () -> {
                        if (!main.isVisualFanVisible() || !main.isWorking() || !base.isWorking()
                                || base.getEnergyStored() <= stoppedEnergy) {
                            helper.fail("cleared 1.12.2 rotor plane did not resume generation and rendering"); return;
                        }
                        helper.succeed();
                    });
                });
            });
        });
    }

    private static void clearLegacyWindRotorPlane(net.minecraft.server.level.ServerLevel level,
                                                   BlockPos mainPos,
                                                   net.minecraft.core.Direction facing) {
        BlockPos center = com.mohistmc.academy.world.block.entity.WindGenMainBlockEntity
                .fanPosition(mainPos, facing);
        for (int vertical = -7; vertical <= 7; vertical++) {
            for (int lateral = -7; lateral <= 7; lateral++) {
                if (vertical == 0 && lateral == 0) continue;
                BlockPos target = facing.getAxis() == net.minecraft.core.Direction.Axis.X
                        ? center.offset(0, vertical, lateral)
                        : center.offset(lateral, vertical, 0);
                level.setBlock(target, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    @GameTest(template = EMPTY)
    public static void windBaseChargesAtMostLegacyBandwidthPerTick(GameTestHelper helper) {
        BlockPos basePos = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlock(basePos, AcademyBlocks.WINDGEN_BASE.get().defaultBlockState(), 3);
        var base = (com.mohistmc.academy.world.block.entity.WindGenBaseBlockEntity) helper.getLevel().getBlockEntity(basePos);
        ItemStack unit = new ItemStack(AcademyItems.ENERGY_UNIT.get());
        unit.setDamageValue(com.mohistmc.academy.world.item.EnergyUnit.MAX_ENERGY);
        base.getItems().set(0, unit);
        base.setEnergy(1000);
        base.tick(false, false, false, basePos.getY());
        if (com.mohistmc.academy.capability.EnergyItemHelper.getEnergy(unit) != 300
                || base.getEnergyStored() != 700) {
            helper.fail("wind base exceeded 1.0.7 300 IF/t item-charge bandwidth"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void everyNodeTierTransfersEnergyItemsAtItsLegacyBandwidth(GameTestHelper helper) {
        var level = helper.getLevel();
        var blocks = List.of(AcademyBlocks.NODE_BASIC.get(), AcademyBlocks.NODE_STANDARD.get(),
                AcademyBlocks.NODE_ADVANCED.get());
        int[] bandwidths = {150, 300, 900};
        int[] capacities = {15_000, 50_000, 200_000};
        for (int tier = 0; tier < blocks.size(); tier++) {
            BlockPos pos = helper.absolutePos(new BlockPos(2 + tier * 3, 1, 2));
            level.setBlock(pos, blocks.get(tier).defaultBlockState(), 3);
            if (!(level.getBlockEntity(pos) instanceof com.mohistmc.academy.world.block.entity.BaseNodeBlockEntity node)) {
                helper.fail("Node transfer fixture missing tier " + tier); return;
            }

            ItemStack input = new ItemStack(AcademyItems.ENERGY_UNIT.get());
            node.getItems().set(0, input);
            node.setEnergy(capacities[tier] - 50);
            node.serverTick();
            if ((int) node.getEnergy() != capacities[tier]
                    || com.mohistmc.academy.capability.EnergyItemHelper.getEnergy(input)
                    != com.mohistmc.academy.world.item.EnergyUnit.MAX_ENERGY - 50) {
                helper.fail("Node input transfer ignored max-energy boundary at tier " + tier); return;
            }

            node.getItems().set(0, ItemStack.EMPTY);
            ItemStack output = new ItemStack(AcademyItems.ENERGY_UNIT.get());
            output.setDamageValue(com.mohistmc.academy.world.item.EnergyUnit.MAX_ENERGY);
            node.getItems().set(1, output);
            node.serverTick();
            if ((int) node.getEnergy() != capacities[tier] - bandwidths[tier]
                    || com.mohistmc.academy.capability.EnergyItemHelper.getEnergy(output) != bandwidths[tier]) {
                helper.fail("Node output transfer ignored legacy bandwidth at tier " + tier); return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 10)
    public static void removingWindBaseUpperPartDropsExactlyOneBase(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos basePos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlock(basePos, AcademyBlocks.WINDGEN_BASE.get().defaultBlockState(), 3);
        level.setBlock(basePos.above(), AcademyBlocks.WIND_GEN_BASE_SUB.get().defaultBlockState(), 3);
        level.destroyBlock(basePos.above(), false);
        helper.runAfterDelay(1, () -> {
            long drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(basePos).inflate(2),
                    e -> e.getItem().is(AcademyItems.WINDGEN_BASE.get())).size();
            if (drops != 1 || !level.getBlockState(basePos).isAir()) {
                helper.fail("orphaned wind base did not materialize exactly one recoverable base item"); return;
            }
            helper.succeed();
        });
    }

    private static FriendlyByteBuf menuPos(BlockPos pos) {
        return new FriendlyByteBuf(Unpooled.buffer()).writeBlockPos(pos);
    }

    @GameTest(template = EMPTY)
    public static void machineMenuShiftClickUsesPlayerThenMachineRanges(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlock(pos, AcademyBlocks.MATRIX.get().defaultBlockState(), 3);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5);
        ((MatrixBlockEntity) helper.getLevel().getBlockEntity(pos)).setOwnerUUID(player.getUUID());
        MatrixMenu menu = new MatrixMenu(1, player.getInventory(), menuPos(pos));
        ItemStack core = new ItemStack(AcademyItems.MAT_CORE_0.get(), 1);
        player.getInventory().setItem(9, core);
        ItemStack moved = menu.quickMoveStack(player, 0);
        int coreMenuSlot = 36 + MatrixBlockEntity.CORE_SLOT;
        if (moved.isEmpty() || !menu.getSlot(coreMenuSlot).getItem().is(AcademyItems.MAT_CORE_0.get())
                || !player.getInventory().getItem(9).isEmpty()) {
            helper.fail("player-first shift-click did not target the official Matrix core slot"); return;
        }
        menu.quickMoveStack(player, coreMenuSlot);
        if (!menu.getSlot(coreMenuSlot).getItem().isEmpty()) {
            helper.fail("machine-to-player shift-click used the wrong source range"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void matrixMenuRejectsForeignInventoryMutation(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.getLevel().setBlock(pos, AcademyBlocks.MATRIX.get().defaultBlockState(), 3);
        MatrixBlockEntity matrix = (MatrixBlockEntity) helper.getLevel().getBlockEntity(pos);
        ServerPlayer visitor = helper.makeMockServerPlayerInLevel();
        visitor.setPos(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5);
        matrix.setOwnerUUID(UUID.randomUUID());
        matrix.getItems().set(MatrixBlockEntity.CORE_SLOT,
                new ItemStack(AcademyItems.MAT_CORE_0.get()));
        MatrixMenu menu = new MatrixMenu(31, visitor.getInventory(), menuPos(pos));
        int coreMenuSlot = 36 + MatrixBlockEntity.CORE_SLOT;
        if (!menu.quickMoveStack(visitor, coreMenuSlot).isEmpty()
                || matrix.getItems().get(MatrixBlockEntity.CORE_SLOT).isEmpty()) {
            helper.fail("foreign visitor extracted a protected Matrix component");
            return;
        }
        visitor.getInventory().setItem(9, new ItemStack(AcademyItems.CONSTRAINT_PLATE.get()));
        if (!menu.quickMoveStack(visitor, 0).isEmpty()
                || visitor.getInventory().getItem(9).isEmpty()) {
            helper.fail("foreign visitor inserted into a protected Matrix slot");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void machineContainerHonorsCountsEmptyAndFixedClear(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        helper.getLevel().setBlock(pos, AcademyBlocks.MATRIX.get().defaultBlockState(), 3);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5);
        MatrixMenu menu = new MatrixMenu(2, player.getInventory(), menuPos(pos));
        menu.container.setItem(1, new ItemStack(AcademyItems.CONSTRAINT_PLATE.get(), 10));
        ItemStack removed = menu.container.removeItem(1, 3);
        if (removed.getCount() != 3 || menu.container.getItem(1).getCount() != 7) {
            helper.fail("removeItem ignored requested count"); return;
        }
        menu.container.setItem(1, ItemStack.EMPTY);
        if (!menu.container.getItem(1).isEmpty()) { helper.fail("setItem EMPTY did not clear"); return; }
        menu.container.setItem(2, new ItemStack(AcademyItems.CONSTRAINT_PLATE.get(), 1));
        menu.container.clearContent();
        if (menu.container.getContainerSize() != 4 || !menu.container.isEmpty()) {
            helper.fail("clearContent destroyed fixed size or retained items"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void matrixIsFourSlotPersistentContainer(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
        MatrixBlockEntity original = new MatrixBlockEntity(pos, AcademyBlocks.MATRIX.get().defaultBlockState());
        original.getItems().set(MatrixBlockEntity.CORE_SLOT,
                new ItemStack(AcademyItems.MAT_CORE_1.get(), 1));
        original.getItems().set(MatrixBlockEntity.PLATE_SLOT_0,
                new ItemStack(AcademyItems.CONSTRAINT_PLATE.get(), 2));
        var registries = helper.getLevel().registryAccess();
        CompoundTag tag = original.saveWithoutMetadata(registries);
        MatrixBlockEntity restored = new MatrixBlockEntity(pos, AcademyBlocks.MATRIX.get().defaultBlockState());
        restored.loadWithComponents(tag, registries);
        if (restored.getContainerSize() != 4 || restored.getItems().size() != 4
                || !restored.getItems().get(MatrixBlockEntity.CORE_SLOT).is(AcademyItems.MAT_CORE_1.get())
                || restored.getItems().get(MatrixBlockEntity.PLATE_SLOT_0).getCount() != 2) {
            helper.fail("Matrix four-slot inventory failed NBT round-trip"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void matrixInitializationRequiresCoreAndThreePlates(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 6));
        MatrixBlockEntity matrix = new MatrixBlockEntity(pos, AcademyBlocks.MATRIX.get().defaultBlockState());
        if (matrix.hasInitializationMaterials() || matrix.initializationCoreLevel() != -1) {
            helper.fail("empty Matrix reported initialization ready"); return;
        }
        matrix.getItems().set(MatrixBlockEntity.CORE_SLOT, new ItemStack(AcademyItems.MAT_CORE_2.get(), 1));
        for (int slot = MatrixBlockEntity.PLATE_SLOT_0; slot <= MatrixBlockEntity.PLATE_SLOT_2; slot++) matrix.getItems().set(slot,
                new ItemStack(AcademyItems.CONSTRAINT_PLATE.get(), 1));
        if (!matrix.hasInitializationMaterials() || matrix.initializationCoreLevel() != 2) {
            helper.fail("complete tier-2 Matrix materials were not recognized"); return;
        }
        matrix.getItems().get(2).shrink(1);
        if (matrix.hasInitializationMaterials()) {
            helper.fail("Matrix remained ready with one missing plate"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void nodeWorldModelTracksFinal112EnergyQuarters(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(6, 1, 6));
        helper.getLevel().setBlock(pos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        if (!(helper.getLevel().getBlockEntity(pos) instanceof BaseNodeBlockEntity node)) {
            helper.fail("basic node block entity missing"); return;
        }
        node.setEnergy(node.getMaxEnergy() * .5);
        BlockState state = helper.getLevel().getBlockState(pos);
        var raw = state.getBlock().getStateDefinition().getProperty("working");
        if (!(raw instanceof IntegerProperty energyProperty)
                || state.getValue(energyProperty) != 2) {
            helper.fail("node model did not select the final 1.12.2 half-energy frame"); return;
        }
        node.setEnergy(node.getMaxEnergy());
        state = helper.getLevel().getBlockState(pos);
        if (state.getValue(energyProperty) != 4) {
            helper.fail("node model did not select the final 1.12.2 full-energy frame"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void matrixMigratesRebuild010SlotOrderWithoutLosingMaterials(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 6));
        MatrixBlockEntity rebuild010 = new MatrixBlockEntity(pos,
                AcademyBlocks.MATRIX.get().defaultBlockState());
        // The incorrect layout shipped by rebuilds through 0.0.10.
        rebuild010.getItems().set(0, new ItemStack(AcademyItems.MAT_CORE_1.get()));
        for (int slot = 1; slot < 4; slot++) {
            rebuild010.getItems().set(slot, new ItemStack(AcademyItems.CONSTRAINT_PLATE.get()));
        }
        CompoundTag persisted = rebuild010.saveWithoutMetadata(registries);

        MatrixBlockEntity restored = new MatrixBlockEntity(pos,
                AcademyBlocks.MATRIX.get().defaultBlockState());
        restored.loadWithComponents(persisted, registries);
        if (!restored.getItems().get(MatrixBlockEntity.CORE_SLOT)
                    .is(AcademyItems.MAT_CORE_1.get())
                || !restored.hasInitializationMaterials()
                || restored.getItems().stream().anyMatch(stack -> stack.getCount() != 1)) {
            helper.fail("0.0.10 Matrix inventory did not migrate losslessly to final 1.12.2 slot order");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void matrixComponentsDynamicallyExposeLegacyTierParameters(GameTestHelper helper) {
        MatrixBlockEntity matrix = new MatrixBlockEntity(helper.absolutePos(new BlockPos(4, 1, 7)),
                AcademyBlocks.MATRIX.get().defaultBlockState());
        for (int slot = MatrixBlockEntity.PLATE_SLOT_0; slot <= MatrixBlockEntity.PLATE_SLOT_2; slot++) {
            matrix.getItems().set(slot, new ItemStack(AcademyItems.CONSTRAINT_PLATE.get()));
        }
        var cores = List.of(AcademyItems.MAT_CORE_0.get(), AcademyItems.MAT_CORE_1.get(),
                AcademyItems.MAT_CORE_2.get());
        int[] capacities = {8, 16, 24};
        int[] bandwidths = {60, 240, 540};
        double[] ranges = {24.0, 24.0 * Math.sqrt(2), 24.0 * Math.sqrt(3)};
        for (int tier = 0; tier < cores.size(); tier++) {
            matrix.getItems().set(MatrixBlockEntity.CORE_SLOT, new ItemStack(cores.get(tier)));
            if (matrix.getCapacity() != capacities[tier]
                    || Math.abs(matrix.getBandwidth() - bandwidths[tier]) > 0.001
                    || Math.abs(matrix.getRange() - ranges[tier]) > 0.001) {
                helper.fail("Matrix tier " + tier + " does not match final 1.12.2 parameters"); return;
            }
        }
        ItemStack plate = matrix.getItems().get(2);
        matrix.getItems().set(2, ItemStack.EMPTY);
        if (matrix.getCapacity() != 0 || matrix.getBandwidth() != 0 || matrix.getRange() != 0) {
            helper.fail("Matrix kept operating after an installed plate was removed"); return;
        }
        matrix.getItems().set(2, plate);
        if (matrix.getCapacity() != 24 || matrix.getBandwidth() != 540) {
            helper.fail("Matrix did not automatically resume after its plate was restored"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void matrixUsesLegacySevenProxyFootprintForEveryFacing(GameTestHelper helper) {
        BlockPos main = helper.absolutePos(new BlockPos(8, 2, 8));
        for (net.minecraft.core.Direction facing : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            var state = AcademyBlocks.MATRIX.get().defaultBlockState()
                    .setValue(com.mohistmc.academy.world.block.Matrix.FACING, facing);
            var positions = com.mohistmc.academy.world.block.Matrix.structurePositions(main, state);
            long lower = positions.stream().filter(pos -> pos.getY() == main.getY()).count();
            long upper = positions.stream().filter(pos -> pos.getY() == main.getY() + 1).count();
            if (positions.size() != 7 || new java.util.HashSet<>(positions).size() != 7
                    || lower != 3 || upper != 4 || !positions.contains(main.above())) {
                helper.fail("Matrix footprint mismatch for " + facing + ": " + positions); return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void missingMatrixProxyDropsMainComponentsAndCleansNetworkExactlyOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos main = helper.absolutePos(new BlockPos(6, 2, 6));
        BlockPos nodePos = main.east(4);
        var state = AcademyBlocks.MATRIX.get().defaultBlockState();
        level.setBlock(main, state, 3);
        ((com.mohistmc.academy.world.block.Matrix) AcademyBlocks.MATRIX.get())
                .createStructure(level, main, state);
        level.setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        MatrixBlockEntity matrix = (MatrixBlockEntity) level.getBlockEntity(main);
        NodeBasicBlockEntity node = (NodeBasicBlockEntity) level.getBlockEntity(nodePos);
        matrix.getItems().set(MatrixBlockEntity.CORE_SLOT, new ItemStack(AcademyItems.MAT_CORE_1.get()));
        for (int slot = MatrixBlockEntity.PLATE_SLOT_0; slot <= MatrixBlockEntity.PLATE_SLOT_2; slot++) {
            matrix.getItems().set(slot, new ItemStack(AcademyItems.CONSTRAINT_PLATE.get()));
        }
        matrix.setSSID("matrix-lifecycle");
        matrix.setPassword("");
        matrix.applyCoreLevel(1);
        matrix.setInitialized(true);
        if (!WirelessSystem.createNetwork(level, matrix, "matrix-lifecycle", "")
                || !WirelessSystem.linkNode(level, matrix, node, "")
                || WirelessSystem.getNetwork(level, node) == null) {
            helper.fail("Matrix lifecycle fixture could not form its wireless network"); return;
        }

        var parts = com.mohistmc.academy.world.block.Matrix.structurePositions(main, state);
        BlockPos removedCorner = parts.getLast();
        int directDistance = Math.abs(removedCorner.getX() - main.getX())
                + Math.abs(removedCorner.getY() - main.getY())
                + Math.abs(removedCorner.getZ() - main.getZ());
        if (directDistance <= 1) {
            helper.fail("Matrix lifecycle test did not select a non-neighbour upper proxy"); return;
        }
        String installedBeforeBreak = matrix.getItems().stream()
                .map(stack -> stack.isEmpty() ? "empty" : stack.getItem() + "x" + stack.getCount())
                .collect(java.util.stream.Collectors.joining(","));
        level.destroyBlock(removedCorner, false);
        helper.runAfterDelay(3, () -> {
            AABB dropsArea = new AABB(main).inflate(5);
            int matrixDrops = level.getEntitiesOfClass(ItemEntity.class, dropsArea,
                    e -> e.getItem().is(AcademyItems.MATRIX.get())).stream()
                    .mapToInt(e -> e.getItem().getCount()).sum();
            int coreDrops = level.getEntitiesOfClass(ItemEntity.class, dropsArea,
                    e -> e.getItem().is(AcademyItems.MAT_CORE_1.get())).stream()
                    .mapToInt(e -> e.getItem().getCount()).sum();
            int plateDrops = level.getEntitiesOfClass(ItemEntity.class, dropsArea,
                    e -> e.getItem().is(AcademyItems.CONSTRAINT_PLATE.get())).stream()
                    .mapToInt(e -> e.getItem().getCount()).sum();
            long proxyDrops = level.getEntitiesOfClass(ItemEntity.class, dropsArea,
                    e -> e.getItem().is(AcademyBlocks.MATRIX_SUB.get().asItem())).size();
            boolean residue = !level.getBlockState(main).isAir()
                    || parts.stream().anyMatch(part -> !level.getBlockState(part).isAir());
            var connectedProperty = node.getBlockState().getBlock().getStateDefinition().getProperty("connected");
            boolean modelStillConnected = connectedProperty instanceof net.minecraft.world.level.block.state.properties.BooleanProperty property
                    && node.getBlockState().getValue(property);
            if (matrixDrops != 1 || coreDrops != 1 || plateDrops != 3 || proxyDrops != 0
                    || residue || WirelessSystem.getNetwork(level, node) != null || modelStillConnected) {
                String nearbyItems = level.getEntitiesOfClass(ItemEntity.class, new AABB(main).inflate(32)).stream()
                        .map(entity -> entity.getItem().getItem() + "x" + entity.getItem().getCount()
                                + "@" + entity.blockPosition())
                        .collect(java.util.stream.Collectors.joining(","));
                helper.fail("Matrix teardown mismatch main=" + matrixDrops + " core=" + coreDrops
                        + " plates=" + plateDrops + " proxy=" + proxyDrops + " residue=" + residue
                        + " network=" + (WirelessSystem.getNetwork(level, node) != null)
                        + " connectedModel=" + modelStillConnected + " installedBefore=" + installedBeforeBreak
                        + " nearbyItems=[" + nearbyItems + "]"); return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void advancedDeveloperMainRemovalDropsMachineAndBothInputsExactlyOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos main = helper.absolutePos(new BlockPos(6, 2, 6));
        var state = AcademyBlocks.DEV_ADVANCED.get().defaultBlockState();
        level.setBlock(main, state, 3);
        var dev = (com.mohistmc.academy.world.block.DevMachineBase) AcademyBlocks.DEV_ADVANCED.get();
        var parts = dev.getRotatedSubBlocks(state.getValue(com.mohistmc.academy.world.block.DevMachineBase.FACING)
                        .getOpposite()).stream()
                .map(sub -> main.offset(sub.dx(), sub.dy(), sub.dz())).toList();
        for (BlockPos part : parts) level.setBlock(part, dev.getStructureSubBlock().defaultBlockState(), 19);
        if (!dev.initializeStructure(level, main, parts)
                || !(level.getBlockEntity(main) instanceof DevAdvancedBlockEntity advanced)) {
            helper.fail("Advanced developer fixture did not initialize"); return;
        }
        advanced.getItems().set(0, new ItemStack(AcademyItems.MAGNETIC_COIL.get()));
        advanced.getItems().set(1, new ItemStack(AcademyItems.FACTOR_AEROHAND.get()));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setPos(main.getX() + 10, main.getY(), main.getZ() + 10);
        // The embedded mock's public game-mode/ability state can disagree
        // unless its authoritative game-mode controller is changed as well.
        // Also use the tier-2 pickaxe required by both 1.0.7 and this block's
        // requiresCorrectToolForDrops contract.
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(Items.DIAMOND_PICKAXE));
        if (!level.destroyBlock(main, true, player)) {
            helper.fail("Advanced developer main could not be destroyed by a survival player"); return;
        }
        // Block removal, proxy cleanup and item spawning are synchronous.
        // Count in the same server tick so mock players from parallel GameTests
        // cannot pick up this test's drops before the assertion runs.
        assertAdvancedDeveloperDrops(helper, main, parts);
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void advancedDeveloperProxyRemovalDropsMachineAndBothInputsExactlyOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos main = helper.absolutePos(new BlockPos(6, 2, 6));
        var state = AcademyBlocks.DEV_ADVANCED.get().defaultBlockState();
        level.setBlock(main, state, 3);
        var dev = (com.mohistmc.academy.world.block.DevMachineBase) AcademyBlocks.DEV_ADVANCED.get();
        var parts = dev.getRotatedSubBlocks(state.getValue(com.mohistmc.academy.world.block.DevMachineBase.FACING)
                        .getOpposite()).stream()
                .map(sub -> main.offset(sub.dx(), sub.dy(), sub.dz())).toList();
        for (BlockPos part : parts) level.setBlock(part, dev.getStructureSubBlock().defaultBlockState(), 19);
        if (!dev.initializeStructure(level, main, parts)
                || !(level.getBlockEntity(main) instanceof DevAdvancedBlockEntity advanced)) {
            helper.fail("Advanced developer fixture did not initialize"); return;
        }
        advanced.getItems().set(0, new ItemStack(AcademyItems.MAGNETIC_COIL.get()));
        advanced.getItems().set(1, new ItemStack(AcademyItems.FACTOR_TELEKINESIS.get()));
        level.destroyBlock(parts.getLast(), false);
        assertAdvancedDeveloperDrops(helper, main, parts);
    }

    @GameTest(template = EMPTY)
    public static void sharedCourseSkillIdsResolveInsideEveryAbilityTree(GameTestHelper helper) {
        for (com.mohistmc.academy.skill.AbilityCategory category
                : com.mohistmc.academy.skill.AbilityCategory.all()) {
            for (String id : List.of("brain_course", "brain_course_advanced", "mind_course")) {
                var skill = SkillRegistry.getSkill(category, id);
                if (skill == null || skill.getCategory() != category) {
                    helper.fail("Shared course resolved through the wrong tree: " + category.id() + "/" + id);
                    return;
                }
            }
        }
        helper.succeed();
    }

    private static void assertAdvancedDeveloperDrops(GameTestHelper helper, BlockPos main, List<BlockPos> parts) {
        var level = helper.getLevel();
        AABB area = new AABB(main).inflate(6);
        int machines = level.getEntitiesOfClass(ItemEntity.class, area,
                e -> e.getItem().is(AcademyItems.DEV_ADVANCED.get())).stream()
                .mapToInt(e -> e.getItem().getCount()).sum();
        int coils = level.getEntitiesOfClass(ItemEntity.class, area,
                e -> e.getItem().is(AcademyItems.MAGNETIC_COIL.get())).stream()
                .mapToInt(e -> e.getItem().getCount()).sum();
        int factors = level.getEntitiesOfClass(ItemEntity.class, area,
                e -> e.getItem().getItem() instanceof com.mohistmc.academy.world.item.BaseFactor).stream()
                .mapToInt(e -> e.getItem().getCount()).sum();
        boolean residue = !level.getBlockState(main).isAir()
                || parts.stream().anyMatch(part -> !level.getBlockState(part).isAir());
        if (machines != 1 || coils != 1 || factors != 1 || residue) {
            String nearbyItems = level.getEntitiesOfClass(ItemEntity.class,
                            new AABB(main).inflate(32)).stream()
                    .map(entity -> entity.getItem().getItem() + "x" + entity.getItem().getCount()
                            + "@" + entity.blockPosition())
                    .collect(java.util.stream.Collectors.joining(","));
            helper.fail("Advanced developer teardown mismatch main=" + machines + " coil=" + coils
                    + " factor=" + factors + " residue=" + residue
                    + " nearbyItems=[" + nearbyItems + "]"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 20)
    public static void phaseGeneratorAndEveryNodeTierDropInstalledInventoryExactlyOnce(GameTestHelper helper) {
        var level = helper.getLevel();
        var blocks = List.of(AcademyBlocks.PHASE_GEN.get(), AcademyBlocks.NODE_BASIC.get(),
                AcademyBlocks.NODE_STANDARD.get(), AcademyBlocks.NODE_ADVANCED.get());
        List<BlockPos> positions = new ArrayList<>();
        int expectedIron = 0;
        int expectedGold = 0;
        for (int index = 0; index < blocks.size(); index++) {
            BlockPos pos = helper.absolutePos(new BlockPos(3 + index * 3, 2, 5));
            positions.add(pos);
            level.setBlock(pos, blocks.get(index).defaultBlockState(), 3);
            if (!(level.getBlockEntity(pos) instanceof AcademyContainerBlockEntity container)) {
                helper.fail("Inventory fixture did not create a container at index " + index); return;
            }
            int iron = index + 1;
            int gold = index + 2;
            container.getItems().set(0, new ItemStack(Items.IRON_INGOT, iron));
            container.getItems().set(1, new ItemStack(Items.GOLD_INGOT, gold));
            expectedIron += iron;
            expectedGold += gold;
            if (container instanceof PhaseGenBlockEntity) {
                container.getItems().set(2, new ItemStack(Items.DIAMOND, 7));
            }
            container.setChanged();
            // false deliberately suppresses the block's loot table.  Every
            // entity observed below must therefore come from onRemove.
            level.destroyBlock(pos, false);
        }
        final int ironTotal = expectedIron;
        final int goldTotal = expectedGold;
        helper.runAfterDelay(2, () -> {
            AABB area = new AABB(Vec3.atLowerCornerOf(positions.getFirst()),
                    Vec3.atLowerCornerOf(positions.getLast().offset(1, 1, 1))).inflate(3);
            int iron = level.getEntitiesOfClass(ItemEntity.class, area,
                    e -> e.getItem().is(Items.IRON_INGOT)).stream()
                    .mapToInt(e -> e.getItem().getCount()).sum();
            int gold = level.getEntitiesOfClass(ItemEntity.class, area,
                    e -> e.getItem().is(Items.GOLD_INGOT)).stream()
                    .mapToInt(e -> e.getItem().getCount()).sum();
            int diamonds = level.getEntitiesOfClass(ItemEntity.class, area,
                    e -> e.getItem().is(Items.DIAMOND)).stream()
                    .mapToInt(e -> e.getItem().getCount()).sum();
            boolean residue = positions.stream().anyMatch(pos -> !level.getBlockState(pos).isAir()
                    || level.getBlockEntity(pos) != null);
            if (iron != ironTotal || gold != goldTotal || diamonds != 7 || residue) {
                helper.fail("Container teardown mismatch iron=" + iron + "/" + ironTotal
                        + " gold=" + gold + "/" + goldTotal + " diamonds=" + diamonds
                        + " residue=" + residue); return;
            }
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void phaseGeneratorBlockstateRefreshNeverDropsItsInventory(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
        level.setBlock(pos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof PhaseGenBlockEntity phase)) {
            helper.fail("Phase generator fixture did not create block entity"); return;
        }
        phase.getItems().set(0, new ItemStack(Items.DIAMOND, 3));
        var property = level.getBlockState(pos).getBlock().getStateDefinition().getProperty("lit");
        if (!(property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty lit)) {
            helper.fail("Phase generator lit property is missing"); return;
        }
        level.setBlock(pos, level.getBlockState(pos).setValue(lit, true), 3);
        int drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2),
                e -> e.getItem().is(Items.DIAMOND)).stream().mapToInt(e -> e.getItem().getCount()).sum();
        if (!(level.getBlockEntity(pos) instanceof PhaseGenBlockEntity refreshed)
                || refreshed.getItems().getFirst().getCount() != 3 || drops != 0) {
            helper.fail("State-only refresh removed or duplicated the phase inventory"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void staleMatrixNetworkRecoversWithoutConsumingOrDuplicatingComponents(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 2, 5));
        level.setBlock(pos, AcademyBlocks.MATRIX.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(pos) instanceof MatrixBlockEntity matrix)) {
            helper.fail("Matrix recovery fixture did not create block entity"); return;
        }
        matrix.getItems().set(MatrixBlockEntity.CORE_SLOT, new ItemStack(AcademyItems.MAT_CORE_1.get()));
        for (int slot = MatrixBlockEntity.PLATE_SLOT_0; slot <= MatrixBlockEntity.PLATE_SLOT_2; slot++) {
            matrix.getItems().set(slot, new ItemStack(AcademyItems.CONSTRAINT_PLATE.get()));
        }
        matrix.setSSID("recovered-network");
        matrix.setPassword("recovery-pass");
        matrix.applyCoreLevel(1);
        matrix.setInitialized(true);

        // This is the broken-save shape: the BE says initialized but the
        // dimension SavedData has no corresponding network.
        WirelessSystem.removeNetwork(level, matrix);
        int[] before = matrix.getItems().stream().mapToInt(ItemStack::getCount).toArray();
        var first = WirelessSystem.reconcileMatrixNetwork(level, matrix);
        var network = WiWorldData.get(level).getNetwork(matrix);
        var second = WirelessSystem.reconcileMatrixNetwork(level, matrix);
        var sameNetwork = WiWorldData.get(level).getNetwork(matrix);
        int[] after = matrix.getItems().stream().mapToInt(ItemStack::getCount).toArray();
        if (first != WirelessSystem.MatrixNetworkState.RECOVERED
                || second != WirelessSystem.MatrixNetworkState.PRESENT
                || network == null || network != sameNetwork
                || !java.util.Arrays.equals(before, after)) {
            helper.fail("Matrix recovery was not idempotent or changed installed components"); return;
        }

        // The inverse mismatch must adopt the existing authoritative network,
        // never replace it and lose its node list.
        matrix.setInitialized(false);
        var adopted = WirelessSystem.reconcileMatrixNetwork(level, matrix);
        if (adopted != WirelessSystem.MatrixNetworkState.PRESENT || !matrix.isInitialized()
                || WiWorldData.get(level).getNetwork(matrix) != network) {
            helper.fail("Matrix flag failed to adopt its existing network"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void unrecoverableStaleMatrixReturnsToInitWithoutTouchingSlots(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(6, 2, 6));
        level.setBlock(pos, AcademyBlocks.MATRIX.get().defaultBlockState(), 3);
        MatrixBlockEntity matrix = (MatrixBlockEntity) level.getBlockEntity(pos);
        matrix.setSSID("missing-components");
        matrix.setInitialized(true);
        var state = WirelessSystem.reconcileMatrixNetwork(level, matrix);
        if (state != WirelessSystem.MatrixNetworkState.NEEDS_REINITIALIZATION
                || matrix.isInitialized() || !matrix.getItems().stream().allMatch(ItemStack::isEmpty)
                || WiWorldData.get(level).getNetwork(matrix) != null) {
            helper.fail("Unrecoverable Matrix did not safely return to INIT"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void matrixNodeGeneratorEndToEndAuthority(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos matrixPos = helper.absolutePos(new BlockPos(20, 2, 20));
        BlockPos nodePos = matrixPos.offset(3, 0, 0);
        BlockPos generatorPos = matrixPos.offset(5, 0, 0);
        level.setBlock(matrixPos, AcademyBlocks.MATRIX.get().defaultBlockState(), 3);
        level.setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(generatorPos, AcademyBlocks.SOLAR_GEN.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(matrixPos) instanceof MatrixBlockEntity matrix)
                || !(level.getBlockEntity(nodePos) instanceof NodeBasicBlockEntity node)
                || !(level.getBlockEntity(generatorPos) instanceof SolarGenBlockEntity generator)) {
            helper.fail("wireless end-to-end fixtures failed to place"); return;
        }
        matrix.setPassword("matrix-pass");
        if (WirelessSystem.linkNode(level, matrix, node, "matrix-pass")) {
            helper.fail("uninitialized Matrix accepted a node"); return;
        }
        if (!WirelessSystem.linkGenerator(level, node, generator, false, "")
                || WirelessSystem.getNetwork(level, node) != null
                || WirelessSystem.getNodeConnection(level, node) == null) {
            helper.fail("standalone node rejected a generator"); return;
        }
        matrix.setInitialized(true);
        matrix.getItems().set(MatrixBlockEntity.CORE_SLOT, new ItemStack(AcademyItems.MAT_CORE_0.get()));
        for (int slot = MatrixBlockEntity.PLATE_SLOT_0; slot <= MatrixBlockEntity.PLATE_SLOT_2; slot++) {
            matrix.getItems().set(slot, new ItemStack(AcademyItems.CONSTRAINT_PLATE.get()));
        }
        matrix.applyCoreLevel(0);
        if (!WirelessSystem.createNetwork(level, matrix, "test-net", "matrix-pass")) {
            helper.fail("initialized Matrix failed to create network"); return;
        }
        if (WirelessSystem.linkNode(level, matrix, node, "wrong")) {
            helper.fail("wrong Matrix password linked node"); return;
        }
        if (!WirelessSystem.linkNode(level, matrix, node, "matrix-pass")) {
            helper.fail("valid MatrixNodes production policy failed to link owned in-range node"); return;
        }
        node.setPassword("node-pass");
        if (WirelessSystem.linkGenerator(level, node, generator, true, "wrong")) {
            helper.fail("wrong node password linked generator"); return;
        }
        if (!WirelessSystem.linkGenerator(level, node, generator, true, "node-pass")) {
            helper.fail("networked node rejected valid generator credentials"); return;
        }

        BlockPos farPos = matrixPos.offset(30, 0, 0);
        level.setBlock(farPos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        if (WirelessSystem.linkNode(level, matrix,
                (NodeBasicBlockEntity) level.getBlockEntity(farPos), "matrix-pass")) {
            helper.fail("out-of-range node joined Matrix network"); return;
        }

        // Legacy tier-0 capacity is eight nodes total. The first is linked.
        for (int i = 0; i < 7; i++) {
            BlockPos extraPos = matrixPos.offset(0, 0, 2 + i);
            level.setBlock(extraPos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
            var extra = (NodeBasicBlockEntity) level.getBlockEntity(extraPos);
            if (!WirelessSystem.linkNode(level, matrix, extra, "matrix-pass")) {
                helper.fail("Matrix rejected node before reaching capacity"); return;
            }
        }
        BlockPos overflowPos = matrixPos.offset(0, 0, 12);
        level.setBlock(overflowPos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        if (WirelessSystem.linkNode(level, matrix,
                (NodeBasicBlockEntity) level.getBlockEntity(overflowPos), "matrix-pass")) {
            helper.fail("Matrix exceeded node capacity"); return;
        }
        ItemStack installedPlate = matrix.getItems().get(2);
        matrix.getItems().set(2, ItemStack.EMPTY);
        if (matrix.getCapacity() != 0 || matrix.getBandwidth() != 0 || matrix.getRange() != 0
                || WirelessSystem.getNetwork(level, node) == null
                || WirelessSystem.linkNode(level, matrix,
                (NodeBasicBlockEntity) level.getBlockEntity(overflowPos), "matrix-pass")) {
            helper.fail("Missing Matrix component did not pause its existing network safely"); return;
        }
        matrix.getItems().set(2, installedPlate);
        if (matrix.getCapacity() != 8 || matrix.getBandwidth() != 60
                || Math.abs(matrix.getRange() - 24) > 0.001) {
            helper.fail("Restored Matrix component did not resume legacy parameters"); return;
        }
        if (!WirelessSystem.unlinkNode(level, matrix, node)
                || WirelessSystem.getNetwork(level, node) != null
                || !WirelessSystem.linkGenerator(level, node, generator, true, "node-pass")) {
            helper.fail("unlink was not immediate or standalone node stopped serving generators"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void machineMenuDataHasBoundedServerValues(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos phasePos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(phasePos, AcademyBlocks.PHASE_GEN.get().defaultBlockState(), 3);
        player.setPos(phasePos.getX() + 1.5, phasePos.getY(), phasePos.getZ() + 0.5);
        PhaseGenMenu phase = new PhaseGenMenu(3, player.getInventory(), menuPos(phasePos));
        if (phase.getProgress() < 0 || phase.getProcessTicks() < 1) { helper.fail("Phase data bounds invalid"); return; }

        BlockPos fusorPos = helper.absolutePos(new BlockPos(2, 1, 1));
        helper.getLevel().setBlock(fusorPos, AcademyBlocks.IMAG_FUSOR.get().defaultBlockState(), 3);
        ImagFusorMenu fusor = new ImagFusorMenu(4, player.getInventory(), menuPos(fusorPos));
        if (fusor.getFluidAmount() < 0 || fusor.getMaxFluid() < 1) { helper.fail("Fusor data bounds invalid"); return; }

        BlockPos devPos = helper.absolutePos(new BlockPos(3, 1, 1));
        helper.getLevel().setBlock(devPos, AcademyBlocks.DEV_ADVANCED.get().defaultBlockState(), 3);
        if (!(helper.getLevel().getBlockEntity(devPos) instanceof DevAdvancedBlockEntity dev)) {
            helper.fail("Advanced developer BE missing"); return;
        }
        dev.setEnergy(143_210);
        DevAdvancedMenu devMenu = new DevAdvancedMenu(5, player.getInventory(), menuPos(devPos));
        if (devMenu.getEnergy() != 143_210 || devMenu.getMaxEnergy() != DevAdvancedBlockEntity.MAX_ENERGY) {
            helper.fail("Developer ContainerData did not expose authoritative energy bounds"); return;
        }
        if (new ItemStack(AcademyItems.MAGNETIC_COIL.get()).getMaxStackSize() != 1
                || new ItemStack(AcademyItems.FACTOR_AEROHAND.get()).getMaxStackSize() != 1
                || !devMenu.slots.isEmpty()) {
            helper.fail("Developer reset materials must be single items and its legacy menu must be slotless"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void recipeManagerLoadsAndMatchesAllAcademyMachineRecipes(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var former = manager.getAllRecipesFor(AcademyRecipeTypes.METAL_FORMING.get());
        var fusor = manager.getAllRecipesFor(AcademyRecipeTypes.IMAG_FUSING.get());
        if (former.size() != 27 || fusor.size() != 2) {
            helper.fail("RecipeManager loaded " + former.size() + " Metal Former and " + fusor.size() + " Imag Fusor recipes");
            return;
        }
        for (var holder : former) {
            MetalFormingRecipe recipe = holder.value();
            ItemStack output = recipe.assemble(
                    new MetalFormingRecipeInput(ItemStack.EMPTY, recipe.getMode()),
                    helper.getLevel().registryAccess());
            if (recipe.getIngredients().isEmpty()
                    || recipe.getIngredients().getFirst().getItems().length == 0
                    || output.isEmpty()) {
                if (recipe.getOutputTag() == null) {
                    helper.fail("Concrete Metal Former recipe has an unresolved input/output: " + holder.id()); return;
                }
                // Legacy cross-mod recipes were only registered when the old
                // OreDictionary contained both sides. Empty optional common
                // tags therefore correctly leave the recipe inactive.
                continue;
            }
            ItemStack input = recipe.getIngredients().getFirst().getItems()[0].copy();
            input.setCount(recipe.getInputCount());
            if (!recipe.matches(new MetalFormingRecipeInput(input, recipe.getMode()), helper.getLevel())
                    || output.isEmpty()) {
                helper.fail("Metal Former recipe does not match/assemble: " + holder.id()); return;
            }
        }
        for (var holder : fusor) {
            ImagFusorRecipe recipe = holder.value();
            if (recipe.input().getItems().length == 0) {
                helper.fail("Imag Fusor recipe has no resolvable input: " + holder.id()); return;
            }
            ItemStack input = recipe.input().getItems()[0].copy();
            if (!recipe.matches(new ImagFusorRecipeInput(input), helper.getLevel())
                    || recipe.assemble(new ImagFusorRecipeInput(input), helper.getLevel().registryAccess()).isEmpty()) {
                helper.fail("Imag Fusor recipe does not match/assemble: " + holder.id()); return;
            }
        }
        Set<String> expectedOfficial = Set.of(
                "ability_interf","app_freq_transmitter","app_media_player","app_skill_tree","brain_comp",
                "calc_chip","calc_chip_2","cons_ingot","cons_plate","conv_comp","crystal0","data_chip","data_chip_2",
                "dev_advanced","dev_normal","dev_normal_2","dev_portable","ene_unit","ene_unit_2","ene_unit_3",
                "frame","fusor","fusor_2","fusor_3","imagsil_ingot","info_comp","mag_hook","magnetic_coil",
                "mat","mat_core_0","mat_core_1","mat_core_2","matter_unit","metal_former","node0","node1","node2",
                "phase_gen","plateiron","reso_comp","si_piece","silbarn","solar_gen","terminal","tutorial","wafer",
                "windgen_base","windgen_fan","windgen_main","windgen_pillar",
                "rf_input","rf_output","rf_input_from_output","rf_output_from_input");
        Set<String> loadedOfficial = new HashSet<>();
        manager.getAllRecipesFor(RecipeType.CRAFTING).forEach(holder -> collectOfficial(holder.id(), loadedOfficial));
        manager.getAllRecipesFor(RecipeType.SMELTING).forEach(holder -> collectOfficial(holder.id(), loadedOfficial));
        if (!loadedOfficial.equals(expectedOfficial)) {
            Set<String> missing = new HashSet<>(expectedOfficial); missing.removeAll(loadedOfficial);
            Set<String> extra = new HashSet<>(loadedOfficial); extra.removeAll(expectedOfficial);
            helper.fail("Official 1.0.7 recipe load drift; missing=" + missing + ", extra=" + extra); return;
        }
        if (loadedOfficial.size() + former.size() + fusor.size() != 83) {
            helper.fail("Academy RecipeManager total is not 83"); return;
        }
        helper.succeed();
    }

    private static void collectOfficial(ResourceLocation id, Set<String> out) {
        if (id.getNamespace().equals(AcademyCraft.MODID) && id.getPath().startsWith("official_")) {
            out.add(id.getPath().substring("official_".length()));
        }
    }

    @GameTest(template = EMPTY)
    public static void metalFormerProcessesEveryModeWithRealEnergyAndInventory(GameTestHelper helper) {
        record Example(MetalFormerRecipes.Mode mode, net.minecraft.world.level.ItemLike input,
                       int count, net.minecraft.world.level.ItemLike output, int outputCount) {}
        List<Example> examples = List.of(
                new Example(MetalFormerRecipes.Mode.PLATE, net.minecraft.world.item.Items.IRON_INGOT, 1, AcademyItems.REINFORCED_IRON_PLATE.get(), 1),
                new Example(MetalFormerRecipes.Mode.INCISE, AcademyItems.IMAG_SILICON_INGOT.get(), 1, AcademyItems.WAFER.get(), 2),
                new Example(MetalFormerRecipes.Mode.ETCH, AcademyItems.DATA_CHIP.get(), 1, AcademyItems.CALC_CHIP.get(), 1),
                new Example(MetalFormerRecipes.Mode.REFINE, AcademyItems.IMAGSIL_ORE.get(), 1, AcademyItems.IMAG_SILICON_INGOT.get(), 4),
                new Example(MetalFormerRecipes.Mode.REFINE, net.minecraft.world.item.Items.COPPER_ORE, 1,
                        net.minecraft.world.item.Items.COPPER_INGOT, 2));
        int x = 1;
        for (Example e : examples) {
            BlockPos pos = helper.absolutePos(new BlockPos(x++, 2, 1));
            MetalFomerBlockEntity be = new MetalFomerBlockEntity(pos, AcademyBlocks.METAL_FORMER.get().defaultBlockState());
            be.setLevel(helper.getLevel());
            while (be.getMode() != e.mode()) be.cycleMode(1);
            be.getItems().set(MetalFomerBlockEntity.SLOT_IN, new ItemStack(e.input(), e.count()));
            be.injectEnergy(MetalFomerBlockEntity.MAX_ENERGY);
            for (int tick = 0; tick < MetalFomerBlockEntity.WORK_TICKS + 8; tick++) be.serverTick();
            ItemStack result = be.getItems().get(MetalFomerBlockEntity.SLOT_OUT);
            if (!be.getItems().get(MetalFomerBlockEntity.SLOT_IN).isEmpty()
                    || !result.is(e.output().asItem()) || result.getCount() != e.outputCount()
                    || be.getEnergy() >= MetalFomerBlockEntity.MAX_ENERGY) {
                helper.fail("Metal Former failed real processing for mode " + e.mode()); return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void imagFusorProcessesBothRecipesAndNeverConsumesWhenBlocked(GameTestHelper helper) {
        // A blocked empty-container slot must stop tank filling instead of deleting the container return.
        ImagFusorBlockEntity blockedContainer = fusor(helper);
        blockedContainer.getItems().set(ImagFusorBlockEntity.FLUID_INPUT_SLOT,
                new ItemStack(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get()));
        blockedContainer.getItems().set(ImagFusorBlockEntity.EMPTY_UNIT_SLOT,
                new ItemStack(AcademyItems.COIN.get()));
        blockedContainer.tick();
        if (!blockedContainer.getItems().get(ImagFusorBlockEntity.FLUID_INPUT_SLOT)
                .is(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get()) || blockedContainer.getFluidAmount() != 0) {
            helper.fail("Imag Fusor swallowed a phase container when its return slot was blocked"); return;
        }
        // Insufficient liquid: phase containers may be accepted, but recipe input must remain untouched.
        ImagFusorBlockEntity insufficient = fusor(helper);
        insufficient.getItems().set(ImagFusorBlockEntity.FLUID_INPUT_SLOT, new ItemStack(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get(), 2));
        insufficient.getItems().set(ImagFusorBlockEntity.INPUT_SLOT, new ItemStack(AcademyItems.CRYSTAL_LOW.get()));
        for (int i = 0; i < 140; i++) insufficient.tick();
        if (!insufficient.getItems().get(ImagFusorBlockEntity.INPUT_SLOT).is(AcademyItems.CRYSTAL_LOW.get())
                || !insufficient.getItems().get(ImagFusorBlockEntity.OUTPUT_SLOT).isEmpty()) {
            helper.fail("Imag Fusor consumed recipe input with insufficient liquid"); return;
        }
        // Blocked output: neither recipe input nor liquid may be consumed.
        ImagFusorBlockEntity blocked = fusor(helper);
        blocked.getItems().set(ImagFusorBlockEntity.FLUID_INPUT_SLOT, new ItemStack(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get(), 3));
        blocked.getItems().set(ImagFusorBlockEntity.INPUT_SLOT, new ItemStack(AcademyItems.CRYSTAL_LOW.get()));
        blocked.getItems().set(ImagFusorBlockEntity.OUTPUT_SLOT, new ItemStack(AcademyItems.COIN.get()));
        for (int i = 0; i < 140; i++) blocked.tick();
        if (!blocked.getItems().get(ImagFusorBlockEntity.INPUT_SLOT).is(AcademyItems.CRYSTAL_LOW.get())
                || !blocked.getItems().get(ImagFusorBlockEntity.OUTPUT_SLOT).is(AcademyItems.COIN.get())
                || blocked.getFluidAmount() != 3000) {
            helper.fail("Imag Fusor consumed material/liquid into blocked output"); return;
        }
        if (!runFusorRecipe(helper, AcademyItems.CRYSTAL_LOW.get(), 3, AcademyItems.CRYSTAL_NORMAL.get())
                || !runFusorRecipe(helper, AcademyItems.CRYSTAL_NORMAL.get(), 8, AcademyItems.CRYSTAL_PURE.get())) {
            helper.fail("Imag Fusor failed one of its two real processing recipes"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void phaseGeneratorClampsStorageAndRejectsNegativeExtraction(GameTestHelper helper) {
        com.mohistmc.academy.world.block.entity.PhaseGenBlockEntity gen =
                new com.mohistmc.academy.world.block.entity.PhaseGenBlockEntity(
                        helper.absolutePos(new BlockPos(9, 2, 9)), AcademyBlocks.PHASE_GEN.get().defaultBlockState());
        gen.setLevel(helper.getLevel());
        gen.getItems().set(0, new ItemStack(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get(), 64));
        gen.tick(); // legacy order: generation runs before the first container is emptied
        int tankBeforePull = gen.getFluidAmount();
        if (gen.getStoredEnergy() != 0 || tankBeforePull != PhaseGenBlockEntity.PER_UNIT
                || gen.getProvidedEnergy(50) != 0 || gen.getFluidAmount() != tankBeforePull) {
            helper.fail("Phase generator bypassed its legacy IF buffer when pulled"); return;
        }
        gen.tick();
        int tankAfterGeneration = gen.getFluidAmount();
        if (gen.getStoredEnergy() != 50 || tankAfterGeneration != PhaseGenBlockEntity.PER_UNIT * 2 - PhaseGenBlockEntity.CONSUME_PER_TICK
                || gen.getProvidedEnergy(100) != 50 || gen.getFluidAmount() != tankAfterGeneration) {
            helper.fail("Phase generator did not fill and drain its 6000 IF buffer like 1.0.7"); return;
        }
        for (int i = 0; i < 1200; i++) gen.tick();
        float before = gen.getStoredEnergy();
        double invalid = gen.getProvidedEnergy(-100);
        if (before != gen.getMaxEnergy() || invalid != 0 || gen.getStoredEnergy() != before) {
            helper.fail("Phase generator exceeded storage or minted energy through negative extraction"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void everyBasicGeneratorRejectsNegativeExtraction(GameTestHelper helper) {
        BlockPos windPos = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos solarPos = helper.absolutePos(new BlockPos(5, 2, 3));
        BlockPos catPos = helper.absolutePos(new BlockPos(7, 2, 3));
        var wind = new com.mohistmc.academy.world.block.entity.WindGenBaseBlockEntity(
                windPos, AcademyBlocks.WINDGEN_BASE.get().defaultBlockState());
        var solar = new com.mohistmc.academy.world.block.entity.SolarGenBlockEntity(
                solarPos, AcademyBlocks.SOLAR_GEN.get().defaultBlockState());
        var cat = new com.mohistmc.academy.world.block.entity.CatEngineBlockEntity(
                catPos, AcademyBlocks.CAT_ENGINE.get().defaultBlockState());
        wind.setEnergy(200);
        solar.setEnergy(200);
        cat.tick(helper.getLevel(), catPos, AcademyBlocks.CAT_ENGINE.get().defaultBlockState());
        int windBefore = wind.getEnergyStored();
        int solarBefore = solar.getEnergyStored();
        float catBefore = cat.getStoredEnergy();
        if (wind.getProvidedEnergy(-100) != 0 || wind.getProvidedEnergy(Double.NaN) != 0
                || wind.getEnergyStored() != windBefore
                || solar.getProvidedEnergy(-100) != 0 || solar.getProvidedEnergy(Double.POSITIVE_INFINITY) != 0
                || solar.getEnergyStored() != solarBefore
                || cat.getProvidedEnergy(-100) != 0 || cat.getProvidedEnergy(Double.NaN) != 0
                || cat.getStoredEnergy() != catBefore) {
            helper.fail("basic generator accepted a negative or non-finite request"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void machinePersistenceAndMultiCountOutputBoundariesAreSanitized(GameTestHelper helper) {
        CompoundTag virtualNode = new CompoundTag();
        virtualNode.putInt("x", 1); virtualNode.putInt("y", 2); virtualNode.putInt("z", 3);
        CompoundTag virtualGenerator = new CompoundTag();
        virtualGenerator.putInt("x", 4); virtualGenerator.putInt("y", 5); virtualGenerator.putInt("z", 6);
        CompoundTag virtualReceiver = new CompoundTag();
        virtualReceiver.putInt("x", 7); virtualReceiver.putInt("y", 8); virtualReceiver.putInt("z", 9);
        net.minecraft.nbt.ListTag generators = new net.minecraft.nbt.ListTag(); generators.add(virtualGenerator);
        net.minecraft.nbt.ListTag receivers = new net.minecraft.nbt.ListTag(); receivers.add(virtualReceiver);
        CompoundTag savedConnection = new CompoundTag();
        savedConnection.put("node", virtualNode); savedConnection.put("generators", generators); savedConnection.put("receivers", receivers);
        var restoredConnection = new com.mohistmc.academy.energy.impl.NodeConn(
                new com.mohistmc.academy.energy.impl.WiWorldData(), savedConnection);
        if (restoredConnection.getLoad() != 2) {
            helper.fail("wireless connections could not restore before world binding"); return;
        }

        CompoundTag matrixTag = new CompoundTag();
        matrixTag.putString("ownerUUID", "not-a-uuid");
        matrixTag.putDouble("bandwidth", Double.NaN);
        matrixTag.putDouble("matrix_range", Double.POSITIVE_INFINITY);
        matrixTag.putInt("capacity", Integer.MIN_VALUE);
        MatrixBlockEntity corruptMatrix = new MatrixBlockEntity(helper.absolutePos(new BlockPos(11, 2, 10)),
                AcademyBlocks.MATRIX.get().defaultBlockState());
        corruptMatrix.loadAdditional(matrixTag, helper.getLevel().registryAccess());
        if (corruptMatrix.getOwnerUUID() != null || !Double.isFinite(corruptMatrix.getBandwidth())
                || !Double.isFinite(corruptMatrix.getRange()) || corruptMatrix.getCapacity() < 0) {
            helper.fail("Matrix accepted corrupt UUID/numeric persistence"); return;
        }

        CompoundTag nodeTag = new CompoundTag();
        nodeTag.putString("ownerUUID", "broken-owner");
        nodeTag.putDouble("node_energy", Double.NaN);
        nodeTag.putDouble("node_maxEnergy", Double.NEGATIVE_INFINITY);
        nodeTag.putDouble("node_bandwidth", -50);
        var corruptNode = new com.mohistmc.academy.world.block.entity.NodeBasicBlockEntity(
                helper.absolutePos(new BlockPos(12, 2, 10)), AcademyBlocks.NODE_BASIC.get().defaultBlockState());
        corruptNode.loadAdditional(nodeTag, helper.getLevel().registryAccess());
        if (corruptNode.getOwnerUUID() != null || corruptNode.getEnergy() != 0
                || !Double.isFinite(corruptNode.getMaxEnergy()) || corruptNode.getMaxEnergy() < 0
                || !Double.isFinite(corruptNode.getBandwidth()) || corruptNode.getBandwidth() < 0) {
            helper.fail("Node accepted corrupt UUID/numeric persistence"); return;
        }

        CompoundTag phaseTag = new CompoundTag();
        phaseTag.putFloat("storedEnergy", Float.NaN);
        PhaseGenBlockEntity phase = new PhaseGenBlockEntity(helper.absolutePos(new BlockPos(10, 2, 10)),
                AcademyBlocks.PHASE_GEN.get().defaultBlockState());
        phase.loadAdditional(phaseTag, helper.getLevel().registryAccess());
        if (phase.getFluidAmount() != 0 || phase.getStoredEnergy() != 0) {
            helper.fail("Phase generator accepted corrupt persisted tank/energy"); return;
        }

        CompoundTag fusorTag = new CompoundTag();
        fusorTag.putInt("fluidAmount", Integer.MAX_VALUE);
        fusorTag.putInt("processingTime", -50);
        ImagFusorBlockEntity fusor = fusor(helper);
        fusor.loadAdditional(fusorTag, helper.getLevel().registryAccess());
        if (fusor.getFluidAmount() != fusor.getMaxFluid() || fusor.getProcessingTime() != 0) {
            helper.fail("Imag Fusor accepted corrupt persisted fluid/time"); return;
        }

        ItemStack existing = new ItemStack(AcademyItems.COIN.get(), 62);
        ItemStack resultThree = new ItemStack(AcademyItems.COIN.get(), 3);
        if (ImagFusorBlockEntity.canAcceptOutput(existing, resultThree)) {
            helper.fail("Imag Fusor accepted a multi-count output that exceeds stack capacity"); return;
        }
        existing.setCount(61);
        if (!ImagFusorBlockEntity.canAcceptOutput(existing, resultThree)) {
            helper.fail("Imag Fusor rejected an exactly fitting multi-count output"); return;
        }
        ItemStack componentA = new ItemStack(Items.WOODEN_SWORD);
        ItemStack componentB = new ItemStack(Items.WOODEN_SWORD);
        componentA.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("A"));
        componentB.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("B"));
        if (ImagFusorBlockEntity.canAcceptOutput(componentA, componentB)) {
            helper.fail("Imag Fusor merged outputs with different components"); return;
        }
        helper.succeed();
    }

    private static ImagFusorBlockEntity fusor(GameTestHelper helper) {
        ImagFusorBlockEntity be = new ImagFusorBlockEntity(helper.absolutePos(new BlockPos(8, 2, 8)),
                AcademyBlocks.IMAG_FUSOR.get().defaultBlockState());
        be.setLevel(helper.getLevel());
        return be;
    }

    private static boolean runFusorRecipe(GameTestHelper helper, net.minecraft.world.level.ItemLike input,
                                          int liquidUnits, net.minecraft.world.level.ItemLike expected) {
        ImagFusorBlockEntity be = fusor(helper);
        be.getItems().set(ImagFusorBlockEntity.FLUID_INPUT_SLOT,
                new ItemStack(AcademyItems.MATTER_UNIT_PHASE_LIQUID.get(), liquidUnits));
        be.getItems().set(ImagFusorBlockEntity.INPUT_SLOT, new ItemStack(input));
        be.injectEnergy(be.getMaxEnergy());
        // Units are transferred one per tick before the 120 productive ticks can begin.
        for (int i = 0; i < 120 + liquidUnits; i++) be.tick();
        return be.getItems().get(ImagFusorBlockEntity.INPUT_SLOT).isEmpty()
                && be.getItems().get(ImagFusorBlockEntity.OUTPUT_SLOT).is(expected.asItem())
                && be.getFluidAmount() == 0;
    }

    @GameTest(template = EMPTY)
    public static void markTeleportUsesLegacyHeldTickAndResourceCaps(GameTestHelper helper) {
        if (MarkTeleportEffect.computeMaxDistance(0, 1000, 0) != 2
                || MarkTeleportEffect.computeMaxDistance(0, 1000, 20) != 25
                || MarkTeleportEffect.computeMaxDistance(1, 20, 100) != 5
                || MarkTeleportEffect.computeMaxDistance(1, 1000, 10) != 22) {
            helper.fail("Mark Teleport no longer matches MTContext getMaxDist");
        } else helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void abilityEntitiesAreRegistered(GameTestHelper helper) {
        List<String> ids = List.of("coin_entity", "ore_highlight", "railgun_beam",
                "meltdown_beam", "meltdown_barrage", "shield_effect", "plasma_orb", "silbarn", "mag_hook_projectile");
        for (String path : ids) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(AcademyCraft.MODID, path);
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                helper.fail("Missing AcademyCraft entity registration: " + id);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void silbarnItemConsumesOnceAndSpawnsAuthoritativeProjectile(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        ItemStack stack = new ItemStack(AcademyItems.SILBARN.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        AcademyItems.SILBARN.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        List<EntitySilbarn> projectiles = helper.getLevel().getEntitiesOfClass(EntitySilbarn.class,
                player.getBoundingBox().inflate(3));
        if (stack.getCount() != 1 || projectiles.size() != 1
                || !player.getUUID().equals(projectiles.getFirst().ownerId())) {
            helper.fail("Silbarn did not atomically consume one item and create its owned projectile");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void silbarnCollisionFreezesAndExpiresAfterLegacyDelay(GameTestHelper helper) {
        BlockPos wall = helper.absolutePos(new BlockPos(4, 2, 2));
        helper.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        EntitySilbarn projectile = new EntitySilbarn(AcademyEntities.SILBARN.get(), helper.getLevel());
        projectile.setPos(wall.getX() - 1.2D, wall.getY() + 0.5D, wall.getZ() + 0.5D);
        projectile.setDeltaMovement(2, 0, 0);
        if (!helper.getLevel().addFreshEntity(projectile)) {
            helper.fail("Silbarn projectile could not spawn");
            return;
        }
        projectile.tick();
        if (!projectile.isHit() || projectile.getDeltaMovement().lengthSqr() != 0) {
            helper.fail("Silbarn did not enter its synchronized fixed impact state");
            return;
        }
        for (int tick = 0; tick < EntitySilbarn.IMPACT_LIFETIME_TICKS && projectile.isAlive(); tick++) {
            projectile.tick();
        }
        if (!projectile.isRemoved()) helper.fail("Silbarn survived its bounded impact lifetime");
        else helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void rayBarrageBreaksSilbarnAndUsesLegacyRectangularFan(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos start = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D);
        player.setYRot(0);
        player.setYHeadRot(0);
        player.setXRot(0);
        Vec3 eye = player.getEyePosition();

        EntitySilbarn silbarn = new EntitySilbarn(AcademyEntities.SILBARN.get(), helper.getLevel());
        silbarn.setPos(eye.x, eye.y - 0.2D, eye.z + 5.0D);
        if (!helper.getLevel().addFreshEntity(silbarn)) {
            helper.fail("Ray Barrage Silbarn target could not spawn"); return;
        }

        Zombie inside = EntityType.ZOMBIE.create(helper.getLevel());
        Zombie outside = EntityType.ZOMBIE.create(helper.getLevel());
        if (inside == null || outside == null) { helper.fail("Ray Barrage cone targets could not spawn"); return; }
        double insideYaw = Math.toRadians(20.0D);
        double outsideYaw = Math.toRadians(40.0D);
        inside.setPos(eye.x - Math.sin(insideYaw) * 7.0D, player.getY(), eye.z + Math.cos(insideYaw) * 7.0D);
        outside.setPos(eye.x - Math.sin(outsideYaw) * 7.0D, player.getY(), eye.z + Math.cos(outsideYaw) * 7.0D);
        helper.getLevel().addFreshEntity(inside);
        helper.getLevel().addFreshEntity(outside);
        float insideHealth = inside.getHealth();
        float outsideHealth = outside.getHealth();

        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setDevMode(true);
        RayBarrageEffect effect = new RayBarrageEffect();
        if (!effect.canActivate(player, data)) { helper.fail("Ray Barrage key-down activation was rejected"); return; }
        effect.execute(player, data);
        if (!silbarn.isHit()) { helper.fail("Ray Barrage did not post the legacy heavy Silbarn collision"); return; }
        if (inside.getHealth() >= insideHealth || outside.getHealth() != outsideHealth) {
            helper.fail("Ray Barrage no longer uses the legacy ±27.5 yaw / ±55 pitch damage window"); return;
        }
        List<MeltdownBarrageEntity> barrages = helper.getLevel().getEntitiesOfClass(MeltdownBarrageEntity.class,
                silbarn.getBoundingBox().inflate(2.0D));
        if (barrages.size() != 1 || barrages.getFirst().rayCount() < 25 || barrages.getFirst().rayCount() > 30) {
            helper.fail("Ray Barrage did not create one bounded 25..30-ray render batch"); return;
        }
        List<MeltdownBeamEntity> preRays = helper.getLevel().getEntitiesOfClass(MeltdownBeamEntity.class,
                new AABB(player.getEyePosition(), silbarn.position()).inflate(1.0D));
        if (preRays.size() != 1 || preRays.getFirst().getLifetime() != 50) {
            helper.fail("Ray Barrage special pre-ray does not retain its legacy 50-tick lifetime"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void magneticHookConsumesHitsForFourAndReturnsItsItem(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        ItemStack stack = new ItemStack(AcademyItems.MAG_HOOK.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        AcademyItems.MAG_HOOK.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        List<EntityMagHook> spawned = helper.getLevel().getEntitiesOfClass(EntityMagHook.class,
                player.getBoundingBox().inflate(4));
        if (stack.getCount() != 1 || spawned.size() != 1) {
            helper.fail("Magnetic Hook did not consume one item and spawn one projectile");
            return;
        }
        spawned.getFirst().discard();

        // Zombies have innate armour in 1.21.1, so their observed health loss
        // is 3.936 even when the projectile supplies the legacy 4.0 damage.
        // Use an unarmoured living target to verify the supplied damage value.
        var target = EntityType.PIG.create(helper.getLevel());
        if (target == null) {
            helper.fail("Could not create Magnetic Hook damage target");
            return;
        }
        Vec3 start = helper.absoluteVec(new Vec3(2.5, 3, 2.5));
        target.setPos(start.x + 1.3, start.y, start.z);
        helper.getLevel().addFreshEntity(target);
        float health = target.getHealth();
        EntityMagHook hook = new EntityMagHook(AcademyEntities.MAG_HOOK.get(), helper.getLevel());
        hook.launch(player, true);
        hook.setPos(start);
        hook.setDeltaMovement(2, 0, 0);
        helper.getLevel().addFreshEntity(hook);
        hook.tick();
        float dealt = health - target.getHealth();
        if (Math.abs(dealt - EntityMagHook.ENTITY_HIT_DAMAGE) > 0.01F
                || !hook.isRemoved()) {
            helper.fail("Magnetic Hook did not deal the legacy four damage and recover after entity hit"
                    + " dealt=" + dealt + " removed=" + hook.isRemoved()
                    + " hookPos=" + hook.position() + " targetPos=" + target.position());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void magneticHookBlockAnchorCanBeRecoveredByTouch(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos wall = helper.absolutePos(new BlockPos(4, 2, 2));
        helper.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
        EntityMagHook hook = new EntityMagHook(AcademyEntities.MAG_HOOK.get(), helper.getLevel());
        hook.launch(player, true);
        hook.setPos(wall.getX() - 1.2D, wall.getY() + 0.5D, wall.getZ() + 0.5D);
        hook.setDeltaMovement(2, 0, 0);
        helper.getLevel().addFreshEntity(hook);
        hook.tick();
        if (!hook.isHit() || !wall.equals(hook.anchor())) {
            helper.fail("Magnetic Hook did not become a fixed block anchor");
            return;
        }
        hook.tickCount = 6;
        hook.playerTouch(player);
        if (!hook.isRemoved() || !player.getInventory().contains(new ItemStack(AcademyItems.MAG_HOOK.get()))) {
            helper.fail("Touching a fixed Magnetic Hook did not recover its consumed item");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void shieldEffectPersistsUntilItsContextDiscardsIt(GameTestHelper helper) {
        ServerPlayer owner = helper.makeMockServerPlayerInLevel();
        ShieldEffectEntity shield = new ShieldEffectEntity(AcademyEntities.SHIELD_EFFECT.get(), helper.getLevel())
                .bind(owner.getUUID());
        for (int tick = 0; tick < 200; tick++) {
            shield.tickCount++;
            shield.tick();
        }
        if (shield.isRemoved()) { helper.fail("An active legacy shield visual expired before its context"); return; }
        shield.discard();
        if (!shield.isRemoved()) helper.fail("Shield context teardown did not discard its visual");
        else helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void chargingIdentityCannotBeSlotSwapped(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        SkillChargingManager.startCharging(id, 2, "academy:synthetic_test_skill",1,0);
        SkillChargingManager.ChargingState state = SkillChargingManager.getState(id);
        if (state == null || state.epoch == 0 || state.slotIndex != 2 || !"academy:synthetic_test_skill".equals(state.skillId)) {
            helper.fail("Charging state did not preserve the authoritative slot and skill identity");
            return;
        }
        SkillChargingManager.startCharging(id, 7, "academy:replacement_skill",2,0);
        SkillChargingManager.ChargingState replacement = SkillChargingManager.getState(id);
        if (replacement == state || replacement == null || replacement.slotIndex != 7
                || !"academy:replacement_skill".equals(replacement.skillId)) {
            helper.fail("Charging replacement left a mixed slot/skill identity");
            return;
        }
        if (replacement.epoch==state.epoch||SkillChargingManager.matches(replacement,state.slotIndex,state.skillId,state.epoch)
                ||SkillChargingManager.matches(replacement,replacement.slotIndex,replacement.skillId,state.epoch)) {
            helper.fail("A delayed key-up epoch matched a replacement charging session");return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void plasmaCodecPreservesAuthoritativeState(GameTestHelper helper) {
        PlasmaOrbEntity original = new PlasmaOrbEntity(AcademyEntities.PLASMA_ORB.get(), helper.getLevel());
        CompoundTag tag = new CompoundTag();
        original.saveWithoutId(tag);
        PlasmaOrbEntity restored = new PlasmaOrbEntity(AcademyEntities.PLASMA_ORB.get(), helper.getLevel());
        restored.load(tag);
        CompoundTag roundTrip = new CompoundTag();
        restored.saveWithoutId(roundTrip);
        for (String key : List.of("DestX", "DestY", "DestZ", "Damage", "Radius",
                "Armed", "FlightTicks")) {
            if (!roundTrip.contains(key)) {
                helper.fail("Plasma entity codec lost key: " + key);
                return;
            }
        }
        if (roundTrip.contains("Owner")
                || Math.abs(roundTrip.getFloat("Damage") - 80.0f) > 0.001f
                || Math.abs(roundTrip.getFloat("Radius") - 12.0f) > 0.001f
                || roundTrip.getBoolean("Armed") || roundTrip.getInt("FlightTicks") != 0) {
            helper.fail("Plasma entity codec changed default authoritative state");
        } else {
            helper.succeed();
        }
    }

    @GameTest(template = EMPTY)
    public static void ownerlessPlasmaIsDiscarded(GameTestHelper helper) {
        PlasmaOrbEntity orb = new PlasmaOrbEntity(AcademyEntities.PLASMA_ORB.get(), helper.getLevel());
        orb.setPos(helper.absoluteVec(new Vec3(1.5, 2, 1.5)));
        orb.tick();
        if (!orb.isRemoved()) helper.fail("Ownerless plasma survived its server tick");
        else helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void syntheticLoginSkipsUnnegotiatedPayload(GameTestHelper helper) {
        ServerPlayer synthetic = helper.makeMockServerPlayerInLevel();
        try {
            if (SafePayloadSender.canSend(synthetic, FlashingStatePacket.TYPE)) {
                helper.fail("Synthetic player unexpectedly advertised the flashing payload");
                return;
            }
            FlashingSessionManager.login(new PlayerEvent.PlayerLoggedInEvent(synthetic));
            helper.succeed();
        } catch (RuntimeException exception) {
            helper.fail("Login attempted an unnegotiated payload: " + exception);
        }
    }

    @GameTest(template = EMPTY)
    public static void flashingSidewaysBasisSurvivesVerticalView(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setYRot(137);
        player.setXRot(-90);
        Vec3 left = FlashingTargeting.direction(player, 0);
        Vec3 right = FlashingTargeting.direction(player, 1);
        Vec3 forward = FlashingTargeting.direction(player, 2);
        if (Math.abs(left.length() - 1) > 1.0e-6 || Math.abs(right.length() - 1) > 1.0e-6
                || left.add(right).lengthSqr() > 1.0e-10 || Math.abs(left.y) > 1.0e-10
                || Math.abs(right.y) > 1.0e-10 || Math.abs(forward.y) < .99) {
            helper.fail("Flashing A/D collapsed or inherited pitch at a vertical view angle");
        } else {
            helper.succeed();
        }
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipLedgerSurvivesSerializationAndInvalidOwnerContext(GameTestHelper helper) {
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
        MagManipBlockEntity original=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel());
        CompoundTag payload=new CompoundTag(); payload.putString("CustomName","round6-sentinel");
        original.initialize(player,Blocks.HOPPER.defaultBlockState(),helper.absolutePos(new BlockPos(1,2,1)),payload,12);
        CompoundTag saved=new CompoundTag(); original.saveWithoutId(saved);
        saved.putUUID("Owner",UUID.randomUUID()); // model logout/restart: no attributable live owner
        saved.putString("SourceDimension","academy:wrong_dimension");
        MagManipBlockEntity restored=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel()); restored.load(saved);
        CompoundTag roundTrip=new CompoundTag(); restored.saveWithoutId(roundTrip);
        if(!roundTrip.hasUUID("Transaction")||!roundTrip.hasUUID("PermissionToken")||!roundTrip.contains("BlockEntity")
                ||!"round6-sentinel".equals(roundTrip.getCompound("BlockEntity").getString("CustomName"))){helper.fail("MagManip durable ledger lost transaction or BE payload");return;}
        restored.tick(); if(restored.isRemoved()){helper.fail("invalid owner/dimension caused lossy settlement");return;} helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void magManipTransferSanitizesPayloadAndEnforcesAllowlist(GameTestHelper helper) {
        CompoundTag source = new CompoundTag();
        source.putString("id", "minecraft:hopper");
        source.putInt("x", 12); source.putInt("y", 64); source.putInt("z", -4);
        CompoundTag item = new CompoundTag(); item.putString("id", "minecraft:diamond");
        net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag(); items.add(item);
        source.put("Items", items);

        CompoundTag clean = MagManipTransferPolicy.sanitize(source);
        if (clean.contains("id") || clean.contains("x") || clean.contains("y") || clean.contains("z")
                || !"minecraft:diamond".equals(clean.getList("Items", 10).getCompound(0).getString("id"))) {
            helper.fail("MagManip sanitizer leaked identity/coordinates or lost container payload"); return;
        }
        if (!source.contains("id") || !source.contains("x")) {
            helper.fail("MagManip sanitizer mutated the durable source payload"); return;
        }
        if (MagManipTransferPolicy.mayMove(new ChestBlockEntity(BlockPos.ZERO, Blocks.CHEST.defaultBlockState()))
                || !MagManipTransferPolicy.mayMove(new HopperBlockEntity(BlockPos.ZERO, Blocks.HOPPER.defaultBlockState()))) {
            helper.fail("MagManip movable block-entity allowlist widened or rejected hopper"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void magManipAllowedBlockEntityRoundTripsThroughEscrow(GameTestHelper helper) {
        ServerPlayer owner=helper.makeMockServerPlayerInLevel();BlockPos source=helper.absolutePos(new BlockPos(4,1,4));
        helper.getLevel().setBlock(source,Blocks.HOPPER.defaultBlockState(),3);HopperBlockEntity hopper=(HopperBlockEntity)helper.getLevel().getBlockEntity(source);hopper.setItem(0,new ItemStack(Items.DIAMOND,3));
        CompoundTag payload=MagManipTransferPolicy.capture(hopper,helper.getLevel().registryAccess());ResourceLocation type=MagManipTransferPolicy.typeId(hopper);
        helper.getLevel().setBlock(source,AcademyBlocks.MAG_MANIP_ESCROW.get().defaultBlockState(),3);
        MagManipBlockEntity carrier=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel());carrier.setPos(helper.absoluteVec(new Vec3(5.5,2,5.5)));carrier.initialize(owner,Blocks.HOPPER.defaultBlockState(),source,payload,type,8);
        if(!carrier.reserveForSpawn()||!carrier.commitSpawnReservation()||!helper.getLevel().addFreshEntity(carrier)){helper.fail("could not establish hopper material transaction");return;}
        carrier.recoverMaterial();
        if(!helper.getLevel().getBlockState(source).is(Blocks.HOPPER)||!(helper.getLevel().getBlockEntity(source) instanceof HopperBlockEntity restored)||restored.getItem(0).getCount()!=3||!restored.getItem(0).is(Items.DIAMOND)||MagManipTransactionData.get(helper.getLevel()).inspect(carrier.transactionId())!=null){helper.fail("hopper payload did not round-trip exactly or ledger remained");return;}helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void magManipInventoryAbortReturnsExactlyOneMaterial(GameTestHelper helper) {
        ServerPlayer owner=helper.makeMockServerPlayerInLevel();owner.getInventory().setItem(0,new ItemStack(Items.IRON_BLOCK,2));owner.getInventory().selected=0;
        MagManipBlockEntity carrier=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel());carrier.setPos(helper.absoluteVec(new Vec3(6.5,2,6.5)));UUID tx=UUID.randomUUID();long now=helper.getLevel().getGameTime();String item=owner.getMainHandItem().getItem()+"|"+owner.getMainHandItem().getComponents();String hash=MagManipTransactionData.sourceHash("INVENTORY",null,null,item,2);MagManipTransactionData ledger=MagManipTransactionData.get(helper.getLevel());
        if(!ledger.reserve(owner.getUUID(),tx,carrier.getUUID(),helper.getLevel().dimension().location().toString(),now)||!ledger.prepare(tx,carrier.getUUID(),carrier.blockPosition(),owner.blockPosition(),Blocks.IRON_BLOCK.defaultBlockState(),null,null,"INVENTORY",0,2,hash,now)){helper.fail("inventory transaction prepare failed");return;}
        owner.getMainHandItem().shrink(1);if(!ledger.markSourceConsumed(tx,carrier.getUUID(),hash,now)||!ledger.markActive(tx,carrier.getUUID(),now)){helper.fail("inventory consume commit failed");return;}carrier.initializeMaterial(owner,tx,Blocks.IRON_BLOCK.defaultBlockState(),owner.blockPosition(),null,null,8);helper.getLevel().addFreshEntity(carrier);carrier.recoverMaterial();
        if(owner.getInventory().countItem(Items.IRON_BLOCK)!=2||ledger.inspect(tx)!=null){helper.fail("inventory abort duplicated/lost material or retained ledger");return;}helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void magManipPlacementCancellationRetainsThenRecoversExactMaterial(GameTestHelper helper) {
        ServerPlayer owner=helper.makeMockServerPlayerInLevel();BlockPos source=helper.absolutePos(new BlockPos(7,1,7));helper.getLevel().setBlock(source,AcademyBlocks.MAG_MANIP_ESCROW.get().defaultBlockState(),3);
        MagManipBlockEntity carrier=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel());carrier.setPos(helper.absoluteVec(new Vec3(8.5,2,8.5)));carrier.initialize(owner,Blocks.IRON_BLOCK.defaultBlockState(),source,null,null,8);
        if(!carrier.reserveForSpawn()||!carrier.commitSpawnReservation()||!helper.getLevel().addFreshEntity(carrier)){helper.fail("material cancellation setup failed");return;}
        Consumer<BlockEvent.EntityPlaceEvent> deny=event->{if(event.getPos().equals(source))event.setCanceled(true);};NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class,deny);
        try{carrier.recoverMaterial();if(!carrier.isAlive()||!helper.getLevel().getBlockState(source).is(AcademyBlocks.MAG_MANIP_ESCROW.get())||MagManipTransactionData.get(helper.getLevel()).inspect(carrier.transactionId())==null){helper.fail("cancelled placement lost carrier, escrow, or ledger");return;}}finally{NeoForge.EVENT_BUS.unregister(deny);}
        carrier.recoverMaterial();if(carrier.isAlive()||!helper.getLevel().getBlockState(source).is(Blocks.IRON_BLOCK)||MagManipTransactionData.get(helper.getLevel()).inspect(carrier.transactionId())!=null){helper.fail("authorized retry did not settle exactly once");return;}helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void abilityAttachmentSyncAndPersistenceKeepTeleportLocations(GameTestHelper helper) {
        PlayerAbilityData authoritative = new PlayerAbilityData();
        PlayerAbilityData.TeleportLocation location = new PlayerAbilityData.TeleportLocation(
                "lab", "minecraft:overworld", 1.5, 64, -3.5);
        if (!authoritative.addTeleportLocation(location)) {
            helper.fail("Ability attachment rejected teleport location"); return;
        }
        PlayerAbilityData clientProjection = PlayerAbilityData.fromSyncTag(authoritative.toSyncTag());
        if (!List.of(location).equals(clientProjection.getTeleportLocations())
                || !List.of(location).equals(authoritative.getTeleportLocations())) {
            helper.fail("Ability synchronisation projection dropped or mutated teleport locations"); return;
        }
        CompoundTag saved = PlayerAbilityDataCodec.INSTANCE.write(authoritative, helper.getLevel().registryAccess());
        PlayerAbilityData reloaded = PlayerAbilityDataCodec.INSTANCE.read(null, saved, helper.getLevel().registryAccess());
        if (!List.of(location).equals(reloaded.getTeleportLocations())) {
            helper.fail("Ability attachment persistence dropped teleport locations"); return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void locationProtocolCodecsBoundCountsRejectTrailingBytesAndKeepConsent(GameTestHelper helper) {
        List<PlayerAbilityData.TeleportLocation> entries = new ArrayList<>();
        for (int i = 0; i < 40; i++) entries.add(new PlayerAbilityData.TeleportLocation(
                "p" + i, "minecraft:overworld", i, 64, 0));
        ByteBuf locations = Unpooled.buffer();
        try {
            LocationTeleportSyncPacket.STREAM_CODEC.encode(locations, new LocationTeleportSyncPacket(entries));
            LocationTeleportSyncPacket decoded = LocationTeleportSyncPacket.STREAM_CODEC.decode(locations);
            if (decoded.locations().size() != PlayerAbilityData.MAX_TELEPORT_LOCATIONS || locations.isReadable()) {
                helper.fail("Location sync codec advertised the wrong bounded count or left unread bytes"); return;
            }
        } finally { locations.release(); }

        ByteBuf trailing = Unpooled.buffer();
        try {
            LocationTeleportSyncPacket.STREAM_CODEC.encode(trailing, new LocationTeleportSyncPacket(List.of()));
            trailing.writeByte(7);
            try {
                LocationTeleportSyncPacket.STREAM_CODEC.decode(trailing);
                helper.fail("Location sync codec accepted trailing bytes"); return;
            } catch (io.netty.handler.codec.DecoderException expected) {
                // Required strict framing rejection.
            }
        } finally { trailing.release(); }

        LocationConsentResponsePacket response = new LocationConsentResponsePacket(918273645L, true);
        ByteBuf consent = Unpooled.buffer();
        try {
            LocationConsentResponsePacket.STREAM_CODEC.encode(consent, response);
            if (!response.equals(LocationConsentResponsePacket.STREAM_CODEC.decode(consent)) || consent.isReadable()) {
                helper.fail("Location consent codec lost its nonce/decision or left unread bytes"); return;
            }
        } finally { consent.release(); }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void chargingAckCarriesFullCorrelationAndExplicitRejection(GameTestHelper helper) {
        SyncChargingStatePacket accepted = new SyncChargingStatePacket(0, 80, 2, "railgun", 99, 7, true);
        SyncChargingStatePacket rejected = new SyncChargingStatePacket(-1, 0, 2, "railgun", 0, 7, false);
        if (accepted.slotIndex() != 2 || !"railgun".equals(accepted.skillId()) || !accepted.accepted()
                || rejected.slotIndex() != 2 || rejected.accepted() || rejected.epoch() != 0) {
            helper.fail("Charging acknowledgement lost correlation fields or explicit rejection"); return;
        }
        helper.succeed();
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipOfflineOwnerRecoversAtOriginAndIndexIsConstantTime(GameTestHelper helper) {
        ServerPlayer player=helper.makeMockServerPlayerInLevel(); BlockPos origin=helper.absolutePos(new BlockPos(2,2,2));
        helper.getLevel().removeBlock(origin,false);
        MagManipBlockEntity original=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel());
        original.setPos(helper.absoluteVec(new Vec3(4,3,4))); original.initialize(player,Blocks.IRON_BLOCK.defaultBlockState(),origin,null,null,7);
        if(!original.reserveForSpawn()||!original.commitSpawnReservation()||!helper.getLevel().addFreshEntity(original)){helper.fail("could not add ACTIVE MagManip ledger");return;}
        if(MagManipBlockEntity.findOwned(player)!=original){helper.fail("owner UUID index did not resolve the live ledger");return;}
        CompoundTag saved=new CompoundTag(); original.saveWithoutId(saved); original.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        saved.putUUID("Owner",UUID.randomUUID()); saved.putInt("OwnerMissingTicks",199);
        MagManipBlockEntity restored=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel()); restored.load(saved);
        helper.getLevel().addFreshEntity(restored);
        if(restored.isAlive()||!helper.getLevel().getBlockState(origin).isAir()||MagManipTransactionData.get(helper.getLevel()).inspect(original.transactionId())==null){helper.fail("invalid-owner snapshot materialized or released durable payload");return;}
        MagManipTransactionData.get(helper.getLevel()).release(original.transactionId(),original.getUUID());helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void magManipRejectsSecondLiveLedgerBeforeSourceCommit(GameTestHelper helper) {
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
        MagManipBlockEntity first=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel());
        first.initialize(player,Blocks.IRON_BLOCK.defaultBlockState(),helper.absolutePos(new BlockPos(1,2,1)),null,null,1);
        MagManipBlockEntity second=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel());
        second.initialize(player,Blocks.IRON_BLOCK.defaultBlockState(),helper.absolutePos(new BlockPos(2,2,1)),null,null,1);
        if(!first.reserveForSpawn()||second.reserveForSpawn()) { helper.fail("same owner acquired two live MagManip transaction slots"); return; }
        first.releaseSpawnReservation();
        if(!second.reserveForSpawn()) { helper.fail("released transaction slot remained orphaned"); return; }
        second.releaseSpawnReservation(); helper.succeed();
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipLoadOrderDeterministicallyQuarantinesSecondOwnerLedger(GameTestHelper helper) {
        ServerPlayer player=helper.makeMockServerPlayerInLevel(); BlockPos origin=helper.absolutePos(new BlockPos(1,2,1));
        helper.getLevel().removeBlock(origin,false);MagManipBlockEntity high=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel());
        high.initialize(player,Blocks.IRON_BLOCK.defaultBlockState(),origin,null,null,1);UUID highTx=high.transactionId();
        if(!high.reserveForSpawn()||!high.commitSpawnReservation()||!helper.getLevel().addFreshEntity(high)){helper.fail("could not establish authoritative ACTIVE carrier");return;}
        CompoundTag lowTag=new CompoundTag(); high.saveWithoutId(lowTag);lowTag.putUUID("Transaction",UUID.randomUUID());
        MagManipBlockEntity low=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel()); low.load(lowTag); low.setUUID(UUID.randomUUID());
        helper.getLevel().addFreshEntity(low);
        if(!high.isAlive()||low.isAlive()||MagManipBlockEntity.findOwned(player)!=high||MagManipTransactionData.get(helper.getLevel()).inspect(highTx)==null) { helper.fail("durable first claim was displaced by later representation"); return; }
        high.releaseSpawnReservation();high.discard();helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void magManipChunkUnloadKeepsDurableReservationAndReloadClaimsIt(GameTestHelper helper) {
        ServerPlayer player=helper.makeMockServerPlayerInLevel(); BlockPos origin=helper.absolutePos(new BlockPos(2,2,2));
        MagManipBlockEntity first=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel());
        first.initialize(player,Blocks.IRON_BLOCK.defaultBlockState(),origin,null,null,1);
        helper.getLevel().removeBlock(origin,false);
        if(!first.reserveForSpawn()||!first.commitSpawnReservation()||!helper.getLevel().addFreshEntity(first)){helper.fail("could not establish first durable transaction");return;}
        CompoundTag entityTag=new CompoundTag(); first.saveWithoutId(entityTag); UUID entityId=first.getUUID();
        first.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        MagManipBlockEntity second=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel());
        second.initialize(player,Blocks.IRON_BLOCK.defaultBlockState(),origin.offset(1,0,0),null,null,1);
        if(second.reserveForSpawn()){helper.fail("chunk unload released owner reservation and allowed a second transaction");return;}
        CompoundTag durable=new CompoundTag(); MagManipTransactionData.get(helper.getLevel()).save(durable,helper.getLevel().registryAccess());
        if(durable.getList("Entries",10).stream().noneMatch(t->((CompoundTag)t).getUUID("Transaction").equals(first.transactionId()))){helper.fail("restart serialization lost the unloaded transaction reservation");return;}
        MagManipBlockEntity reloaded=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel()); reloaded.load(entityTag); reloaded.setUUID(entityId);
        if(!helper.getLevel().addFreshEntity(reloaded)||reloaded.recoveryOnly()||MagManipBlockEntity.findOwned(player)!=reloaded){helper.fail("reloaded entity could not reclaim its exact durable transaction");return;}
        reloaded.releaseSpawnReservation();reloaded.discard(); helper.succeed();
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipReservationIsServerGlobalAndDoesNotTimeExpire(GameTestHelper helper) {
        MagManipTransactionData overworld=MagManipTransactionData.get(helper.getLevel());
        net.minecraft.server.level.ServerLevel nether=helper.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        if(nether!=null&&MagManipTransactionData.get(nether)!=overworld){helper.fail("MagManip created a dimension-local reservation table");return;}
        UUID owner=UUID.randomUUID(),transaction=UUID.randomUUID(),entity=UUID.randomUUID();
        long now=helper.getLevel().getGameTime();
        if(!overworld.reserve(owner,transaction,entity,"minecraft:the_nether",now)
                ||!overworld.reserved(owner,now+1_000_000L)){helper.fail("active unloaded reservation expired by wall-clock ticks");return;}
        CompoundTag saved=new CompoundTag(); overworld.save(saved,helper.getLevel().registryAccess());
        if(saved.getList("Entries",10).stream().noneMatch(t->((CompoundTag)t).getUUID("Transaction").equals(transaction))){helper.fail("global reservation did not survive real SavedData serialization");return;}
        overworld.release(transaction,entity);
        if(overworld.reserved(owner,now+1_000_001L)){helper.fail("explicit settlement did not clear reservation");return;}
        helper.succeed();
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipRestartedTtlLedgerRestoresExactlyOnce(GameTestHelper helper) {
        ServerPlayer player=helper.makeMockServerPlayerInLevel(); BlockPos origin=helper.absolutePos(new BlockPos(3,2,3));
        helper.getLevel().removeBlock(origin,false);
        MagManipBlockEntity seed=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel());
        seed.initialize(player,Blocks.IRON_BLOCK.defaultBlockState(),origin,null,null,1);
        if(!seed.reserveForSpawn()||!seed.commitSpawnReservation()){helper.fail("could not establish restart ledger");return;}
        CompoundTag restart=new CompoundTag(); seed.saveWithoutId(restart); restart.putUUID("Owner",UUID.randomUUID()); restart.putInt("Age",2399);
        MagManipBlockEntity restored=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel()); restored.load(restart);
        helper.getLevel().addFreshEntity(restored);
        if(restored.isAlive()||!helper.getLevel().getBlockState(origin).isAir()||MagManipTransactionData.get(helper.getLevel()).inspect(seed.transactionId())==null){helper.fail("stale TTL snapshot materialized or released payload");return;}
        seed.releaseSpawnReservation();helper.succeed();
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipArbitraryRemovalDoesNotReleaseCommittedReservation(GameTestHelper helper) {
        ServerPlayer player=helper.makeMockServerPlayerInLevel();MagManipBlockEntity entity=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel());
        entity.initialize(player,Blocks.IRON_BLOCK.defaultBlockState(),helper.absolutePos(new BlockPos(1,1,1)),null,1);entity.setPos(helper.absoluteVec(new Vec3(1.5,2,1.5)));
        if(!entity.reserveForSpawn()||!entity.commitSpawnReservation()||!helper.getLevel().addFreshEntity(entity)){helper.fail("setup failed");return;}
        UUID owner=player.getUUID();entity.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        if(!MagManipTransactionData.get(helper.getLevel()).reserved(owner,helper.getLevel().getGameTime())){helper.fail("non-terminal removal released committed reservation");return;}helper.succeed();
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipMissingChunkCannotProveOrphan(GameTestHelper helper) {
        MagManipTransactionData data=MagManipTransactionData.get(helper.getLevel());UUID owner=UUID.randomUUID(),tx=UUID.randomUUID(),entity=UUID.randomUUID();long now=helper.getLevel().getGameTime();
        if(!data.reserve(owner,tx,entity,helper.getLevel().dimension().location().toString(),now)
                ||!data.commit(tx,entity,new BlockPos(30_000_000,64,30_000_000),helper.absolutePos(new BlockPos(1,1,1)),Blocks.IRON_BLOCK.defaultBlockState(),null,null,now)
                ||data.proveOrphan(helper.getLevel(),tx,now+MagManipTransactionData.ORPHAN_GRACE_TICKS+1)){helper.fail("unloaded chunk was accepted as orphan proof");return;}
        data.release(tx,entity);helper.succeed();
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipLoadedChunkProofAndRecoveryCompletion(GameTestHelper helper) {
        MagManipTransactionData data=MagManipTransactionData.get(helper.getLevel());UUID owner=UUID.randomUUID(),tx=UUID.randomUUID(),entity=UUID.randomUUID();long now=helper.getLevel().getGameTime();BlockPos origin=helper.absolutePos(new BlockPos(2,1,2));helper.getLevel().setBlock(origin,AcademyBlocks.MAG_MANIP_ESCROW.get().defaultBlockState(),3);
        if(!data.reserve(owner,tx,entity,helper.getLevel().dimension().location().toString(),now)
                ||!data.commit(tx,entity,origin,origin,Blocks.IRON_BLOCK.defaultBlockState(),null,null,now)
                ||!data.proveOrphan(helper.getLevel(),tx,now+MagManipTransactionData.ORPHAN_GRACE_TICKS+1)
                ||data.recovery(tx)==null||!data.recover(helper.getLevel(),tx)||data.inspect(tx)==null||!"RECOVERY_ISSUED".equals(data.inspect(tx).state())||data.inspect(tx).recoveryEntity()==null||!helper.getLevel().getBlockState(origin).is(AcademyBlocks.MAG_MANIP_ESCROW.get())){
            helper.fail("loaded-chunk proof did not issue one durable recovery token while retaining escrow");return;}data.cancelRecovery(tx,tx);helper.getLevel().removeBlock(origin,false);helper.succeed();
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipAdminCancelRejectsWrongTransactionConfirmation(GameTestHelper helper) {
        MagManipTransactionData data=MagManipTransactionData.get(helper.getLevel());UUID owner=UUID.randomUUID(),tx=UUID.randomUUID(),entity=UUID.randomUUID();long now=helper.getLevel().getGameTime();BlockPos origin=helper.absolutePos(new BlockPos(3,1,3));
        data.reserve(owner,tx,entity,helper.getLevel().dimension().location().toString(),now);data.commit(tx,entity,origin,origin,Blocks.IRON_BLOCK.defaultBlockState(),null,null,now);data.proveOrphan(helper.getLevel(),tx,now+MagManipTransactionData.ORPHAN_GRACE_TICKS+1);
        if(data.cancelRecovery(tx,UUID.randomUUID())||data.inspect(tx)==null){helper.fail("wrong confirmation UUID cancelled durable data");return;}data.cancelRecovery(tx,tx);helper.succeed();
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipPreparedCrashOnlyAbortsWhenOriginalSourceMatches(GameTestHelper helper) {
        MagManipTransactionData data=MagManipTransactionData.get(helper.getLevel());UUID owner=UUID.randomUUID(),tx=UUID.randomUUID(),entity=UUID.randomUUID();long now=helper.getLevel().getGameTime();BlockPos origin=helper.absolutePos(new BlockPos(4,1,4));helper.getLevel().setBlock(origin,Blocks.IRON_BLOCK.defaultBlockState(),3);String hash=MagManipTransactionData.sourceHash("BLOCK",Blocks.IRON_BLOCK.defaultBlockState(),null,"",0);
        if(!data.reserve(owner,tx,entity,helper.getLevel().dimension().location().toString(),now)||!data.prepare(tx,entity,origin,origin,Blocks.IRON_BLOCK.defaultBlockState(),null,null,"BLOCK",-1,0,hash,now)||!data.proveOrphan(helper.getLevel(),tx,now+MagManipTransactionData.ORPHAN_GRACE_TICKS+1)||data.inspect(tx)!=null||!helper.getLevel().getBlockState(origin).is(Blocks.IRON_BLOCK)){helper.fail("PREPARED crash materialized payload or failed safe abort");return;}helper.succeed();
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipSourceConsumedCrashRecoversOnceAndIsIdempotent(GameTestHelper helper) {
        MagManipTransactionData data=MagManipTransactionData.get(helper.getLevel());UUID owner=UUID.randomUUID(),tx=UUID.randomUUID(),entity=UUID.randomUUID();long now=helper.getLevel().getGameTime();BlockPos origin=helper.absolutePos(new BlockPos(5,1,5));helper.getLevel().setBlock(origin,Blocks.IRON_BLOCK.defaultBlockState(),3);String hash=MagManipTransactionData.sourceHash("BLOCK",Blocks.IRON_BLOCK.defaultBlockState(),null,"",0);
        if(!data.reserve(owner,tx,entity,helper.getLevel().dimension().location().toString(),now)||!data.prepare(tx,entity,origin,origin,Blocks.IRON_BLOCK.defaultBlockState(),null,null,"BLOCK",-1,0,hash,now)){helper.fail("prepare failed");return;}helper.getLevel().setBlock(origin,AcademyBlocks.MAG_MANIP_ESCROW.get().defaultBlockState(),3);
        if(!data.markSourceConsumed(tx,entity,hash,now)||!data.proveOrphan(helper.getLevel(),tx,now+MagManipTransactionData.ORPHAN_GRACE_TICKS+1)||!data.recover(helper.getLevel(),tx)){helper.fail("SOURCE_CONSUMED recovery did not issue");return;}UUID token=data.inspect(tx).recoveryEntity();
        if(token==null||!data.recover(helper.getLevel(),tx)||!token.equals(data.inspect(tx).recoveryEntity())||!helper.getLevel().getBlockState(origin).is(AcademyBlocks.MAG_MANIP_ESCROW.get())){helper.fail("SOURCE_CONSUMED recovery was not idempotent or lost escrow before owner pickup");return;}data.cancelRecovery(tx,tx);helper.getLevel().removeBlock(origin,false);helper.succeed();
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipFailedItemEmissionKeepsRecoveryRequired(GameTestHelper helper) {
        MagManipTransactionData data=MagManipTransactionData.get(helper.getLevel());UUID owner=UUID.randomUUID(),tx=UUID.randomUUID(),entity=UUID.randomUUID();long now=helper.getLevel().getGameTime();BlockPos origin=helper.absolutePos(new BlockPos(6,1,6));String hash=MagManipTransactionData.sourceHash("BLOCK",Blocks.IRON_BLOCK.defaultBlockState(),null,"",0);
        helper.getLevel().setBlock(origin,AcademyBlocks.MAG_MANIP_ESCROW.get().defaultBlockState(),3);data.reserve(owner,tx,entity,helper.getLevel().dimension().location().toString(),now);data.prepare(tx,entity,origin,origin,Blocks.IRON_BLOCK.defaultBlockState(),null,null,"BLOCK",-1,0,hash,now);data.markSourceConsumed(tx,entity,hash,now);data.proveOrphan(helper.getLevel(),tx,now+MagManipTransactionData.ORPHAN_GRACE_TICKS+1);
        MagManipTransactionData.Entry before=data.inspect(tx);if(before==null||data.emitRecoveryItem(helper.getLevel(),tx,origin,net.minecraft.world.item.ItemStack.EMPTY)||data.inspect(tx)==null||!"RECOVERY_REQUIRED".equals(data.inspect(tx).state())){helper.fail("failed/empty item insertion released or advanced recovery row");return;}data.cancelRecovery(tx,tx);helper.succeed();
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipExactCarrierClaimIsActivePhaseOnly(GameTestHelper helper) {
        MagManipTransactionData data=MagManipTransactionData.get(helper.getLevel());UUID owner=UUID.randomUUID(),tx=UUID.randomUUID(),entity=UUID.randomUUID();long now=helper.getLevel().getGameTime();BlockPos origin=helper.absolutePos(new BlockPos(7,1,7));String dim=helper.getLevel().dimension().location().toString();String hash=MagManipTransactionData.sourceHash("BLOCK",Blocks.IRON_BLOCK.defaultBlockState(),null,"",0);
        if(!data.reserve(owner,tx,entity,dim,now)||data.claim(owner,tx,entity,dim,now)){helper.fail("RESERVED carrier claimed ACTIVE authority");return;}
        if(!data.prepare(tx,entity,origin,origin,Blocks.IRON_BLOCK.defaultBlockState(),null,null,"BLOCK",-1,0,hash,now)||data.claim(owner,tx,entity,dim,now)){helper.fail("PREPARED carrier claimed ACTIVE authority");return;}
        if(!data.markSourceConsumed(tx,entity,hash,now)||data.claim(owner,tx,entity,dim,now)){helper.fail("SOURCE_CONSUMED carrier claimed before activation");return;}
        if(!data.markActive(tx,entity,now)||!data.claim(owner,tx,entity,dim,now)||data.claim(owner,tx,UUID.randomUUID(),dim,now)){helper.fail("ACTIVE exact representation/generation claim invariant failed");return;}
        data.release(tx,entity);helper.succeed();
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipRecoveryOutboxReissuesSameTokenAndPickupSettles(GameTestHelper helper) {
        MagManipTransactionData data=MagManipTransactionData.get(helper.getLevel());ServerPlayer owner=helper.makeMockServerPlayerInLevel();UUID tx=UUID.randomUUID(),carrier=UUID.randomUUID();long now=helper.getLevel().getGameTime();BlockPos origin=helper.absolutePos(new BlockPos(8,1,8));helper.getLevel().setBlock(origin,AcademyBlocks.MAG_MANIP_ESCROW.get().defaultBlockState(),3);String hash=MagManipTransactionData.sourceHash("BLOCK",Blocks.IRON_BLOCK.defaultBlockState(),null,"",0);
        data.reserve(owner.getUUID(),tx,carrier,helper.getLevel().dimension().location().toString(),now);data.prepare(tx,carrier,origin,origin,Blocks.IRON_BLOCK.defaultBlockState(),null,null,"BLOCK",-1,0,hash,now);data.markSourceConsumed(tx,carrier,hash,now);data.proveOrphan(helper.getLevel(),tx,now+MagManipTransactionData.ORPHAN_GRACE_TICKS+1);
        if(!data.recover(helper.getLevel(),tx)){helper.fail("recovery outbox did not issue");return;}MagManipTransactionData.Entry issued=data.inspect(tx);UUID token=issued==null?null:issued.recoveryEntity();
        if(token==null||!"RECOVERY_ISSUED".equals(issued.state())||!data.recover(helper.getLevel(),tx)||!token.equals(data.inspect(tx).recoveryEntity())){helper.fail("recovery retry changed token/generation");return;}
        net.minecraft.world.entity.Entity representation=helper.getLevel().getEntity(token);if(!(representation instanceof net.minecraft.world.entity.item.ItemEntity item)||!data.acknowledgePickup(owner,token,item.getItem())){helper.fail("exact owner pickup did not settle issued token");return;}
        if(data.inspect(tx)!=null){helper.fail("pickup acknowledgement left durable row");return;}item.discard();helper.succeed();
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipCrashSnapshotDistinguishesOriginalFromEscrow(GameTestHelper helper) {
        MagManipTransactionData data=MagManipTransactionData.get(helper.getLevel());UUID owner=UUID.randomUUID(),tx=UUID.randomUUID(),carrier=UUID.randomUUID();long now=helper.getLevel().getGameTime();BlockPos origin=helper.absolutePos(new BlockPos(9,1,9));String dim=helper.getLevel().dimension().location().toString();String hash=MagManipTransactionData.sourceHash("BLOCK",Blocks.IRON_BLOCK.defaultBlockState(),null,"",0);
        helper.getLevel().setBlock(origin,Blocks.IRON_BLOCK.defaultBlockState(),3);data.reserve(owner,tx,carrier,dim,now);data.prepare(tx,carrier,origin,origin,Blocks.IRON_BLOCK.defaultBlockState(),null,null,"BLOCK",-1,0,hash,now);
        if(!data.proveOrphan(helper.getLevel(),tx,now+MagManipTransactionData.ORPHAN_GRACE_TICKS+1)||data.inspect(tx)!=null){helper.fail("original-source snapshot did not abort PREPARED");return;}
        UUID tx2=UUID.randomUUID();data.reserve(owner,tx2,carrier,dim,now);data.prepare(tx2,carrier,origin,origin,Blocks.IRON_BLOCK.defaultBlockState(),null,null,"BLOCK",-1,0,hash,now);helper.getLevel().setBlock(origin,AcademyBlocks.MAG_MANIP_ESCROW.get().defaultBlockState(),3);
        if(!data.proveOrphan(helper.getLevel(),tx2,now+MagManipTransactionData.ORPHAN_GRACE_TICKS+1)||data.recovery(tx2)==null){helper.fail("escrow snapshot did not promote to recovery");return;}data.cancelRecovery(tx2,tx2);helper.getLevel().removeBlock(origin,false);helper.succeed();
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipLateMiningCannotCreateConsumeProof(GameTestHelper helper) {
        MagManipTransactionData data=MagManipTransactionData.get(helper.getLevel());UUID owner=UUID.randomUUID(),tx=UUID.randomUUID(),carrier=UUID.randomUUID();long now=helper.getLevel().getGameTime();BlockPos origin=helper.absolutePos(new BlockPos(10,1,10));String hash=MagManipTransactionData.sourceHash("BLOCK",Blocks.IRON_BLOCK.defaultBlockState(),null,"",0);
        helper.getLevel().setBlock(origin,Blocks.IRON_BLOCK.defaultBlockState(),3);data.reserve(owner,tx,carrier,helper.getLevel().dimension().location().toString(),now);data.prepare(tx,carrier,origin,origin,Blocks.IRON_BLOCK.defaultBlockState(),null,null,"BLOCK",-1,0,hash,now);data.markSourceConsumed(tx,carrier,hash,now);helper.getLevel().removeBlock(origin,false);
        if(data.proveOrphan(helper.getLevel(),tx,now+MagManipTransactionData.ORPHAN_GRACE_TICKS+1)){helper.fail("late air observation authorized recovery");return;}data.release(tx,carrier);helper.succeed();
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipNonOwnerPickupIsRejectedBeforeTransfer(GameTestHelper helper) {
        MagManipTransactionData data=MagManipTransactionData.get(helper.getLevel());ServerPlayer owner=helper.makeMockServerPlayerInLevel(),attacker=helper.makeMockServerPlayerInLevel();UUID tx=UUID.randomUUID(),carrier=UUID.randomUUID();long now=helper.getLevel().getGameTime();BlockPos origin=helper.absolutePos(new BlockPos(11,1,11));String hash=MagManipTransactionData.sourceHash("BLOCK",Blocks.IRON_BLOCK.defaultBlockState(),null,"",0);helper.getLevel().setBlock(origin,AcademyBlocks.MAG_MANIP_ESCROW.get().defaultBlockState(),3);
        data.reserve(owner.getUUID(),tx,carrier,helper.getLevel().dimension().location().toString(),now);data.prepare(tx,carrier,origin,origin,Blocks.IRON_BLOCK.defaultBlockState(),null,null,"BLOCK",-1,0,hash,now);data.markSourceConsumed(tx,carrier,hash,now);data.proveOrphan(helper.getLevel(),tx,now+MagManipTransactionData.ORPHAN_GRACE_TICKS+1);data.recover(helper.getLevel(),tx);ItemEntity token=(ItemEntity)helper.getLevel().getEntity(data.inspect(tx).recoveryEntity());
        if(token==null||data.mayPickupRecovery(attacker,token)||!data.mayPickupRecovery(owner,token)){helper.fail("pre-pickup owner gate failed");return;}token.discard();data.cancelRecovery(tx,tx);helper.getLevel().removeBlock(origin,false);helper.succeed();
    }

    @Deprecated // Retired materializing-transaction fixture; intentionally not registered.
    public static void magManipDespawnReissuesSameTokenAndOwnerAckClearsEscrow(GameTestHelper helper) {
        MagManipTransactionData data=MagManipTransactionData.get(helper.getLevel());ServerPlayer owner=helper.makeMockServerPlayerInLevel();UUID tx=UUID.randomUUID(),carrier=UUID.randomUUID();long now=helper.getLevel().getGameTime();BlockPos origin=helper.absolutePos(new BlockPos(12,1,12));String hash=MagManipTransactionData.sourceHash("BLOCK",Blocks.IRON_BLOCK.defaultBlockState(),null,"",0);helper.getLevel().setBlock(origin,AcademyBlocks.MAG_MANIP_ESCROW.get().defaultBlockState(),3);
        data.reserve(owner.getUUID(),tx,carrier,helper.getLevel().dimension().location().toString(),now);data.prepare(tx,carrier,origin,origin,Blocks.IRON_BLOCK.defaultBlockState(),null,null,"BLOCK",-1,0,hash,now);data.markSourceConsumed(tx,carrier,hash,now);data.proveOrphan(helper.getLevel(),tx,now+MagManipTransactionData.ORPHAN_GRACE_TICKS+1);data.recover(helper.getLevel(),tx);UUID tokenId=data.inspect(tx).recoveryEntity();ItemEntity first=(ItemEntity)helper.getLevel().getEntity(tokenId);first.discard();data.retryIssued(helper.getLevel());ItemEntity second=(ItemEntity)helper.getLevel().getEntity(tokenId);
        if(second==null||!data.acknowledgePickup(owner,tokenId,second.getItem())||data.inspect(tx)!=null||!helper.getLevel().getBlockState(origin).isAir()){helper.fail("despawn retry or pickup escrow cleanup failed");return;}second.discard();helper.succeed();
    }

    @Deprecated // Projection-only behavior was intentionally retired when material parity returned.
    public static void magManipProjectionLeavesSourceAndInventoryUntouched(GameTestHelper helper) {
        ServerPlayer owner=helper.makeMockServerPlayerInLevel();BlockPos source=helper.absolutePos(new BlockPos(2,1,2));helper.getLevel().setBlock(source,Blocks.IRON_BLOCK.defaultBlockState(),3);
        int inventoryCount=owner.getInventory().getContainerSize();MagManipBlockEntity projection=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel());projection.initializeProjection(owner,helper.getLevel().getBlockState(source),8);
        if(!helper.getLevel().getBlockState(source).is(Blocks.IRON_BLOCK)||owner.getInventory().getContainerSize()!=inventoryCount||projection.shouldBeSaved()){helper.fail("projection changed its source/inventory or became persistent");return;}helper.succeed();
    }

    @Deprecated // Projection-only behavior was intentionally retired when material parity returned.
    public static void magManipProjectionCollisionCannotDropOrPlace(GameTestHelper helper) {
        ServerPlayer owner=helper.makeMockServerPlayerInLevel();BlockPos source=helper.absolutePos(new BlockPos(3,1,3));helper.getLevel().setBlock(source,Blocks.IRON_BLOCK.defaultBlockState(),3);net.minecraft.world.phys.AABB area=new net.minecraft.world.phys.AABB(source).inflate(32);long before=helper.getLevel().getEntitiesOfClass(ItemEntity.class,area).size();
        MagManipBlockEntity projection=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel());projection.initializeProjection(owner,Blocks.IRON_BLOCK.defaultBlockState(),8);projection.setPos(helper.absoluteVec(new Vec3(3.5,3,3.5)));projection.claim();helper.getLevel().addFreshEntity(projection);projection.throwFrom(owner,1);for(int i=0;i<450&&projection.isAlive();i++)projection.tick();long after=helper.getLevel().getEntitiesOfClass(ItemEntity.class,area).size();
        if(!helper.getLevel().getBlockState(source).is(Blocks.IRON_BLOCK)||before!=after||projection.isAlive()){helper.fail("projection placed/dropped material or survived terminal collision TTL");return;}helper.succeed();
    }

    @Deprecated // Projection-only behavior was intentionally retired when material parity returned.
    public static void magManipRejectsConcurrentProjectionAndBlockEntityPayload(GameTestHelper helper) {
        ServerPlayer owner=helper.makeMockServerPlayerInLevel();MagManipBlockEntity first=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel()),second=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel());first.initializeProjection(owner,Blocks.IRON_BLOCK.defaultBlockState(),8);second.initializeProjection(owner,Blocks.IRON_BLOCK.defaultBlockState(),8);
        if(!first.claim()||second.claim()){helper.fail("one owner claimed concurrent projections");return;}first.releaseUnspawnedClaim();first.discard();second.discard();CompoundTag be=new CompoundTag();be.putString("Items","forbidden");MagManipBlockEntity compatibility=new MagManipBlockEntity(AcademyEntities.MAG_MANIP_BLOCK.get(),helper.getLevel());compatibility.initialize(owner,Blocks.HOPPER.defaultBlockState(),BlockPos.ZERO,be,8);CompoundTag save=new CompoundTag();
        compatibility.saveWithoutId(save);
        if(save.contains("BlockEntity")){helper.fail("BE payload entered projection persistence");return;}helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void magManipLegacyLedgerCleanupNeverMaterializes(GameTestHelper helper) {
        MagManipTransactionData data=MagManipTransactionData.get(helper.getLevel());UUID owner=UUID.randomUUID(),tx=UUID.randomUUID(),carrier=UUID.randomUUID();data.reserve(owner,tx,carrier,helper.getLevel().dimension().location().toString(),helper.getLevel().getGameTime());net.minecraft.world.phys.AABB area=new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(32);long before=helper.getLevel().getEntitiesOfClass(ItemEntity.class,area).size();int purged=data.purgeLegacyRepresentations(helper.getLevel());long after=helper.getLevel().getEntitiesOfClass(ItemEntity.class,area).size();
        if(purged!=0||data.inspect(tx)==null||before!=after){helper.fail("unproven legacy row was deleted or materialized an item");return;}data.release(tx,carrier);helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void magManipMigrationRetainsUnloadedAndPartialRows(GameTestHelper helper) {
        MagManipTransactionData data=MagManipTransactionData.get(helper.getLevel());long now=helper.getLevel().getGameTime();UUID owner=UUID.randomUUID(),tx=UUID.randomUUID(),carrier=UUID.randomUUID();BlockPos far=new BlockPos(29_000_000,64,29_000_000);String hash=MagManipTransactionData.sourceHash("BLOCK",Blocks.IRON_BLOCK.defaultBlockState(),null,"",0);
        data.reserve(owner,tx,carrier,helper.getLevel().dimension().location().toString(),now);data.prepare(tx,carrier,far,far,Blocks.IRON_BLOCK.defaultBlockState(),null,null,"BLOCK",-1,0,hash,now);MagManipTransactionData.MigrationResult first=data.migrateLoadedRepresentations(helper.getLevel(),16),second=data.migrateLoadedRepresentations(helper.getLevel(),16);
        if(first.cleaned()!=0||second.cleaned()!=0||data.inspect(tx)==null){helper.fail("unloaded migration row was not restart/idempotence safe");return;}data.release(tx,carrier);helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void magManipMigrationCleansOnlyFullyObservedRowIdempotently(GameTestHelper helper) {
        MagManipTransactionData data=MagManipTransactionData.get(helper.getLevel());long now=helper.getLevel().getGameTime();UUID owner=UUID.randomUUID(),tx=UUID.randomUUID(),carrier=UUID.randomUUID();BlockPos source=helper.absolutePos(new BlockPos(13,1,13)),entityPos=helper.absolutePos(new BlockPos(12,2,12));helper.getLevel().setBlock(source,AcademyBlocks.MAG_MANIP_ESCROW.get().defaultBlockState(),3);String hash=MagManipTransactionData.sourceHash("BLOCK",Blocks.IRON_BLOCK.defaultBlockState(),null,"",0);
        data.reserve(owner,tx,carrier,helper.getLevel().dimension().location().toString(),now);data.prepare(tx,carrier,entityPos,source,Blocks.IRON_BLOCK.defaultBlockState(),null,null,"BLOCK",-1,0,hash,now);MagManipTransactionData.MigrationResult first=data.migrateLoadedRepresentations(helper.getLevel(),16),second=data.migrateLoadedRepresentations(helper.getLevel(),16);
        if(first.cleaned()!=0||second.cleaned()!=0||data.inspect(tx)==null||!helper.getLevel().getBlockState(source).is(AcademyBlocks.MAG_MANIP_ESCROW.get())){helper.fail("material ledger migration deleted an authoritative row or escrow");return;}data.release(tx,carrier);helper.getLevel().removeBlock(source,false);helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void magManipMigrationBudgetEventuallyVisitsCleanableTail(GameTestHelper helper) {
        MagManipTransactionData data=MagManipTransactionData.get(helper.getLevel());long now=helper.getLevel().getGameTime();java.util.List<UUID> pending=new ArrayList<>();
        for(int i=0;i<16;i++){UUID owner=new UUID(40,i),tx=new UUID(40,i),carrier=new UUID(41,i);pending.add(tx);data.reserve(owner,tx,carrier,"academy:missing_dimension",now);}
        UUID owner=new UUID(50,0),tx=new UUID(50,0),carrier=new UUID(51,0);BlockPos source=helper.absolutePos(new BlockPos(14,1,14)),entityPos=helper.absolutePos(new BlockPos(14,2,14));String hash=MagManipTransactionData.sourceHash("BLOCK",Blocks.IRON_BLOCK.defaultBlockState(),null,"",0);helper.getLevel().setBlock(source,AcademyBlocks.MAG_MANIP_ESCROW.get().defaultBlockState(),3);data.reserve(owner,tx,carrier,helper.getLevel().dimension().location().toString(),now);data.prepare(tx,carrier,entityPos,source,Blocks.IRON_BLOCK.defaultBlockState(),null,null,"BLOCK",-1,0,hash,now);
        int cleaned=0;for(int pass=0;pass<3;pass++)cleaned+=data.migrateLoadedRepresentations(helper.getLevel(),16).cleaned();CompoundTag saved=new CompoundTag();data.save(saved,helper.getLevel().registryAccess());
        if(cleaned!=0||data.inspect(tx)==null){helper.fail("retired migration deleted a live material transaction");return;}data.release(tx,carrier);helper.getLevel().removeBlock(source,false);for(int i=0;i<pending.size();i++)data.release(pending.get(i),new UUID(41,i));helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void magManipMigrationSchemaDistinguishesKnownChunkZero(GameTestHelper helper) {
        MagManipTransactionData data=MagManipTransactionData.get(helper.getLevel());UUID owner=UUID.randomUUID(),tx=UUID.randomUUID(),carrier=UUID.randomUUID();long now=helper.getLevel().getGameTime();BlockPos zeroChunkEntity=new BlockPos(1,64,1),source=helper.absolutePos(new BlockPos(15,1,15));String hash=MagManipTransactionData.sourceHash("BLOCK",Blocks.IRON_BLOCK.defaultBlockState(),null,"",0);data.reserve(owner,tx,carrier,helper.getLevel().dimension().location().toString(),now);data.prepare(tx,carrier,zeroChunkEntity,source,Blocks.IRON_BLOCK.defaultBlockState(),null,null,"BLOCK",-1,0,hash,now);CompoundTag saved=new CompoundTag();data.save(saved,helper.getLevel().registryAccess());
        CompoundTag row=saved.getList("Entries",10).stream().map(t->(CompoundTag)t).filter(t->t.getUUID("Transaction").equals(tx)).findFirst().orElse(null);if(row==null||row.getLong("EntityChunk")!=0L||!row.getBoolean("EntityChunkKnown")){helper.fail("known chunk (0,0) collapsed into missing legacy evidence");return;}data.release(tx,carrier);helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void railgunUnbreakableWallTruncatesAuthoritativeReach(GameTestHelper helper) {
        ServerPlayer player=helper.makeMockServerPlayerInLevel(); BlockPos wall=helper.absolutePos(new BlockPos(3,2,1)); helper.getLevel().setBlock(wall,Blocks.BEDROCK.defaultBlockState(),3);
        Vec3 start=new Vec3(wall.getX()-2.5,wall.getY()+.5,wall.getZ()+.5);
        double reach=RailgunEffect.traceBarrier(helper.getLevel(),player,start,new Vec3(1,0,0),45,2000);
        if(reach>=3){helper.fail("Railgun authoritative traversal passed an unbreakable wall");return;} helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void mineDetectMaximumPayloadCodecIsBounded(GameTestHelper helper) {
        List<MineDetectResultPacket.Entry> entries=new ArrayList<>(8400);
        for(int i=0;i<8400;i++)entries.add(new MineDetectResultPacket.Entry(new BlockPos(i,i&255,-i),i%4));
        MineDetectResultPacket packet=new MineDetectResultPacket(List.copyOf(entries),28.0f);
        long started=System.nanoTime();
        for(int round=0;round<50;round++){
            ByteBuf buffer=Unpooled.buffer(75606);
            try{
                MineDetectResultPacket.STREAM_CODEC.encode(buffer,packet);
                if(buffer.readableBytes()!=75606){helper.fail("MineDetect maximum payload was "+buffer.readableBytes()+" bytes, expected 75606");return;}
                if(MineDetectResultPacket.STREAM_CODEC.decode(buffer).entries().size()!=8400){helper.fail("MineDetect maximum payload lost entries");return;}
            }finally{buffer.release();}
        }
        long elapsedMillis=(System.nanoTime()-started)/1_000_000;
        if(elapsedMillis>=2000){helper.fail("50 maximum MineDetect codec round trips took "+elapsedMillis+" ms");return;}
        System.out.println("[AcademyCraft perf] MineDetect codec: 50 x 8400-entry round trips in "
                +elapsedMillis+" ms (75606 bytes each)");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void skillExpEventsAreOwnerAwareOrderedAndClamped(GameTestHelper helper) {
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data=player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setCurrentAbility(com.mohistmc.academy.skill.AbilityCategory.ELECTROMASTER);
        var skill=SkillRegistry.getSkill("arc_gen");
        if(skill==null){helper.fail("arc_gen missing");return;}
        data.learnSkill(skill.getId()); data.setProficiency(skill.getId(),.999f);
        List<String> order=new ArrayList<>();
        Consumer<AbilityEvents.SkillExpChanged> changed=e->{if(e.player==player){
            if(e.skill!=skill||e.oldExp!=.999f||e.exp!=1f)throw new AssertionError("changed fields");order.add("changed");}};
        Consumer<AbilityEvents.SkillExpAdded> added=e->{if(e.player==player){
            if(e.skill!=skill||e.amount!=.5f||e.oldExp!=.999f||e.exp!=1f)throw new AssertionError("added fields");order.add("added");}};
        NeoForge.EVENT_BUS.addListener(AbilityEvents.SkillExpChanged.class,changed);
        NeoForge.EVENT_BUS.addListener(AbilityEvents.SkillExpAdded.class,added);
        try{
            if(!AbilityMutationService.addSkillExp(player,data,skill.getId(),.5f)||!order.equals(List.of("changed","added"))){helper.fail("event order/commit mismatch");return;}
            if(AbilityMutationService.addSkillExp(player,new PlayerAbilityData(),skill.getId(),.1f)||order.size()!=2){helper.fail("foreign data emitted authoritative event");return;}
        }finally{NeoForge.EVENT_BUS.unregister(changed);NeoForge.EVENT_BUS.unregister(added);}
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void legacySkillRegistrationOrderMatchesFinal112(GameTestHelper helper) {
        List<List<String>> actual = List.of(
                legacySkillIds(com.mohistmc.academy.skill.AbilityCategory.ELECTROMASTER),
                legacySkillIds(com.mohistmc.academy.skill.AbilityCategory.MELTDOWNER),
                legacySkillIds(com.mohistmc.academy.skill.AbilityCategory.TELEPORTER),
                legacySkillIds(com.mohistmc.academy.skill.AbilityCategory.VECMANIP));
        List<List<String>> expected = List.of(
                List.of("arc_gen", "charging", "mag_movement", "mag_manip", "mine_detect",
                        "body_intensify", "thunder_bolt", "railgun", "thunder_clap",
                        "brain_course", "brain_course_advanced", "mind_course"),
                List.of("electron_bomb", "rad_intensify", "scatter_bomb", "light_shield",
                        "meltdowner", "mine_ray_basic", "ray_barrage", "jet_engine", "mine_ray_expert",
                        "mine_ray_luck", "electron_missile", "brain_course", "brain_course_advanced", "mind_course"),
                List.of("threatening_teleport", "dim_folding_theorem", "penetrate_teleport",
                        "mark_teleport", "flesh_ripping", "location_teleport", "shift_tp", "space_fluct",
                        "flashing", "brain_course", "brain_course_advanced", "mind_course"),
                List.of("dir_shock", "ground_shock", "vec_accel", "vec_deviation", "dir_blast",
                        "storm_wing", "blood_retro", "vec_reflection", "plasma_cannon",
                        "brain_course", "brain_course_advanced", "mind_course"));
        if (!actual.equals(expected)) {
            helper.fail("runtime skill order diverged from final 1.12.2; expected=" + expected + ", actual=" + actual);
            return;
        }
        helper.succeed();
    }

    private static List<String> legacySkillIds(com.mohistmc.academy.skill.AbilityCategory category) {
        return SkillRegistry.getSkillsByCategory(category).stream()
                .map(com.mohistmc.academy.skill.Skill::getId)
                .toList();
    }

    @GameTest(template = EMPTY)
    public static void legacyAimCommandsMutateAuthoritativePlayerData(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        var deniedSource = player.createCommandSourceStack().withPermission(0);
        var source = player.createCommandSourceStack().withPermission(4);
        var commands = helper.getLevel().getServer().getCommands();

        commands.performPrefixedCommand(deniedSource, "aim cat meltdowner");
        if (data.hasAbility()) {
            helper.fail("permission level 0 executed an administrator ability mutation");
            return;
        }

        commands.performPrefixedCommand(source, "aim cat electromaster");
        if (data.getCurrentAbility() != com.mohistmc.academy.skill.AbilityCategory.ELECTROMASTER
                || data.getPlayerLevel() != 1) {
            helper.fail("/aim cat did not commit category and Level 1 to the authoritative attachment");
            return;
        }

        int categoryIndex = new ArrayList<>(com.mohistmc.academy.skill.AbilityCategory.all())
                .indexOf(com.mohistmc.academy.skill.AbilityCategory.ELECTROMASTER);
        commands.performPrefixedCommand(source, "aim reset");
        commands.performPrefixedCommand(source, "aim cat #" + categoryIndex);
        if (data.getCurrentAbility() != com.mohistmc.academy.skill.AbilityCategory.ELECTROMASTER) {
            helper.fail("copyable #category index from /aim catlist was not accepted");
            return;
        }

        int arcIndex = SkillRegistry.getSkillsByCategory(data.getCurrentAbility()).stream()
                .map(com.mohistmc.academy.skill.Skill::getId).toList().indexOf("arc_gen");
        commands.performPrefixedCommand(source, "aim learn #" + arcIndex);
        if (!data.getLearnedSkills().contains("arc_gen")) {
            helper.fail("copyable #skill index from /aim skills was not accepted");
            return;
        }
        commands.performPrefixedCommand(source, "aim unlearn #" + arcIndex);
        if (data.getLearnedSkills().contains("arc_gen")) {
            helper.fail("#skill index did not address the same skill for unlearn");
            return;
        }

        commands.performPrefixedCommand(source, "aim learn arc_gen");
        commands.performPrefixedCommand(source, "aim exp arc_gen 0.75");
        commands.performPrefixedCommand(source, "aimp @s level 5");
        commands.performPrefixedCommand(source, "aim exp arc_gen 2");
        commands.performPrefixedCommand(source, "aim level 6");
        if (!data.getLearnedSkills().contains("arc_gen")
                || Math.abs(data.getProficiency("arc_gen") - 0.75f) > 0.0001f
                || data.getPlayerLevel() != 5) {
            helper.fail("legacy learn/exp/targeted level commands did not commit exact values");
            return;
        }

        float baseMaxCp = data.getMaxCp();
        commands.performPrefixedCommand(source, "aim learn brain_course");
        if (data.getMaxCp() != baseMaxCp + 1000) {
            helper.fail("course command did not install its persistent bonus exactly once");
            return;
        }
        commands.performPrefixedCommand(source, "aim unlearn brain_course");
        if (data.getMaxCp() != baseMaxCp) {
            helper.fail("course unlearn did not remove its persistent bonus");
            return;
        }

        data.setCurrentCp(1);
        data.setCurrentOverload(20);
        data.setCooldown("arc_gen", 100);
        commands.performPrefixedCommand(source, "aim fullcp");
        commands.performPrefixedCommand(source, "aim cd_clear");
        commands.performPrefixedCommand(source, "aim maxout");
        if (data.getCurrentCp() != data.getMaxCp() || data.getCurrentOverload() != 0
                || data.isOnCooldown("arc_gen") || data.getProficiency("arc_gen") != 0.75f
                || data.getLevelProgress() != 1.0f) {
            // 1.0.7 maxout fills only the independent level-development
            // gauge; it must not overwrite per-skill proficiency.
            helper.fail("resource/cooldown/maxout commands did not preserve their 1.0.7 boundaries");
            return;
        }

        commands.performPrefixedCommand(source, "aim devmode on");
        if (!data.isDevMode()) {
            helper.fail("explicit devmode on failed");
            return;
        }
        commands.performPrefixedCommand(source, "aim devmode off");
        commands.performPrefixedCommand(source, "aim unlearn arc_gen");
        if (data.isDevMode() || data.getLearnedSkills().contains("arc_gen")) {
            helper.fail("explicit devmode off or unlearn failed");
            return;
        }

        commands.performPrefixedCommand(source, "aim reset");
        if (data.hasAbility() || data.getPlayerLevel() != 0 || !data.getLearnedSkills().isEmpty()) {
            helper.fail("/aim reset left ability data behind");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void legacyAcachCommandAwardsGeneratedAdvancementWithOldId(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var commands = helper.getLevel().getServer().getCommands();
        var advancement = helper.getLevel().getServer().getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath("academy", "legacy/electromaster/lv1"));
        if (advancement == null) {
            helper.fail("generated legacy advancement is absent");
            return;
        }
        commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(0),
                "acach electromaster.lv1");
        if (player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            helper.fail("permission level 0 executed /acach");
            return;
        }
        commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(4),
                "acach electromaster.lv1");
        if (!player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            helper.fail("old dotted /acach id did not award its generated advancement");
            return;
        }
        // The 1.0.7 command still reported success for an already-earned id;
        // executing twice must remain harmless and keep the criterion complete.
        commands.performPrefixedCommand(player.createCommandSourceStack().withPermission(4),
                "acach electromaster.lv1");
        if (!player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            helper.fail("idempotent /acach execution revoked progress");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void legacyTutorialItemIsGrantedExactlyOnceOnLogin(GameTestHelper helper) {
        if (!ACConfig.Server.giveCloudTerminal()) {
            helper.fail("default legacy generic.giveCloudTerminal is not enabled");
            return;
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setTutorialItemGranted(false);
        AABB area = player.getBoundingBox().inflate(4.0);
        // NeoForge's mock players from concurrent GameTests share a small spawn
        // area instead of each template's transformed X/Z.  Compare the
        // synchronous delta so tutorial drops belonging to other fixtures do
        // not make this per-player idempotency check order-dependent.
        long before = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area,
                entity -> entity.getItem().is(AcademyItems.TUTORIAL.get())).size();
        var listener = new ServerListener();
        listener.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        listener.onPlayerLogin(new PlayerEvent.PlayerLoggedInEvent(player));
        long after = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area,
                entity -> entity.getItem().is(AcademyItems.TUTORIAL.get())).size();
        if (!data.isTutorialItemGranted() || after - before != 1) {
            helper.fail("legacy first-login tutorial item delta was not exactly one: " + (after - before));
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void dedicatedPlayersCannotMutateSingleplayerSettings(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        boolean originalPvp = ACConfig.Server.PVP_ENABLED.get();
        boolean originalDestroy = ACConfig.Server.DESTROY_BLOCKS.get();
        if (SettingsConfigPacket.apply(player, SettingsConfigPacket.PVP, !originalPvp)
                || SettingsConfigPacket.apply(player, SettingsConfigPacket.DESTROY_BLOCKS, !originalDestroy)
                || ACConfig.Server.PVP_ENABLED.get() != originalPvp
                || ACConfig.Server.DESTROY_BLOCKS.get() != originalDestroy) {
            helper.fail("a dedicated-server player mutated legacy singleplayer-only settings");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void legacyMediaIsNonStackableConsumedOnceAndRejectsDuplicates(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        data.setTerminalInstalled(true);
        data.installApp(com.mohistmc.academy.terminal.AppRegistry.MEDIA_PLAYER);
        if (AcademyItems.MEDIA_ONLY_MY_RAILGUN.get().getDefaultMaxStackSize() != 1) {
            helper.fail("legacy media item became stackable");
            return;
        }

        ItemStack first = new ItemStack(AcademyItems.MEDIA_ONLY_MY_RAILGUN.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, first);
        AcademyItems.MEDIA_ONLY_MY_RAILGUN.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        if (!data.hasLoadedMedia("only_my_railgun") || !first.isEmpty()) {
            helper.fail("first media load did not install and consume exactly one item");
            return;
        }

        ItemStack duplicate = new ItemStack(AcademyItems.MEDIA_ONLY_MY_RAILGUN.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, duplicate);
        AcademyItems.MEDIA_ONLY_MY_RAILGUN.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        if (duplicate.getCount() != 1) {
            helper.fail("already-loaded media consumed a duplicate item");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void terminalAndAppInstallersConsumeOnlyOnFirstServerCommit(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        PlayerAbilityData data = player.getData(AcademyAttachments.PLAYER_ABILITY);
        if (AcademyItems.TERMINAL_INSTALLER.get().getDefaultMaxStackSize() != 1) {
            helper.fail("legacy terminal installer became stackable");
            return;
        }

        ItemStack terminal = new ItemStack(AcademyItems.TERMINAL_INSTALLER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
        AcademyItems.TERMINAL_INSTALLER.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        if (!data.isTerminalInstalled() || !terminal.isEmpty()) {
            helper.fail("terminal installation was not an exact one-item server commit");
            return;
        }

        ItemStack duplicateTerminal = new ItemStack(AcademyItems.TERMINAL_INSTALLER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, duplicateTerminal);
        AcademyItems.TERMINAL_INSTALLER.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        if (duplicateTerminal.getCount() != 1) {
            helper.fail("duplicate terminal installer was consumed");
            return;
        }

        ItemStack apps = new ItemStack(AcademyItems.APP_MEDIA_PLAYER.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, apps);
        AcademyItems.APP_MEDIA_PLAYER.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        if (!data.hasApp(com.mohistmc.academy.terminal.AppRegistry.MEDIA_PLAYER) || apps.getCount() != 1) {
            helper.fail("first app install did not consume exactly one item");
            return;
        }
        AcademyItems.APP_MEDIA_PLAYER.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        if (apps.getCount() != 1) {
            helper.fail("duplicate app install consumed another item");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void rfEnergyBridgesPreserveRatioDirectionAndWirelessTransfer(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos inputPos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos nodePos = helper.absolutePos(new BlockPos(4, 1, 2));
        BlockPos outputPos = helper.absolutePos(new BlockPos(6, 1, 2));
        level.setBlock(inputPos, AcademyBlocks.RF_INPUT.get().defaultBlockState(), 3);
        level.setBlock(nodePos, AcademyBlocks.NODE_BASIC.get().defaultBlockState(), 3);
        level.setBlock(outputPos, AcademyBlocks.RF_OUTPUT.get().defaultBlockState(), 3);
        if (!(level.getBlockEntity(inputPos) instanceof com.mohistmc.academy.world.block.entity.EnergyBridgeInputBlockEntity input)
                || !(level.getBlockEntity(nodePos) instanceof NodeBasicBlockEntity node)
                || !(level.getBlockEntity(outputPos) instanceof com.mohistmc.academy.world.block.entity.EnergyBridgeOutputBlockEntity output)) {
            helper.fail("RF bridge or node block entity was not constructed");
            return;
        }

        var inputCapability = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                inputPos, net.minecraft.core.Direction.UP);
        var outputCapability = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                outputPos, net.minecraft.core.Direction.UP);
        if (inputCapability == null || outputCapability == null
                || !inputCapability.canReceive() || inputCapability.canExtract()
                || outputCapability.canReceive() || !outputCapability.canExtract()
                || inputCapability.getMaxEnergyStored() != 8000
                || outputCapability.getMaxEnergyStored() != 8000) {
            helper.fail("directional FE capability contract is wrong");
            return;
        }

        if (inputCapability.receiveEnergy(3, false) != 3
                || input.getProvidedEnergy(0.5) != 0.5
                || input.getStoredFe() != 1
                || input.getProvidedEnergy(1.0) != 0.25
                || input.getStoredFe() != 0) {
            helper.fail("quarter-IF fixed point did not conserve a 3 FE remainder");
            return;
        }
        if (Math.abs(output.injectEnergy(1.25)) > 1.0e-9
                || output.getStoredFe() != 5
                || outputCapability.extractEnergy(3, false) != 3
                || output.getStoredFe() != 2
                || outputCapability.extractEnergy(20, false) != 2
                || output.getStoredFe() != 0) {
            helper.fail("output bridge did not expose exact 4 FE per IF extraction");
            return;
        }

        if (!WirelessSystem.linkGenerator(level, node, input, false, "")
                || !WirelessSystem.linkReceiver(level, node, output, false, "")
                || inputCapability.receiveEnergy(400, false) != 400) {
            helper.fail("bridges could not join a standalone 1.0.7-style node");
            return;
        }
        helper.runAfterDelay(3, () -> {
            if (input.getStoredFe() != 0 || output.getStoredFe() != 400
                    || Math.abs(output.getStoredIf() - 100.0) > 1.0e-9) {
                helper.fail("wireless bridge transfer was not exactly 400 FE = 100 IF");
                return;
            }
            helper.succeed();
        });
    }
}
