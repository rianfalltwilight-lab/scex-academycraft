package com.mohistmc.academy.world.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.mohistmc.academy.capability.IFEnergyStorage;
import com.mohistmc.academy.network.DevLearningSessionManager;
import com.mohistmc.academy.network.LearnSkillPacket;
import com.mohistmc.academy.network.OpenDevGuiPacket;
import com.mohistmc.academy.network.SafePayloadSender;
import com.mohistmc.academy.world.block.entity.AcademyContainerBlockEntity;
import com.mohistmc.academy.world.block.entity.DevAdvancedBlockEntity;
import com.mohistmc.academy.world.block.entity.DevNormalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public abstract class DevMachineBase extends BaseEntityBlock implements IDevMachine {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private final List<SubBlockPos> subBlocks = new ArrayList<>();
    private List<SubBlockPos>[] rotatedBuffer;
    private boolean init = false;

    public record SubBlockPos(int dx, int dy, int dz) {}

    public DevMachineBase(Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any().setValue(FACING, Direction.NORTH));
        this.addSubBlock(0, 1, 0);
        this.addSubBlock(0, 0, 1);
        this.addSubBlock(0, 1, 1);
        this.addSubBlock(0, 2, 1);
        this.addSubBlock(0, 0, 2);
        this.addSubBlock(0, 1, 2);
        this.addSubBlock(0, 2, 2);
        finishInit();
    }

    protected void addSubBlock(int dx, int dy, int dz) {
        if (init) {
            throw new RuntimeException("Trying to add a sub block after block init finished");
        }
        subBlocks.add(new SubBlockPos(dx, dy, dz));
    }

    @SuppressWarnings("unchecked")
    private void finishInit() {
        rotatedBuffer = new List[4];
        for (int i = 0; i < 4; i++) {
            Direction dir = Direction.from2DDataValue(i);
            rotatedBuffer[i] = new ArrayList<>();
            for (SubBlockPos s : subBlocks) {
                rotatedBuffer[i].add(rotateSouth(s, dir));
            }
        }
        init = true;
    }

    public List<SubBlockPos> getRotatedSubBlocks(Direction dir) {
        return rotatedBuffer[dir.get2DDataValue()];
    }

    private static SubBlockPos rotateSouth(SubBlockPos s, Direction dir) {
        return switch (dir) {
            case SOUTH -> new SubBlockPos(s.dx, s.dy, s.dz);
            case NORTH -> new SubBlockPos(-s.dx, s.dy, -s.dz);
            case EAST -> new SubBlockPos(s.dz, s.dy, -s.dx);
            case WEST -> new SubBlockPos(-s.dz, s.dy, s.dx);
            default -> new SubBlockPos(s.dx, s.dy, s.dz);
        };
    }

    protected abstract Block getSubBlock();
    public final Block getStructureSubBlock() { return getSubBlock(); }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            BlockEntity entity = level.getBlockEntity(pos);
            DevMachineType type;
            IFEnergyStorage storage;
            if (entity instanceof DevNormalBlockEntity normal) {
                type = DevMachineType.NORMAL;
                storage = normal;
            } else if (entity instanceof DevAdvancedBlockEntity advanced) {
                type = DevMachineType.ADVANCED;
                storage = advanced;
                // Builds made before the 1.0.7 workflow restoration exposed
                // two non-legacy staging slots. Return those items before the
                // now slotless developer UI opens so upgrading a world never
                // strands or deletes a coil/factor.
                advanced.returnLegacyStagingItems(serverPlayer);
            } else {
                return InteractionResult.CONSUME;
            }

            // AcademyCraft 1.0.7 entered DeveloperUI directly.  The container
            // menus are retained only for the authenticated wireless page;
            // making them the normal entry point produced the duplicate,
            // mostly blank developer screens seen in 0.0.10.
            // Close any previous server container before sending the client-
            // only skill tree. Otherwise a rapid machine transition can send
            // the old menu's close packet after OpenDevGuiPacket and erase the
            // freshly opened developer screen.
            serverPlayer.closeContainer();
            LearnSkillPacket.syncToClient(serverPlayer);
            UUID nonce = DevLearningSessionManager.issue(serverPlayer, type, Optional.of(pos));
            String nodeName = "";
            if (entity instanceof com.mohistmc.academy.energy.api.block.IWirelessUser user) {
                var connection = com.mohistmc.academy.energy.impl.WirelessSystem
                        .getUserConnection(serverPlayer.serverLevel(), user);
                if (connection != null && !connection.isDisposed() && connection.getNode() != null) {
                    nodeName = connection.getNode().getNodeName();
                }
            }
            SafePayloadSender.send(serverPlayer, new OpenDevGuiPacket(type.ordinal(),
                    storage.getEnergyStored(), storage.getMaxEnergyStored(), Optional.of(pos), nonce, nodeName));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        // Structure placement is owned by DevMachineBlockItem's atomic transaction.
    }

    public final boolean initializeStructure(Level level, BlockPos pos, List<BlockPos> targets) {
        BlockEntity mainBe = level.getBlockEntity(pos);
        if (!(mainBe instanceof IDevStructure mainStructure)) return false;

        // Validate every participant before publishing the shared UUID.  If a
        // protection/listener removes a proxy block entity during placement,
        // partially assigned UUIDs would make that proxy's onRemove callback
        // treat rollback as a real structure teardown and materialize a main
        // machine item.  The placement snapshots can then roll back safely
        // because no proxy is linked until the whole footprint is valid.
        List<IDevSubStructure> subStructures = new ArrayList<>(targets.size());
        for (BlockPos target : targets) {
            BlockEntity subBe = level.getBlockEntity(target);
            if (!(subBe instanceof IDevSubStructure subStructure)) return false;
            subStructures.add(subStructure);
        }

        UUID id = UUID.randomUUID();
        mainStructure.setStructureId(id);
        for (IDevSubStructure subStructure : subStructures) {
            subStructure.setStructureId(id);
            subStructure.setMainPos(pos);
        }
        return true;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide() && !newState.is(state.getBlock())) {
            Direction dir = state.getValue(FACING).getOpposite();
            BlockEntity mainBe = level.getBlockEntity(pos);
            UUID mainId = (mainBe instanceof IDevStructure dev) ? dev.getStructureId() : null;

            if (level instanceof net.minecraft.server.level.ServerLevel server
                    && mainBe instanceof com.mohistmc.academy.energy.api.block.IWirelessUser user) {
                com.mohistmc.academy.energy.impl.WirelessSystem.unlinkUser(server, user);
            }

            // BaseEntityBlock does not automatically spill this custom
            // container.  Copy then clear before touching proxies so a nested
            // removal callback can never duplicate the coil/factor contents.
            if (mainBe instanceof AcademyContainerBlockEntity container) {
                for (int i = 0; i < container.getItems().size(); i++) {
                    ItemStack stack = container.getItems().get(i);
                    if (!stack.isEmpty()) {
                        Block.popResource(level, pos, stack.copy());
                        container.getItems().set(i, ItemStack.EMPTY);
                    }
                }
                container.setChanged();
            }

            List<SubBlockPos> subList = getRotatedSubBlocks(dir);
            for (SubBlockPos sub : subList) {
                BlockPos subPos = pos.offset(sub.dx, sub.dy, sub.dz);
                BlockEntity subBe = level.getBlockEntity(subPos);
                if (subBe instanceof IDevSubStructure s) {
                    UUID subId = s.getStructureId();
                    if (pos.equals(s.getMainPos()) && (mainId == null || mainId.equals(subId))) {
                        level.destroyBlock(subPos, false);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        super.createBlockStateDefinition(builder);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        Direction dir = state.getValue(FACING).getOpposite();
        BlockPos pos = context.getClickedPos();
        List<SubBlockPos> subList = getRotatedSubBlocks(dir);

        for (SubBlockPos sub : subList) {
            BlockPos subPos = pos.offset(sub.dx, sub.dy, sub.dz);
            if (!context.getLevel().isEmptyBlock(subPos)) {
                return null;
            }
        }

        return state;
    }
}
